package com.hrishabh.algocracksubmissionservice.complexity.inference;

import java.util.Map;

public record TimingObservation(
        String caseId,
        String variant,
        Map<String, Integer> sizeVector,
        String primaryDimension,
        long primarySize,
        long medianElapsedNs,
        long madElapsedNs,
        boolean outputValidated,
        boolean usableForInference) {
}
