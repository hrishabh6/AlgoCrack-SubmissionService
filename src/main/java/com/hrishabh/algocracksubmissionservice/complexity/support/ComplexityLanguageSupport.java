package com.hrishabh.algocracksubmissionservice.complexity.support;

import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityLanguageUnsupportedException;

public final class ComplexityLanguageSupport {

    private ComplexityLanguageSupport() {
    }

    public static String normalizeStoredLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            throw new ComplexityLanguageUnsupportedException("Language is missing");
        }
        String normalized = rawLanguage.trim();
        if (normalized.equalsIgnoreCase("java")) {
            return "JAVA";
        }
        throw new ComplexityLanguageUnsupportedException("Only Java is supported in V1");
    }

    public static boolean isEligibleJavaLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return false;
        }
        return rawLanguage.trim().equalsIgnoreCase("java");
    }
}
