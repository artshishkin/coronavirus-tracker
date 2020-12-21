package com.artarkatesoft.coronavirustracker.services;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.artarkatesoft.coronavirustracker.entities.CountrySummaryEntity;
import com.artarkatesoft.coronavirustracker.entities.PopulationWorldBank;
import com.artarkatesoft.coronavirustracker.model.CountryData;
import com.artarkatesoft.coronavirustracker.model.CountryOneParameterData;
import com.artarkatesoft.coronavirustracker.model.DayOneParameterSummary;
import com.artarkatesoft.coronavirustracker.model.PeriodSummary;
import com.artarkatesoft.coronavirustracker.repository.CountrySummaryRepository;
import com.artarkatesoft.coronavirustracker.repository.LocationRepository;
import com.artarkatesoft.coronavirustracker.repository.PopulationWorldBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Slf4j
@XRayEnabled
public class CoronaDataService {

    private final LocationRepository locationRepository;
    private final PopulationWorldBankRepository populationWorldBankRepository;
    private final CountrySummaryRepository countrySummaryRepository;
    private final SummaryCalculationService summaryCalculationService;

    public List<DayOneParameterSummary> getCountryConfirmedHistory(String countryName) {
        return summaryCalculationService.getCountryConfirmedHistory(countryName);
    }

    public List<DayOneParameterSummary> getCountryDeathsHistory(String countryName) {
        return summaryCalculationService.getCountryDeathsHistory(countryName);
    }

    public List<DayOneParameterSummary> getCountryRecoveredHistory(String countryName) {
        return summaryCalculationService.getCountryRecoveredHistory(countryName);
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
        log.debug("getDayPeriodSummariesOfCountry ({})", countryName);
        return new CountryOneParameterData(countryName, getPopulationOfCountry(countryName), getCountryConfirmedHistory(countryName));
    }


    public CountryData getDayPeriodSummaryOfCountry(String countryName) {
        return new CountryData(countryName, getPopulationOfCountry(countryName), summaryCalculationService.getCountryAllParametersHistory(countryName));
    }

    public CountryData getWeekPeriodSummaryOfCountry(String countryName) {
        List<PeriodSummary> countryAllParametersHistory = summaryCalculationService.getCountryAllParametersHistory(countryName);
        int size = countryAllParametersHistory.size();
        List<PeriodSummary> result = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            if (i % 7 == 0) {
                result.add(countryAllParametersHistory.get(size - i - 1));
            }
        }
        return new CountryData(countryName, getPopulationOfCountry(countryName), result);
    }


    public List<String> getAllCountries() {
        return locationRepository.findAllCountries();
    }

    private Long getPopulationOfCountry(String countryName) {
        List<PopulationWorldBank> listPopulation = populationWorldBankRepository.findByCountryName(countryName);
        return (listPopulation.size() == 0) ? null : listPopulation.get(0).getPopulation();
    }

    @Value("${debug}")
    private boolean isDebug;


    public List<CountrySummaryEntity> getSummaryList() {
        return countrySummaryRepository.findAll();
    }

    public Optional<CountrySummaryEntity> getCountrySummary(String countryName) {
        return countrySummaryRepository.findByCountry(countryName);
    }
}
