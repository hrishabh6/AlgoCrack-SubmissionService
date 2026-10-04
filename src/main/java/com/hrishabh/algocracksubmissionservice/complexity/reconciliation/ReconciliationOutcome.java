package com.hrishabh.algocracksubmissionservice.complexity.reconciliation;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;

import java.util.List;

public record ReconciliationOutcome(
        ComplexityResultKind resultKind,
        ComplexityConfidence timeConfidence,
        String timeBigO,
        String timeExpression,
        List<String> reasonCodes,
        List<String> limitations) {
}
