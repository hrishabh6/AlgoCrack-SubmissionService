package com.hrishabh.algocracksubmissionservice.complexity.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ComplexityAnalysisFingerprintReuseTest {

    @Test
    void staticOnlyPathDiffersFromDynamicBenchmarkPath() {
        var base = fingerprint("profile-hash", ComplexityVersionConstants.INTERPRETATION_STATIC_ONLY);
        var dynamic = fingerprint("profile-hash", ComplexityVersionConstants.INTERPRETATION_DYNAMIC_BENCHMARK);
        assertNotEquals(ComplexityAnalysisFingerprint.compute(base), ComplexityAnalysisFingerprint.compute(dynamic));
    }

    private static ComplexityAnalysisFingerprint.FingerprintInput fingerprint(String profileHash, String path) {
        return new ComplexityAnalysisFingerprint.FingerprintInput(
                "source",
                "static-v1.2",
                ComplexityVersionConstants.DYNAMIC_INFERENCE_VERSION,
                ComplexityVersionConstants.HYBRID_CONFIDENCE_MODEL_VERSION,
                "jdk-kb-v1",
                profileHash,
                "gen-v1",
                ComplexityVersionConstants.DEFAULT_HARNESS_VERSION,
                ComplexityVersionConstants.MEASUREMENT_POLICY_VERSION,
                null,
                path);
    }
}
