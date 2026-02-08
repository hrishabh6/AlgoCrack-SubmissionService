package com.hrishabh.algocracksubmissionservice.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal representation of a testcase execution output.
 * CXE-agnostic - used throughout the Submission Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseOutput {

    /**
     * Index of this testcase (for matching with input).
     */
    private Integer index;

    /**
     * The actual output produced by code execution.
     */
    private String output;

    /**
     * Error message if execution failed for this testcase.
     */
    private String error;

    /**
     * Execution time in milliseconds.
     */
    private Long executionTimeMs;

    /**
     * Memory usage in kilobytes.
     */
    private Long memoryKb;
}
