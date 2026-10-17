package com.hrishabh.algocracksubmissionservice.complexity.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ComplexityAnalysisFingerprintTest {

    @Test
    void stableForSameComponents() {
        var input = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "profile-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", null, null, null);
        assertEquals(
                ComplexityAnalysisFingerprint.compute(input),
                ComplexityAnalysisFingerprint.compute(input));
    }

    @Test
    void changesWhenProfileHashChanges() {
        var base = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "profile-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", null, null, null);
        var changed = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "other-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", null, null, null);
        assertNotEquals(ComplexityAnalysisFingerprint.compute(base), ComplexityAnalysisFingerprint.compute(changed));
    }

    @Test
    void changesWhenSemanticProfileIdentityChanges() {
        var without = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "profile-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", null, null, null);
        var withProfile = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "profile-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", "spp-q1-v1", "v1", "hash-a");
        var otherProfile = new ComplexityAnalysisFingerprint.FingerprintInput(
                "abc", "static-v1", "growth-fit-v1", "hybrid-confidence-v1", "jdk-kb-v1",
                "profile-hash", "gen-v1", "harness-v1", "measure-v1", "jdk-21",
                "DYNAMIC_BENCHMARK", "spp-q1-v1", "v1", "hash-b");
        assertNotEquals(ComplexityAnalysisFingerprint.compute(without), ComplexityAnalysisFingerprint.compute(withProfile));
        assertNotEquals(ComplexityAnalysisFingerprint.compute(withProfile), ComplexityAnalysisFingerprint.compute(otherProfile));
    }
}
