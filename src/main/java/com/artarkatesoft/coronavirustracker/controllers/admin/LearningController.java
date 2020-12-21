package com.artarkatesoft.coronavirustracker.controllers.admin;

import com.artarkatesoft.coronavirustracker.services.SummaryCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/learn")
@RequiredArgsConstructor
public class LearningController {

    private final SummaryCalculationService summaryCalculationService;
    private final Environment environment;

    @GetMapping("envdetails")
    public String getEnvironmentDetails() {
        return environment.toString();
    }

    @GetMapping("updateSummary")
    public String updateSummary() {
        summaryCalculationService.updateSummary();
        return "Ok";
    }


}
