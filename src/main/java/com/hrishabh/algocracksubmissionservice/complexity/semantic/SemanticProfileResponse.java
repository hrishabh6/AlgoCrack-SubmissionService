package com.hrishabh.algocracksubmissionservice.complexity.semantic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticProfileResponse(
        String code,
        String profileId,
        String schemaVersion,
        String semanticVersion,
        String status,
        String profileHash,
        String compatibility,
        List<String> reasons,
        SemanticProfileView profile) {
}
