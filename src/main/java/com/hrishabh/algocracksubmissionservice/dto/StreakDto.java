package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * Daily submission streak for a user.
 * Called by ProblemService's UserProfileService via API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakDto {
    /** Consecutive active days ending today, or yesterday if the user has not submitted yet today. */
    private int currentStreak;
    private int longestStreak;
    private boolean activeToday;
    /** ISO date (yyyy-MM-dd) of the most recent active day, or null if none. */
    private String lastActiveDate;
    private long totalActiveDays;
}
