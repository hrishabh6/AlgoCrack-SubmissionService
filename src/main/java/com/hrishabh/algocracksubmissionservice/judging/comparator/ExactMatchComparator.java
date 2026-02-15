package com.hrishabh.algocracksubmissionservice.judging.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Exact match comparator — replicates the current
 * ResultValidationService.outputsMatch() behavior.
 *
 * Comparison order:
 * 1. Null check
 * 2. Trim whitespace
 * 3. Direct string equality
 * 4. JSON tree equality (fallback for complex types)
 * 5. String equality (final fallback if JSON parsing fails)
 *
 * This is the default comparator used when no special comparison is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExactMatchComparator implements OutputComparator {

    private final ObjectMapper objectMapper;

    @Override
    public ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context) {
        String actual = userNormalized != null ? userNormalized.toString() : null;
        String expected = oracleNormalized != null ? oracleNormalized.toString() : null;

        // Null check
        if (actual == null || expected == null) {
            boolean match = actual == expected;
            return match ? ComparisonResult.passed()
                    : ComparisonResult.failed("Null mismatch: actual=" + actual + ", expected=" + expected);
        }

        // Normalize whitespace
        actual = actual.trim();
        expected = expected.trim();

        // Direct string equality
        if (actual.equals(expected)) {
            return ComparisonResult.passed();
        }

        // Try JSON comparison for arrays/objects
        try {
            JsonNode actualNode = objectMapper.readTree(actual);
            JsonNode expectedNode = objectMapper.readTree(expected);
            if (actualNode.equals(expectedNode)) {
                return ComparisonResult.passed();
            }
            return ComparisonResult.failed("Output mismatch");
        } catch (Exception e) {
            // Not valid JSON, fall back to string comparison
            log.debug("JSON parsing failed, using string comparison: {}", e.getMessage());
            return actual.equals(expected)
                    ? ComparisonResult.passed()
                    : ComparisonResult.failed("Output mismatch");
        }
    }
}
