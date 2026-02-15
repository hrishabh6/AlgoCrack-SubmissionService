package com.hrishabh.algocracksubmissionservice.judging.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Normalizes char[][] outputs into canonical 2D array representation.
 *
 * Jackson serializes Java char[] as String, not as a JSON array.
 * This means char[][] becomes ["519748632", "783652419", ...]
 * instead of [["5","1","9",...], ["7","8","3",...], ...].
 *
 * This normalizer transforms string-per-row format into char-array-per-row
 * format so that downstream comparators and validators operate on a
 * consistent logical structure — a 2D array of single characters.
 *
 * Triggered by: returnType == "char[][]"
 *
 * Handles both formats gracefully:
 * - If row is a string → split into individual characters
 * - If row is already an array → pass through unchanged
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharMatrixNormalizer implements OutputNormalizer {

    private final ObjectMapper objectMapper;

    @Override
    public Object normalize(Object extracted, JudgingContext context) {
        if (extracted == null) {
            return null;
        }

        JsonNode node = toJsonNode(extracted);
        if (node == null || !node.isArray()) {
            log.debug("[CharMatrixNormalizer] Input is not an array, passing through");
            return extracted;
        }

        ArrayNode result = objectMapper.createArrayNode();
        boolean transformed = false;

        for (int i = 0; i < node.size(); i++) {
            JsonNode row = node.get(i);

            if (row.isTextual()) {
                // "519748632" → ["5","1","9","7","4","8","6","3","2"]
                String rowStr = row.asText();
                ArrayNode charArray = objectMapper.createArrayNode();
                for (int j = 0; j < rowStr.length(); j++) {
                    charArray.add(String.valueOf(rowStr.charAt(j)));
                }
                result.add(charArray);
                transformed = true;
            } else {
                // Already an array — keep as-is
                result.add(row);
            }
        }

        if (transformed) {
            log.debug("[CharMatrixNormalizer] Normalized {} rows from string to char array form",
                    node.size());
        }

        return result;
    }

    private JsonNode toJsonNode(Object obj) {
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        try {
            return objectMapper.readTree(obj.toString().trim());
        } catch (Exception e) {
            log.debug("[CharMatrixNormalizer] Failed to parse as JSON: {}", e.getMessage());
            return null;
        }
    }
}
