package com.hrishabh.algocracksubmissionservice.dto;

import com.hrishabh.algocracksubmissionservice.dto.internal.RunVerdict;
import lombok.*;

import java.util.List;

/**
 * Response DTO for /run endpoint.
 * Returns execution results synchronously.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunResponseDto {

    /**
     * Overall run verdict (PASSED_RUN, FAILED_RUN, etc.)
     * Uses RUN-specific verdicts to avoid confusion with SUBMIT.
     */
    private RunVerdict verdict;

    /**
     * Was the run successful (all tests passed)?
     */
    private boolean success;

    /**
     * Total runtime in milliseconds
     */
    private Integer runtimeMs;

    /**
     * Memory usage in kilobytes
     */
    private Integer memoryKb;

    /**
     * Compilation output (if any errors)
     */
    private String compilationOutput;

    /**
     * Error message (if any)
     */
    private String errorMessage;

    /**
     * Results for each testcase
     */
    private List<TestCaseRunResult> testCaseResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseRunResult {
        private Integer index;

        /**
         * Did this testcase pass?
         */
        private Boolean passed;

        /**
         * Actual output from user's code
         */
        private String actualOutput;

        /**
         * Expected output (from oracle)
         */
        private String expectedOutput;

        /**
         * Execution time for this testcase
         */
        private Long executionTimeMs;

        /**
         * Error if any
         */
        private String error;
    }

    /**
     * Create error response.
     */
    public static RunResponseDto error(RunVerdict verdict, String errorMessage) {
        return RunResponseDto.builder()
                .verdict(verdict)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
