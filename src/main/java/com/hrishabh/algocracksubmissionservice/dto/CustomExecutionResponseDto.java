package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;
import java.util.List;

/**
 * Response DTO for custom test case execution.
 * Returns raw output without any pass/fail verdict.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomExecutionResponseDto {

    /**
     * Overall execution status:
     * - EXECUTED: Code ran successfully
     * - COMPILATION_ERROR: Code failed to compile
     * - RUNTIME_ERROR: Code threw an exception during execution
     * - TIMEOUT: Execution exceeded time limit
     */
    private String status;

    /**
     * Compilation output (errors/warnings), null if compilation succeeded.
     */
    private String compilationOutput;

    /**
     * Results for each test case.
     */
    private List<TestCaseOutput> results;

    /**
     * Individual test case execution result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseOutput {
        private Integer index;
        private String input;
        private String output; // Raw stdout from user's code
        private String error; // stderr if any
        private Long executionTimeMs;
        private Long memoryKb;
    }
}
