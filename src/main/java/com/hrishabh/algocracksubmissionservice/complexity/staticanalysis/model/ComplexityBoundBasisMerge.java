package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

/**
 * Deterministic merge of complexity bound bases (Batch 3 closure).
 */
public final class ComplexityBoundBasisMerge {

    private ComplexityBoundBasisMerge() {
    }

    /**
     * Preserves the weakest assumption required by any contributing operation.
     * {@link ComplexityBoundBasis#EXPECTED_ASSUMPTION} dominates
     * {@link ComplexityBoundBasis#AMORTIZED_ASSUMPTION}, which dominates
     * {@link ComplexityBoundBasis#WORST_CASE}.
     */
    public static ComplexityBoundBasis merge(ComplexityBoundBasis current, ComplexityBoundBasis incoming) {
        if (incoming == null || incoming == ComplexityBoundBasis.WORST_CASE) {
            return current;
        }
        if (incoming == ComplexityBoundBasis.EXPECTED_ASSUMPTION) {
            return ComplexityBoundBasis.EXPECTED_ASSUMPTION;
        }
        if (incoming == ComplexityBoundBasis.AMORTIZED_ASSUMPTION) {
            if (current == ComplexityBoundBasis.EXPECTED_ASSUMPTION) {
                return ComplexityBoundBasis.EXPECTED_ASSUMPTION;
            }
            return ComplexityBoundBasis.AMORTIZED_ASSUMPTION;
        }
        return current;
    }
}
