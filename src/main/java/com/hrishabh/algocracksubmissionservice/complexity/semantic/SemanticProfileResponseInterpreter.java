package com.hrishabh.algocracksubmissionservice.complexity.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps the Problem Service semantic-profile HTTP result onto a closed availability.
 * Transport and contract failures become an unusable lookup. They do not throw.
 */
public final class SemanticProfileResponseInterpreter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SemanticProfileResponseInterpreter() {
    }

    public static SemanticProfileLookup interpret(int status, String body) {
        SemanticProfileResponse parsed = parse(body);
        if (status == 404 || (parsed != null && "SEMANTIC_PROFILE_MISSING".equals(parsed.code()))) {
            return unavailableIdentity(SemanticProfileAvailability.MISSING, parsed);
        }
        if (parsed != null && "SEMANTIC_PROFILE_STALE".equals(parsed.code())) {
            return unavailableIdentity(SemanticProfileAvailability.STALE, parsed);
        }
        if (parsed != null && "SEMANTIC_PROFILE_INCOMPATIBLE".equals(parsed.code())) {
            return unavailableIdentity(SemanticProfileAvailability.INCOMPATIBLE, parsed);
        }
        if (status >= 200 && status < 300 && parsed != null) {
            return compatibleOrUnchecked(parsed);
        }
        if (status == 409 && parsed != null && parsed.compatibility() != null) {
            return switch (parsed.compatibility()) {
                case "STALE" -> unavailableIdentity(SemanticProfileAvailability.STALE, parsed);
                case "INCOMPATIBLE" -> unavailableIdentity(SemanticProfileAvailability.INCOMPATIBLE, parsed);
                default -> SemanticProfileLookup.unavailable();
            };
        }
        return SemanticProfileLookup.unavailable();
    }

    public static SemanticProfileLookup interpretTransportFailure() {
        return SemanticProfileLookup.unavailable();
    }

    private static SemanticProfileLookup compatibleOrUnchecked(SemanticProfileResponse parsed) {
        if ("UNCHECKED".equals(parsed.compatibility())) {
            return unavailableIdentity(SemanticProfileAvailability.UNCHECKED, parsed);
        }
        if (!"COMPATIBLE".equals(parsed.compatibility())
                || parsed.profile() == null
                || !SemanticProfileView.SCHEMA_V1.equals(parsed.profile().schemaVersion())) {
            return unavailableIdentity(SemanticProfileAvailability.INCOMPATIBLE, parsed);
        }
        return new SemanticProfileLookup(
                SemanticProfileAvailability.COMPATIBLE,
                parsed.profileId(),
                parsed.schemaVersion(),
                parsed.semanticVersion(),
                parsed.profileHash(),
                parsed.profile());
    }

    private static SemanticProfileLookup unavailableIdentity(
            SemanticProfileAvailability availability, SemanticProfileResponse parsed) {
        if (parsed == null) {
            return new SemanticProfileLookup(availability, null, null, null, null, null);
        }
        return new SemanticProfileLookup(
                availability,
                parsed.profileId(),
                parsed.schemaVersion(),
                parsed.semanticVersion(),
                parsed.profileHash(),
                null);
    }

    private static SemanticProfileResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(body, SemanticProfileResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
