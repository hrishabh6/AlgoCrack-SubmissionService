package com.hrishabh.algocracksubmissionservice.complexity.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

public final class ComplexityAnalysisFingerprint {

    private ComplexityAnalysisFingerprint() {
    }

    public static String compute(FingerprintInput input) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(input.sourceSha256());
        joiner.add(nullToEmpty(input.analyzerVersion()));
        joiner.add(nullToEmpty(input.inferenceVersion()));
        joiner.add(nullToEmpty(input.confidenceModelVersion()));
        joiner.add(nullToEmpty(input.knowledgeBaseVersion()));
        joiner.add(input.profileHash() != null ? input.profileHash() : ComplexityVersionConstants.NO_PROFILE_SENTINEL);
        joiner.add(input.generatorVersion() != null ? input.generatorVersion() : ComplexityVersionConstants.NO_GENERATOR_SENTINEL);
        joiner.add(nullToEmpty(input.harnessVersion()));
        joiner.add(nullToEmpty(input.measurementPolicyVersion()));
        joiner.add(nullToEmpty(input.profilerRuntimeVersion()));
        joiner.add(ComplexityVersionConstants.RECONCILIATION_VERSION);
        joiner.add(input.interpretationPath());
        return sha256(joiner.toString());
    }

    public record FingerprintInput(
            String sourceSha256,
            String analyzerVersion,
            String inferenceVersion,
            String confidenceModelVersion,
            String knowledgeBaseVersion,
            String profileHash,
            String generatorVersion,
            String harnessVersion,
            String measurementPolicyVersion,
            String profilerRuntimeVersion,
            String interpretationPath) {
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
