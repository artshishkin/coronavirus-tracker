package com.artarkatesoft.coronavirustracker.controllers;

import com.artarkatesoft.coronavirustracker.model.CountryOneParameterData;
import com.artarkatesoft.coronavirustracker.model.DayOneParameterSummary;
import com.artarkatesoft.coronavirustracker.services.CoronaDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final CoronaDataService coronaDataService;

    @GetMapping("/country/{countryName}")
    public Map<String,List<DayOneParameterSummary>> getAllDataByCountry(@PathVariable("countryName") String countryName) {
        Map<String,List<DayOneParameterSummary>> result = new HashMap<>();
        result.put("confirmed",coronaDataService.getCountryConfirmedHistory(countryName));
        result.put("deaths",coronaDataService.getCountryDeathsHistory(countryName));
        result.put("recovered",coronaDataService.getCountryRecoveredHistory(countryName));
        return result;
    }

    @GetMapping("/country/{countryName}/{dataType}")
    public List<DayOneParameterSummary> getDataByCountry(@PathVariable("countryName") String countryName, @PathVariable("dataType") String dataType) {
        switch (dataType) {
            case "confirmed":
                return coronaDataService.getCountryConfirmedHistory(countryName);
            case "deaths":
                return coronaDataService.getCountryDeathsHistory(countryName);
            case "recovered":
                return coronaDataService.getCountryRecoveredHistory(countryName);
        }
// TODO: 23.03.2020 What we need to return when error
        throw new IllegalArgumentException(dataType + " is not applicable for ");
    }

    @GetMapping("/country/{countryName}/period/{period}")
    public CountryOneParameterData getConfirmedWeekByCountry(
            @PathVariable("countryName") String countryName,
            @PathVariable("period") String period
    ) {
        switch (period) {
            case "week":
                return coronaDataService.getWeekPeriodSummariesOfCountry(countryName);
            case "day":
                return coronaDataService.getDayPeriodSummariesOfCountry(countryName);
        }
        throw new RuntimeException("Not Found");
    }


}
