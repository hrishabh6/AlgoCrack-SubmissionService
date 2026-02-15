package com.hrishabh.algocracksubmissionservice.judging.extractor;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;

/**
 * Phase 1 of the Judging Pipeline: Output Extraction.
 *
 * Transforms raw CXE string output into a structured form suitable for
 * comparison.
 * For standard returns: identity (pass-through)
 * For void returns: extract serialized mutation from stdout
 * For node types: parse serialized structure
 */
public interface OutputExtractor {

    /**
     * Extract judgeable output from raw execution result.
     *
     * @param rawOutput The raw string output from CXE execution
     * @param context   Judging context with question metadata
     * @return Structured output suitable for normalization and comparison
     */
    Object extract(String rawOutput, JudgingContext context);
}
