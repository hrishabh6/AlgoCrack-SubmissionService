package com.hrishabh.algocracksubmissionservice.judging.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts meaningful outputs from design-class execution results.
 *
 * CXE returns per-operation result arrays for design-class problems:
 *   [result_op0, result_op1, ..., result_opN]
 *
 * Different design-class patterns require different extraction strategies:
 *
 * ROUND_TRIP (Codec, Encode/Decode):
 *   Only the final round-trip result matters.
 *   Input:  [null, "serialized_string", [1,null,2,null,3]]
 *   Output: JsonNode representing [1,null,2,null,3]  (last element only)
 *
 * STATEFUL_SEQUENCE (LRU Cache, RandomizedSet):
 *   All observable outputs matter (skip constructor null).
 *   Input:  [null, -1, true, false, 3]
 *   Output: JsonNode representing [-1, true, false, 3]
 *   Assumes first operation is constructor (returns null). This covers 99%
 *   of design-class problems on LeetCode.
 *
 * Returns structured JsonNode, NOT strings.
 * Comparator operates on structured data to preserve type/structure info.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DesignClassExtractor implements OutputExtractor {

    private final ObjectMapper objectMapper;

    @Override
    public Object extract(String rawOutput, JudgingContext context) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return rawOutput;
        }

        List<String> hints = context.getValidationHints();

        try {
            JsonNode array = objectMapper.readTree(rawOutput);

            if (!array.isArray() || array.isEmpty()) {
                log.warn("[DesignClassExtractor] Output is not a non-empty array, falling through: {}",
                        rawOutput);
                return rawOutput;
            }

            if (hints != null && hints.contains("ROUND_TRIP")) {
                return extractRoundTrip(array);
            }

            if (hints != null && hints.contains("STATEFUL_SEQUENCE")) {
                return extractStatefulSequence(array);
            }

            // Default for CLASS with no specific hint: return parsed array as-is
            log.debug("[DesignClassExtractor] No design hint, returning parsed array");
            return array;

        } catch (Exception e) {
            log.error("[DesignClassExtractor] Failed to parse output: {}", e.getMessage());
            return rawOutput;
        }
    }

    /**
     * ROUND_TRIP: extract only the last element (the round-trip result).
     *
     * For Codec: [null, "serialized", [tree]] → JsonNode of [tree]
     * The serialized string may differ between implementations, but
     * deserialize(serialize(tree)) must equal the original tree.
     *
     * Returns the JsonNode directly — preserves structure for comparator.
     */
    private JsonNode extractRoundTrip(JsonNode array) {
        JsonNode lastElement = array.get(array.size() - 1);
        log.debug("[DesignClassExtractor] ROUND_TRIP: extracted last element (type={})",
                lastElement.getNodeType());
        return lastElement;
    }

    /**
     * STATEFUL_SEQUENCE: extract all observable outputs, skipping constructor.
     *
     * For LRU Cache: [null, null, null, 1, -1, ...] → ArrayNode of [null, null, 1, -1, ...]
     * Element 0 is always the constructor result (null), skipped.
     * All subsequent elements are observable method return values.
     *
     * Returns ArrayNode — preserves structure for comparator.
     */
    private ArrayNode extractStatefulSequence(JsonNode array) {
        ArrayNode filtered = objectMapper.createArrayNode();
        for (int i = 1; i < array.size(); i++) {
            filtered.add(array.get(i));
        }
        log.debug("[DesignClassExtractor] STATEFUL_SEQUENCE: extracted {} observable outputs", filtered.size());
        return filtered;
    }
}
