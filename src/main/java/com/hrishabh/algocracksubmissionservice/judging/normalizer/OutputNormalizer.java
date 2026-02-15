package com.hrishabh.algocracksubmissionservice.judging.normalizer;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;

/**
 * Phase 2 of the Judging Pipeline: Output Normalization.
 *
 * Normalizes extracted output so that semantically equivalent outputs
 * compare equal. For example: sorting lists when order doesn't matter,
 * normalizing edge directions in graphs.
 */
public interface OutputNormalizer {

    /**
     * Normalize output for fair comparison.
     *
     * @param extracted The extracted output from Phase 1
     * @param context   Judging context with question metadata
     * @return Normalized output suitable for comparison
     */
    Object normalize(Object extracted, JudgingContext context);
}
