package com.hrishabh.algocracksubmissionservice.judging.comparator;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;

/**
 * Phase 3 of the Judging Pipeline: Output Comparison.
 *
 * Compares two normalized outputs to determine if they are equivalent.
 */
public interface OutputComparator {

    /**
     * Compare normalized user output vs normalized oracle output.
     *
     * @param userNormalized   Normalized user output from Phase 2
     * @param oracleNormalized Normalized oracle output from Phase 2
     * @param context          Judging context with question metadata
     * @return ComparisonResult with passed/failed status and details
     */
    ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context);
}
