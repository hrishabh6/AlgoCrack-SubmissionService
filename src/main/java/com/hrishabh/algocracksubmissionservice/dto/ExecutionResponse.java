package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * Immediate response from CXE after submitting code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {

    private String submissionId;
    private String status;
    private String message;
    private Integer queuePosition;
    private Long estimatedWaitTimeMs;
    private String statusUrl;
    private String resultsUrl;
}
