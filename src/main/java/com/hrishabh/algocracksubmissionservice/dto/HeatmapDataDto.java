package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for heatmap submission activity data.
 * Called by ProblemService's UserProfileService via API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapDataDto {
    private Integer year;
    private String from;
    private String to;
    private List<DayActivity> activity;
    private long totalSubmissions;
    private long totalActiveDays;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayActivity {
        private String date;
        private long count;
    }
}
