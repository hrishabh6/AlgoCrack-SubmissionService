package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

public record StaticFindingDraft(
        String category,
        Integer startLine,
        Integer endLine,
        String expression,
        String certainty,
        String summary) {
}
