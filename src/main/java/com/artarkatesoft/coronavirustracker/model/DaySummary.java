package com.artarkatesoft.coronavirustracker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DaySummary {
    private LocalDate date;
    private Integer confirmedCount;
    private Integer deathsCount;
    private Integer recoveredCount;
}
