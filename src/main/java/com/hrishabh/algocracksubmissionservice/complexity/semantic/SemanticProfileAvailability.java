package com.hrishabh.algocracksubmissionservice.complexity.semantic;

public enum SemanticProfileAvailability {
    COMPATIBLE,
    UNCHECKED,
    MISSING,
    STALE,
    INCOMPATIBLE,
    UNAVAILABLE;

    public String limitationCode() {
        return switch (this) {
            case COMPATIBLE -> null;
            case UNCHECKED -> "SEMANTIC_PROFILE_UNCHECKED";
            case MISSING -> "SEMANTIC_PROFILE_MISSING";
            case STALE -> "SEMANTIC_PROFILE_STALE";
            case INCOMPATIBLE -> "SEMANTIC_PROFILE_INCOMPATIBLE";
            case UNAVAILABLE -> "SEMANTIC_PROFILE_UNAVAILABLE";
        };
    }

    public boolean usable() {
        return this == COMPATIBLE;
    }
}
