package com.hrishabh.algocracksubmissionservice.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Internal DTO representing the result of a batch code execution.
 * CXE-agnostic - the ExecutionAdapter translates CXE responses to this format.
 * 
 * This decouples the Submission Service from CXE DTOs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecutionResult {

    /**
     * Overall execution status.
     */
    private ExecutionStatus status;

    /**
     * Outputs for each testcase (index-aligned with inputs).
     */
    private List<TestCaseOutput> outputs;

    /**
     * Compilation output (if any).
     */
    private String compilationOutput;

    /**
     * Error message (if execution failed).
     */
    private String errorMessage;

    /**
     * Total runtime across all testcases in milliseconds.
     */
    private Long totalRuntimeMs;

    /**
     * Peak memory usage in kilobytes.
     */
    private Long peakMemoryKb;

    /**
     * Worker ID that processed this execution (for debugging).
     */
    private String workerId;

    /**
     * Execution status enum.
     */
    public enum ExecutionStatus {
        SUCCESS,
        COMPILATION_ERROR,
        RUNTIME_ERROR,
        TIMEOUT,
        MEMORY_LIMIT_EXCEEDED,
        INTERNAL_ERROR
    }

    /**
     * Check if execution completed successfully (code ran without errors).
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * Check if there was a compilation error.
     */
    public boolean isCompilationError() {
        return status == ExecutionStatus.COMPILATION_ERROR;
    }
}
