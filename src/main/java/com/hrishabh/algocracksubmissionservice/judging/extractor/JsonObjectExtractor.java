package com.hrishabh.algocracksubmissionservice.judging.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts raw output as a parsed JSON object (JsonNode).
 * Used for complex return types like graphs, maps, or nested objects.
 * Falls back to raw string if JSON parsing fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonObjectExtractor implements OutputExtractor {

    private final ObjectMapper objectMapper;

    @Override
    public Object extract(String rawOutput, JudgingContext context) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return rawOutput;
        }

        try {
            JsonNode node = objectMapper.readTree(rawOutput.trim());
            return node;
        } catch (Exception e) {
            log.debug("[JsonObjectExtractor] JSON parse failed, returning raw string: {}", e.getMessage());
            return rawOutput;
        }
    }
}
