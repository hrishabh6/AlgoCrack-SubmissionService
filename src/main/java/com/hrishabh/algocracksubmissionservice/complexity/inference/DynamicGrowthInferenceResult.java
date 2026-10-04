package com.hrishabh.algocracksubmissionservice.complexity.inference;

import java.util.List;

public record DynamicGrowthInferenceResult(
        GrowthCandidateFamily family,
        String reasonCode,
        List<String> evidenceCodes,
        boolean multiDimensionalInconclusive) {

    public static DynamicGrowthInferenceResult inconclusive(String reasonCode, List<String> evidence) {
        return new DynamicGrowthInferenceResult(GrowthCandidateFamily.INCONCLUSIVE, reasonCode, evidence, false);
    }

    public static DynamicGrowthInferenceResult multiDimensional(List<String> evidence) {
        return new DynamicGrowthInferenceResult(
                GrowthCandidateFamily.INCONCLUSIVE,
                "MULTI_DIMENSIONAL_INCONCLUSIVE",
                evidence,
                true);
    }
}
