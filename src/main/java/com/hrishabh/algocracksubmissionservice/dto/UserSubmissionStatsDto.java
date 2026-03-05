package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for user submission statistics.
 * Called by ProblemService's UserProfileService via API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubmissionStatsDto {
    private long totalSolved;
    private long easySolved;
    private long mediumSolved;
    private long hardSolved;
    private List<LanguageStat> languageStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageStat {
        private String language;
        private long count;
    }
}
