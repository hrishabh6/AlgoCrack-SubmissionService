package com.hrishabh.algocracksubmissionservice.judging.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorts the top-level elements of a JSON array for unordered comparison.
 * Used when isOutputOrderMatters == false for simple (non-nested) list outputs.
 *
 * Example: ["cat","bat","sat"] → ["bat","cat","sat"]
 *
 * For questions like Word Break II (#5) where output order doesn't matter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SortedListNormalizer implements OutputNormalizer {

    private final ObjectMapper objectMapper;

    @Override
    public Object normalize(Object extracted, JudgingContext context) {
        if (!(extracted instanceof JsonNode)) {
            // Fall through — can't normalize non-JSON
            return extracted;
        }

        JsonNode node = (JsonNode) extracted;
        if (!node.isArray()) {
            return extracted;
        }

        // Collect elements, sort by their string representation
        List<JsonNode> elements = new ArrayList<>();
        node.forEach(elements::add);

        elements.sort(Comparator.comparing(JsonNode::toString));

        // Rebuild sorted array
        ArrayNode sorted = objectMapper.createArrayNode();
        elements.forEach(sorted::add);

        log.debug("[SortedListNormalizer] Sorted {} elements", elements.size());
        return sorted;
    }
}
