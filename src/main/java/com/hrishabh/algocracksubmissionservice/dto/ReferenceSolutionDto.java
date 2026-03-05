package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * DTO for reference solution received from ProblemService API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceSolutionDto {
    private String sourceCode;
    private String language;
}
