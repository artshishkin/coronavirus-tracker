package com.artarkatesoft.coronavirustracker.services;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;

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


    private AppConfig appConfig;

    @Autowired
    public void setAppConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @SneakyThrows
    @Scheduled(cron = "0 0/15 * * * *")
    @Async
    public void fetchVirusData() {
        log.info("Corona Data - Fetching CSV data started");
        log.info("Application configuration: {}", appConfig);
//        parseOneCSVVirusFile(CONFIRMED_VIRUS_DATA_URL, confirmedRepository, Confirmed.class);
        parseOneCSVVirusFile(appConfig.getConfirmedUrl(), confirmedRepository, Confirmed.class);
        parseOneCSVVirusFile(appConfig.getDeathsUrl(), deathsRepository, Deaths.class);
        parseOneCSVVirusFile(appConfig.getRecoveredUrl(), recoveredRepository, Recovered.class);
        log.info("Corona Data - Fetching CSV data finished");
    }


    //    @SneakyThrows
    private void parseOneCSVVirusFile(String dataUrl, JpaRepository repository, Class<? extends BaseDayDataEntity> baseDayDataClass) {

        RestTemplate restTemplate = new RestTemplate();

        String fileContent = restTemplate.getForObject(URI.create(dataUrl), String.class);
        if (fileContent == null) {
            log.info("Corona Data - File content of coronavirus data is NULL. URL({})", dataUrl);
            return;
        }

        int hashCode = fileContent.hashCode();
        if (lastHashCodeMap == null) lastHashCodeMap = new HashMap<>();

        if (Objects.equals(lastHashCodeMap.get(dataUrl), hashCode)) return;

        lastHashCodeMap.put(dataUrl, hashCode);

        log.info("Corona Data - Started parsing data from {}", dataUrl);

        StringReader csvBodyReader = new StringReader(fileContent);
        Iterable<CSVRecord> records = null;
        try {
            records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(csvBodyReader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy[yy]");

        List<BaseDayDataEntity> listToSaveAtOnce = new ArrayList<>();

        ExampleMatcher locationMatcher = ExampleMatcher.matchingAll()
                .withIgnoreNullValues()
                .withIgnorePaths("confirmedList", "deathsList", "recoveredList", "longitude", "latitude");

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


            for (Map.Entry<String, String> entry : headerAndValue.entrySet()) {
                String columnName = entry.getKey();
                String columnValue = entry.getValue();

                //convert String to LocalDate
                try {

                    LocalDate localDate = LocalDate.parse(columnName, formatter);
//                    BaseDayDataEntity baseDayData = new Confirmed();

                    BaseDayDataEntity baseDayData = baseDayDataClass.newInstance();
                    baseDayData.setDate(localDate);
                    if (columnValue.isEmpty()) columnValue = "0";
                    baseDayData.setCount(Integer.parseInt(columnValue));
                    baseDayData.setLocation(location);

//                    if (!repository.exists(Example.of(baseDayData)))
//                        repository.save(baseDayData);

//                    if(location.getConfirmedList().contains(baseDayData))
//                        System.out.println("contains");

                    if (!repository.exists(Example.of(baseDayData)))
                        listToSaveAtOnce.add(baseDayData);

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
    }
}
