package com.hrishabh.algocracksubmissionservice.judging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wraps raw execution output with metadata.
 * Richer than just a String — gives pipeline phases access to errors, timing,
 * etc.
 * Future-proofed with fields that may not be used immediately.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionOutput {

    /**
     * The raw string output from code execution.
     */
    private String rawOutput;

    /**
     * Error message if execution failed for this testcase.
     */
    private String error;

    /**
     * Execution time in milliseconds.
     */
    private Long executionTimeMs;

    // ---- Future-safe fields (add now, use later — avoids breaking interfaces)
    // ----

    /**
     * Whether execution timed out. Distinguishes timeout vs runtime error.
     */
    private boolean timedOut;

    /**
     * Memory usage in kilobytes.
     */
    private Long memoryKb;

    /**
     * Finer error type signal: "RUNTIME", "COMPILATION", "TIMEOUT", "OOM".
     */
    private String errorType;

    /**
     * Check if this output has an error.
     */
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}
