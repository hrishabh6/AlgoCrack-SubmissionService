package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * API Response DTO for immediate submission response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseDto {
    
    private String submissionId;
    private String status;
    private String message;
}
