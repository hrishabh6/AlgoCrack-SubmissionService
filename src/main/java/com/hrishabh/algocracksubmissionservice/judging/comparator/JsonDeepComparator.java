package com.hrishabh.algocracksubmissionservice.judging.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deep JSON comparison using Jackson's JsonNode.equals().
 *
 * More flexible than ExactMatchComparator — works directly on parsed JSON objects
 * rather than strings. This allows upstream normalizers to sort/transform the
 * JSON structure before comparison.
 *
 * Used when output has been parsed to JsonNode and normalized (sorted, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonDeepComparator implements OutputComparator {

    private final ObjectMapper objectMapper;

    @Override
    public ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context) {

        // Both are JsonNode — deep structural equality
        if (userNormalized instanceof JsonNode && oracleNormalized instanceof JsonNode) {
            JsonNode userNode = (JsonNode) userNormalized;
            JsonNode oracleNode = (JsonNode) oracleNormalized;

            if (userNode.equals(oracleNode)) {
                return ComparisonResult.passed();
            }
            return ComparisonResult.failed("Output mismatch (JSON deep comparison)");
        }

        // Fallback: try to parse both as JSON and compare
        String userStr = userNormalized != null ? userNormalized.toString().trim() : null;
        String oracleStr = oracleNormalized != null ? oracleNormalized.toString().trim() : null;

        if (userStr == null || oracleStr == null) {
            boolean match = userStr == oracleStr;
            return match ? ComparisonResult.passed()
                    : ComparisonResult.failed("Null mismatch");
        }

        // Direct string match as last resort
        if (userStr.equals(oracleStr)) {
            return ComparisonResult.passed();
        }

        // Try JSON parse
        try {
            JsonNode userNode = objectMapper.readTree(userStr);
            JsonNode oracleNode = objectMapper.readTree(oracleStr);
            if (userNode.equals(oracleNode)) {
                return ComparisonResult.passed();
            }
        } catch (Exception e) {
            log.debug("[JsonDeepComparator] JSON parse failed: {}", e.getMessage());
        }

        return ComparisonResult.failed("Output mismatch");
    }
}
