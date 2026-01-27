package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;
import java.util.List;

/**
 * DTO for polling CXE status and getting results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionStatusDto {

    private String submissionId;
    private String status;
    private String verdict;
    private Integer runtimeMs;
    private Integer memoryKb;
    private String errorMessage;
    private String compilationOutput;
    private List<TestCaseResult> testCaseResults;
    private Long queuedAt;
    private Long startedAt;
    private Long completedAt;
    private String workerId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseResult {
        private Integer index;
        private Boolean passed;
        private String actualOutput;
        private String expectedOutput;
        private Long executionTimeMs;
        private String error;
    }
}
