package com.hrishabh.algocracksubmissionservice.progress.rank;

public enum NormalizedDifficulty {
    EASY,
    MEDIUM,
    HARD,
    UNKNOWN;

    public static NormalizedDifficulty fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "EASY" -> EASY;
            case "MEDIUM" -> MEDIUM;
            case "HARD" -> HARD;
            default -> UNKNOWN;
        };
    }
}
