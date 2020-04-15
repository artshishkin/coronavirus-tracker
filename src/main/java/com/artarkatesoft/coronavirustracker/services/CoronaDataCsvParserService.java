package com.artarkatesoft.coronavirustracker.services;

import com.artarkatesoft.coronavirustracker.aop.LogExecutionTime;
import com.artarkatesoft.coronavirustracker.configuration.AppConfig;
import com.artarkatesoft.coronavirustracker.entities.*;
import com.artarkatesoft.coronavirustracker.entities.daydata.BaseDayDataEntity;
import com.artarkatesoft.coronavirustracker.entities.daydata.Confirmed;
import com.artarkatesoft.coronavirustracker.entities.daydata.Deaths;
import com.artarkatesoft.coronavirustracker.entities.daydata.Recovered;
import com.artarkatesoft.coronavirustracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;

import static org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers.exact;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoronaDataCsvParserService {

    private Map<String, Integer> lastHashCodeMap;

    @Value("${app.coronavirus-data.confirmed-url}")
    private String CONFIRMED_VIRUS_DATA_URL;
    @Value("${app.coronavirus-data.deaths-url}")
    private String DEATHS_VIRUS_DATA_URL;
    @Value("${app.coronavirus-data.recovered-url}")
    private String RECOVERED_VIRUS_DATA_URL;

    private final LocationRepository locationRepository;
    private final ConfirmedRepository confirmedRepository;
    private final DeathsRepository deathsRepository;
    private final RecoveredRepository recoveredRepository;


    private CoronaDataService coronaDataService;

    @Autowired
    public void setCoronaDataService(CoronaDataService coronaDataService) {
        this.coronaDataService = coronaDataService;
    }

    private AppConfig appConfig;

    @Autowired
    public void setAppConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Async
    public void fetchVirusDataAsync() {
        fetchVirusData();
    }


    @SneakyThrows
//    @Scheduled(cron = "0 0/10 * * * *")
    @Scheduled(cron = "0 0 * * * *")
    public void fetchVirusData() {
        log.info("Corona Data - Fetching CSV data started");
        log.info("Application configuration: {}", appConfig);
        boolean needToUpdate = false;
        needToUpdate |= parseOneCSVVirusFile(appConfig.getConfirmedUrl(), confirmedRepository, Confirmed.class);
        needToUpdate |= parseOneCSVVirusFile(appConfig.getDeathsUrl(), deathsRepository, Deaths.class);
        needToUpdate |= parseOneCSVVirusFile(appConfig.getRecoveredUrl(), recoveredRepository, Recovered.class);

        if (needToUpdate)
            coronaDataService.updateSummary();

        log.info("Corona Data - Fetching CSV data finished");
    }

    //    @SneakyThrows
    private boolean parseOneCSVVirusFile(String dataUrl, JpaRepository repository, Class<? extends BaseDayDataEntity> baseDayDataClass) {

        boolean changed = false;

        RestTemplate restTemplate = new RestTemplate();

        String fileContent = restTemplate.getForObject(URI.create(dataUrl), String.class);
        if (fileContent == null) {
            throw new IllegalStateException("Corona Data - File content of coronavirus data is NULL. URL(" + dataUrl + ")");
        }

        int hashCode = fileContent.hashCode();
        if (lastHashCodeMap == null) lastHashCodeMap = new HashMap<>();

        if (Objects.equals(lastHashCodeMap.get(dataUrl), hashCode)) return changed;

        lastHashCodeMap.put(dataUrl, hashCode);

        log.info("Corona Data - Started parsing data from {}", dataUrl);

        StringReader csvBodyReader = new StringReader(fileContent);
        Iterable<CSVRecord> records = null;
        try {
            records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvBodyReader);
        } catch (IOException e) {
            throw new IllegalStateException("Error in parsing CSV. URL(" + dataUrl + ")", e);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy[yy]");

        List<BaseDayDataEntity> listToSaveAtOnce = new ArrayList<>();

        ExampleMatcher locationMatcher = ExampleMatcher.matchingAll()
                .withIgnoreNullValues()
                .withIgnorePaths("confirmedList", "deathsList", "recoveredList", "longitude", "latitude");

//        ExampleMatcher baseDayDataMatcher = ExampleMatcher.matchingAll()
//                .withIgnoreNullValues()
//                .withIgnorePaths("count", "dayDelta");

        for (CSVRecord record : records) {
//            String id = record.get("ID");
            Location location = Location.builder()
                    .provinceState(record.get(0))
                    .countryRegion(record.get(1))
                    .latitude(Double.parseDouble(record.get("Lat")))
                    .longitude(Double.parseDouble(record.get("Long")))
                    .build();

//            location = locationRepository.findOne(Example.of(location)).orElse(locationRepository.save(location));


            Location finalLocation = location;
            location = locationRepository
                    .findOne(Example.of(location, locationMatcher))
                    .orElseGet(() -> locationRepository.save(finalLocation));


//
//            if (!locationRepository.exists(Example.of(location)))
//                locationRepository.save(location);
//            location = locationRepository.findOne(Example.of(location)).get();


//            List<Confirmed> confirmedList = location.getConfirmedList();
//            if (confirmedList == null) {
//                confirmedList = new ArrayList<>();
//                location.setConfirmedList(confirmedList);
//            }

            Map<String, String> headerAndValue = record.toMap();


            Integer previousCount = null;

            for (Map.Entry<String, String> entry : headerAndValue.entrySet()) {
                String columnName = entry.getKey();
                String columnValue = entry.getValue();

                //convert String to LocalDate
                try {
                    LocalDate localDate = LocalDate.parse(columnName, formatter);

                    BaseDayDataEntity baseDayData = baseDayDataClass.newInstance();
                    baseDayData.setDate(localDate);
                    baseDayData.setLocation(location);

                    //Find by Date and Location ONLY!!!
                    Optional optionalDayData = repository.findOne(Example.of(baseDayData));

                    if (columnValue.isEmpty()) columnValue = "0";
                    int currentCount = Integer.parseInt(columnValue);
                    int delta = previousCount == null ? 0 : currentCount - previousCount;
                    previousCount = currentCount;

                    baseDayData.setCount(currentCount);
                    baseDayData.setDayDelta(delta);

                    if (optionalDayData.isPresent()) {
                        BaseDayDataEntity entity = (BaseDayDataEntity) optionalDayData.get();
                        if (entity.getCount() != currentCount) {

                            BeanUtils.copyProperties(baseDayData, entity, "id");
                            listToSaveAtOnce.add(entity);
//                            repository.save(entity);
                        }
                    } else {
                        listToSaveAtOnce.add(baseDayData);
//                        try {
//                            repository.save(baseDayData);
//                        } catch (DataIntegrityViolationException e) {
//                            e.printStackTrace();
//                        }
                    }


//                    if (!repository.exists(Example.of(baseDayData)))
//                        repository.save(baseDayData);

//                    if(location.getConfirmedList().contains(baseDayData))
//                        System.out.println("contains");


//                    if (!repository.exists(Example.of(baseDayData)))
//                        listToSaveAtOnce.add(baseDayData);

                } catch (DateTimeParseException ignored) {
//                    log.warn("DateTimeParse exception", ignored);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!listToSaveAtOnce.isEmpty()) {
            repository.saveAll(listToSaveAtOnce);
        }
        return true;
    }
}
