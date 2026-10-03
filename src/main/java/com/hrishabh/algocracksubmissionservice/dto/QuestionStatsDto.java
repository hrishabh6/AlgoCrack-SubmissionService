package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * Aggregate submission counts for one question.
 * Called by ProblemService to display acceptance rates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionStatsDto {
    private Long questionId;
    private long totalSubmissions;
    private long acceptedSubmissions;
}
