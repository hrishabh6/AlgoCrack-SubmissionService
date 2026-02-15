package com.hrishabh.algocracksubmissionservice.judging.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Set equality comparator — compares two collections as unordered sets.
 *
 * Delegates to JsonDeepComparator assuming inputs have been pre-sorted
 * by a normalizer. If both sides are sorted identically, deep equality
 * confirms set equivalence.
 *
 * Why this exists vs just using JsonDeepComparator directly:
 * It adds semantic clarity — "these are sets, not sequences" — and allows
 * for future optimizations (hash-based set comparison) without changing
 * callers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetEqualityComparator implements OutputComparator {

    private final ObjectMapper objectMapper;

    @Override
    public ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context) {

        // Both should be sorted JsonNodes from normalizer phase
        if (userNormalized instanceof JsonNode && oracleNormalized instanceof JsonNode) {
            JsonNode userNode = (JsonNode) userNormalized;
            JsonNode oracleNode = (JsonNode) oracleNormalized;

            // Size check first (fast fail)
            if (userNode.isArray() && oracleNode.isArray() && userNode.size() != oracleNode.size()) {
                return ComparisonResult.failed(
                        "Set size mismatch: got " + userNode.size() + ", expected " + oracleNode.size());
            }

            if (userNode.equals(oracleNode)) {
                return ComparisonResult.passed();
            }
            return ComparisonResult.failed("Set contents mismatch (elements differ after normalization)");
        }

        // Fallback: string comparison
        String userStr = userNormalized != null ? userNormalized.toString().trim() : null;
        String oracleStr = oracleNormalized != null ? oracleNormalized.toString().trim() : null;

        if (userStr == null || oracleStr == null) {
            return (userStr == oracleStr) ? ComparisonResult.passed()
                    : ComparisonResult.failed("Null mismatch");
        }

        if (userStr.equals(oracleStr)) {
            return ComparisonResult.passed();
        }

        // Try JSON parse and compare
        try {
            JsonNode userNode = objectMapper.readTree(userStr);
            JsonNode oracleNode = objectMapper.readTree(oracleStr);
            if (userNode.equals(oracleNode)) {
                return ComparisonResult.passed();
            }
        } catch (Exception e) {
            log.debug("[SetEqualityComparator] JSON parse failed: {}", e.getMessage());
        }

        return ComparisonResult.failed("Set contents mismatch");
    }
}
