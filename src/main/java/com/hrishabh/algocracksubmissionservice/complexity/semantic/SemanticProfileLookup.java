package com.hrishabh.algocracksubmissionservice.complexity.semantic;

public record SemanticProfileLookup(
        SemanticProfileAvailability availability,
        String profileId,
        String schemaVersion,
        String semanticVersion,
        String profileHash,
        SemanticProfileView profile) {

    public static SemanticProfileLookup unavailable() {
        return new SemanticProfileLookup(SemanticProfileAvailability.UNAVAILABLE, null, null, null, null, null);
    }

    public String limitationCode() {
        return availability.limitationCode();
    }

    public boolean usable() {
        return availability.usable()
                && profile != null
                && SemanticProfileView.SCHEMA_V1.equals(profile.schemaVersion());
    }
}
