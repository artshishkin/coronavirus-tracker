package com.artarkatesoft.coronavirustracker.services;

import com.artarkatesoft.coronavirustracker.entities.daydata.BaseDayDataEntity;
import com.artarkatesoft.coronavirustracker.entities.Location;
import com.artarkatesoft.coronavirustracker.entities.PopulationWorldBank;
import com.artarkatesoft.coronavirustracker.entities.daydata.Confirmed;
import com.artarkatesoft.coronavirustracker.model.CountryData;
import com.artarkatesoft.coronavirustracker.model.CountryOneParameterData;
import com.artarkatesoft.coronavirustracker.model.DayOneParameterSummary;
import com.artarkatesoft.coronavirustracker.model.DaySummary;
import com.artarkatesoft.coronavirustracker.repository.LocationRepository;
import com.artarkatesoft.coronavirustracker.repository.PopulationWorldBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoronaDataService {
    private final LocationRepository locationRepository;
    private final PopulationWorldBankRepository populationWorldBankRepository;


    //    public List<DaySummary> getCountryConfirmedHistory(String countryName) {
//
//        List<Location> locations = locationRepository.findAllByCountryRegion(countryName);
//        List<DaySummary> daySummaryList = null;
//        for (Location location : locations) {
//            List<Confirmed> confirmedList = location.getConfirmedList();
//            if (daySummaryList == null) {
//                daySummaryList = confirmedList.stream().map(confirmed -> DaySummary.builder()
//                        .date(confirmed.getDate())
//                        .count(confirmed.getCount())
//                        .build())
//                        .collect(Collectors.toList());
//            } else {
//                for (int i = 0; i < confirmedList.size(); i++) {
//                    Confirmed confirmed = confirmedList.get(i);
//                    DaySummary daySummary = daySummaryList.get(i);
//                    assert daySummary.getDate().equals(confirmed.getDate());
//                    daySummary.setCount(daySummary.getCount() + confirmed.getCount());
//                }
//            }
//        }
//        return daySummaryList;
//    }

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

            List<? extends BaseDayDataEntity> dayDataEntityList = func.apply(location);
// TODO: 26.03.2020 Change Algo -> may be use TreeMap<LocalDate, DayOneParameterSummary> to store one day summary
            if (dayOneParameterSummaryList == null) {
                if(dayDataEntityList==null || dayDataEntityList.isEmpty()) continue;
                dayOneParameterSummaryList = dayDataEntityList.stream().map(dayData -> DayOneParameterSummary.builder()
                        .date(dayData.getDate())
                        .count(dayData.getCount())
                        .build())
                        .collect(Collectors.toList());
            } else {
                for (int i = 0; i < dayDataEntityList.size(); i++) {
                    BaseDayDataEntity dayData = dayDataEntityList.get(i);
                    DayOneParameterSummary dayOneParameterSummary = dayOneParameterSummaryList.get(i);
                    assert dayOneParameterSummary.getDate().equals(dayData.getDate());
                    dayOneParameterSummary.setCount(dayOneParameterSummary.getCount() + dayData.getCount());
                }
            }
        }
        return dayOneParameterSummaryList;
    }

    public CountryOneParameterData getWeekPeriodSummariesOfCountry(String countryName) {
        List<DayOneParameterSummary> countryConfirmedHistory = getCountryConfirmedHistory(countryName);
        int size = countryConfirmedHistory.size();
        List<DayOneParameterSummary> result = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            if (i % 7 == 0) {
                result.add(countryConfirmedHistory.get(size - i - 1));
            }
        }
        return new CountryOneParameterData(countryName, getPopulationOfCountry(countryName), result);
    }

    public CountryOneParameterData getDayPeriodSummariesOfCountry(String countryName) {
        return new CountryOneParameterData(countryName, getPopulationOfCountry(countryName), getCountryConfirmedHistory(countryName));
    }


    public CountryData getDayPeriodSummaryOfCountry(String countryName) {
        return new CountryData(countryName, getPopulationOfCountry(countryName), getCountryAllParametersHistory(countryName));
    }

    public CountryData getWeekPeriodSummaryOfCountry(String countryName) {
        List<DaySummary> countryAllParametersHistory = getCountryAllParametersHistory(countryName);
        int size = countryAllParametersHistory.size();
        List<DaySummary> result = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            if (i % 7 == 0) {
                result.add(countryAllParametersHistory.get(size - i - 1));
            }
        }
        return new CountryData(countryName, getPopulationOfCountry(countryName), result);
    }


    public List<DaySummary> getCountryAllParametersHistory(String countryName) {

        List<DayOneParameterSummary> countryConfirmedHistory = getCountryConfirmedHistory(countryName);
        List<DayOneParameterSummary> countryDeathsHistory = getCountryDeathsHistory(countryName);
        List<DayOneParameterSummary> countryRecoveredHistory = getCountryRecoveredHistory(countryName);

        List<DaySummary> daySummaryList = new ArrayList<>();

        DayOneParameterSummary confirmedSummary;
        DayOneParameterSummary deathsSummary;
        DayOneParameterSummary recoveredSummary = new DayOneParameterSummary(LocalDate.now(), 0);
        for (int i = 0; i < countryConfirmedHistory.size(); i++) {
            confirmedSummary = countryConfirmedHistory.get(i);
            deathsSummary = countryDeathsHistory.get(i);
//            recoveredSummary = countryRecoveredHistory.get(i);

            if (i < countryRecoveredHistory.size())
                recoveredSummary = countryRecoveredHistory.get(i);
            else {
                recoveredSummary.setDate(confirmedSummary.getDate());
            }


            assert confirmedSummary.getDate().equals(deathsSummary.getDate());
            assert recoveredSummary.getDate().equals(deathsSummary.getDate());

            daySummaryList.add(
                    DaySummary.builder()
                            .date(confirmedSummary.getDate())
                            .confirmedCount(confirmedSummary.getCount())
                            .recoveredCount(recoveredSummary.getCount())
                            .deathsCount(deathsSummary.getCount())
                            .build()
            );
        }
        return daySummaryList;
    }


    public List<String> getAllCountries() {
        return locationRepository.findAll()
                .stream()
                .map(Location::getCountryRegion)
                .filter(country -> !StringUtils.isEmpty(country))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private Long getPopulationOfCountry(String countryName) {
        List<PopulationWorldBank> listPopulation = populationWorldBankRepository.findByCountryName(countryName);
        return (listPopulation.size() == 0) ? null : listPopulation.get(0).getPopulation();
    }


}
