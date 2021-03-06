package com.artarkatesoft.coronavirustracker.services;

import com.artarkatesoft.coronavirustracker.entities.CountrySummaryEntity;
import com.artarkatesoft.coronavirustracker.entities.Location;
import com.artarkatesoft.coronavirustracker.entities.PopulationWorldBank;
import com.artarkatesoft.coronavirustracker.entities.daydata.BaseDayDataEntity;
import com.artarkatesoft.coronavirustracker.model.DayOneParameterSummary;
import com.artarkatesoft.coronavirustracker.model.PeriodSummary;
import com.artarkatesoft.coronavirustracker.repository.CountrySummaryRepository;
import com.artarkatesoft.coronavirustracker.repository.LocationRepository;
import com.artarkatesoft.coronavirustracker.repository.PopulationWorldBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryCalculationService {

    private final PopulationWorldBankRepository populationWorldBankRepository;
    private final CountrySummaryRepository countrySummaryRepository;
    private final LocationRepository locationRepository;

    //    @Transactional(readOnly = true)
//    @Transactional
    public void updateSummary() {
        List<String> allCountries = locationRepository.findAllCountries();
        List<CountrySummaryEntity> countrySummaryEntityList = new ArrayList<>(allCountries.size());

        for (String country : allCountries) {
            long start = System.currentTimeMillis();
            List<PeriodSummary> summaryList = getCountryAllParametersHistory(country);
            PeriodSummary periodSummary = summaryList.stream()
                    .max(Comparator.comparing(PeriodSummary::getDate))
                    .get();

            List<PopulationWorldBank> countriesWithName = populationWorldBankRepository.findByCountryName(country);
            Long population = (countriesWithName.size() > 0) ? countriesWithName.get(0).getPopulation() : null;
            Optional<CountrySummaryEntity> countrySummary = countrySummaryRepository.findByCountry(country);

            if (countrySummary.isPresent()) {
                CountrySummaryEntity countrySummaryEntity = countrySummary.get();
                BeanUtils.copyProperties(periodSummary, countrySummaryEntity);
                if (countrySummaryEntity.getPopulation() == null)
                    countrySummaryEntity.setPopulation(population);

                log.debug("{}: Time execution before {} is {}ms", country, "countrySummaryRepository.save", System.currentTimeMillis() - start);
//                countrySummaryRepository.save(countrySummaryEntity);
                countrySummaryEntityList.add(countrySummaryEntity);
            } else {
                CountrySummaryEntity countrySummaryEntity = CountrySummaryEntity.builder()
                        .country(country).population(population).build();
                BeanUtils.copyProperties(periodSummary, countrySummaryEntity);
                log.debug("{}: Time execution before {} is {}ms", country, "countrySummaryRepository.save", System.currentTimeMillis() - start);
//                countrySummaryRepository.save(countrySummaryEntity);
                countrySummaryEntityList.add(countrySummaryEntity);
            }
            log.debug("Time execution of create/update data of {} is {}ms", country, System.currentTimeMillis() - start);
        }
        countrySummaryRepository.saveAll(countrySummaryEntityList);
    }


    public List<PeriodSummary> getCountryAllParametersHistory(String countryName) {

        List<DayOneParameterSummary> countryConfirmedHistory = getCountryConfirmedHistory(countryName);
        List<DayOneParameterSummary> countryDeathsHistory = getCountryDeathsHistory(countryName);
        List<DayOneParameterSummary> countryRecoveredHistory = getCountryRecoveredHistory(countryName);

        List<PeriodSummary> periodSummaryList = new ArrayList<>();

        DayOneParameterSummary confirmedSummary;
        DayOneParameterSummary deathsSummary = new DayOneParameterSummary(LocalDate.now(), 0, 0);
        ;
        DayOneParameterSummary recoveredSummary = new DayOneParameterSummary(LocalDate.now(), 0, 0);
        for (int i = 0; i < countryConfirmedHistory.size(); i++) {
            confirmedSummary = countryConfirmedHistory.get(i);
            try {
                deathsSummary = countryDeathsHistory.get(i);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
                deathsSummary.setDate(confirmedSummary.getDate());
            }
//            recoveredSummary = countryRecoveredHistory.get(i);
// TODO: 12.04.2020 Due to updated Tables in CSV files we have different confirmed count - so we need to update database instead of insert new values
            if (i < countryRecoveredHistory.size())
                recoveredSummary = countryRecoveredHistory.get(i);
            else {
                recoveredSummary.setDate(confirmedSummary.getDate());
            }
// TODO: 17.04.2020 Test if do not match
            if (!confirmedSummary.getDate().equals(deathsSummary.getDate()) || !recoveredSummary.getDate().equals(deathsSummary.getDate()))
                throw new RuntimeException("Dates of confirmed, recovered and deaths do not match!!!");

            assert confirmedSummary.getDate().equals(deathsSummary.getDate());
            assert recoveredSummary.getDate().equals(deathsSummary.getDate());

            periodSummaryList.add(
                    PeriodSummary.builder()
                            .date(confirmedSummary.getDate())
                            .confirmedCount(confirmedSummary.getCount())
                            .confirmedDelta(confirmedSummary.getDayDelta())
                            .recoveredCount(recoveredSummary.getCount())
                            .recoveredDelta(recoveredSummary.getDayDelta())
                            .deathsCount(deathsSummary.getCount())
                            .deathsDelta(deathsSummary.getDayDelta())
                            .build()
            );
        }
        return periodSummaryList;
    }


    public List<DayOneParameterSummary> getCountryConfirmedHistory(String countryName) {
        return getCountryOneParameterHistory(countryName, Location::getConfirmedList);
    }

    public List<DayOneParameterSummary> getCountryDeathsHistory(String countryName) {
        return getCountryOneParameterHistory(countryName, Location::getDeathsList);
    }

    public List<DayOneParameterSummary> getCountryRecoveredHistory(String countryName) {
        return getCountryOneParameterHistory(countryName, Location::getRecoveredList);
    }

    private List<DayOneParameterSummary> getCountryOneParameterHistory(String countryName, Function<Location, List<? extends BaseDayDataEntity>> func) {

        List<Location> locations = locationRepository.findAllByCountryRegion(countryName);
        List<DayOneParameterSummary> dayOneParameterSummaryList = null;
        for (Location location : locations) {
// TODO: 13.04.2020 org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role: com.artarkatesoft.coronavirustracker.entities.Location.confirmedList, could not initialize proxy - no Session
            List<? extends BaseDayDataEntity> dayDataEntityList = func.apply(location);

            if (dayDataEntityList == null || dayDataEntityList.isEmpty()) continue;
            dayDataEntityList.sort(Comparator.comparing(BaseDayDataEntity::getDate));

// TODO: 26.03.2020 Change Algo -> may be use TreeMap<LocalDate, DayOneParameterSummary> to store one day summary
            if (dayOneParameterSummaryList == null) {

                dayOneParameterSummaryList = dayDataEntityList.stream().map(dayData -> DayOneParameterSummary.builder()
                        .date(dayData.getDate())
                        .count(dayData.getCount())
                        .dayDelta(dayData.getDayDelta())
                        .build())
                        .collect(Collectors.toList());
            } else {
                for (int i = 0; i < dayDataEntityList.size(); i++) {
                    BaseDayDataEntity dayData = dayDataEntityList.get(i);
                    DayOneParameterSummary dayOneParameterSummary = dayOneParameterSummaryList.get(i);
                    assert dayOneParameterSummary.getDate().equals(dayData.getDate());
                    dayOneParameterSummary.setCount(dayOneParameterSummary.getCount() + dayData.getCount());
                    dayOneParameterSummary.setDayDelta(dayOneParameterSummary.getDayDelta() + dayData.getDayDelta());
                }
            }
        }
        return dayOneParameterSummaryList;
    }


}
