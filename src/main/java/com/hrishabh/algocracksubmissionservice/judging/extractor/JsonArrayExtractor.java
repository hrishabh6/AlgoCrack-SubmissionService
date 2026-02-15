package com.hrishabh.algocracksubmissionservice.judging.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts raw output as a parsed JSON array (JsonNode).
 * Used when returnType contains "List" or output is array-shaped.
 * Falls back to raw string if JSON parsing fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonArrayExtractor implements OutputExtractor {

    private final ObjectMapper objectMapper;

    @Override
    public Object extract(String rawOutput, JudgingContext context) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return rawOutput;
        }

        try {
            JsonNode node = objectMapper.readTree(rawOutput.trim());
            if (node.isArray()) {
                return node;
            }
            // Not an array — return as-is for downstream handling
            log.debug("[JsonArrayExtractor] Output is not a JSON array, returning raw: {}", rawOutput);
            return rawOutput;
        } catch (Exception e) {
            log.debug("[JsonArrayExtractor] JSON parse failed, returning raw string: {}", e.getMessage());
            return rawOutput;
        }
    }
}
