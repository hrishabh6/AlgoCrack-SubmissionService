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
 * Sorts nested list output for unordered comparison.
 *
 * First sorts elements within each inner list, then sorts the outer list
 * by string representation of each inner list.
 *
 * Example: [[3,0,1],[2,6],[8,-1,3]] → [[-1,3,8],[0,1,3],[2,6]]
 *
 * For questions like 4Sum (#10) where both inner and outer order don't matter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SortedNestedListNormalizer implements OutputNormalizer {

    private final ObjectMapper objectMapper;

    @Override
    public Object normalize(Object extracted, JudgingContext context) {
        if (!(extracted instanceof JsonNode)) {
            return extracted;
        }

        JsonNode node = (JsonNode) extracted;
        if (!node.isArray()) {
            return extracted;
        }

        // Step 1: Sort elements within each inner list
        List<ArrayNode> normalizedInner = new ArrayList<>();
        for (JsonNode innerElement : node) {
            if (innerElement.isArray()) {
                // Sort the inner array elements
                List<JsonNode> innerList = new ArrayList<>();
                innerElement.forEach(innerList::add);
                innerList.sort(Comparator.comparing(JsonNode::toString));

                ArrayNode sortedInner = objectMapper.createArrayNode();
                innerList.forEach(sortedInner::add);
                normalizedInner.add(sortedInner);
            } else {
                // Not a nested array, wrap as single-element array for consistency
                ArrayNode wrapper = objectMapper.createArrayNode();
                wrapper.add(innerElement);
                normalizedInner.add(wrapper);
            }
        }

        // Step 2: Sort the outer list by string representation
        normalizedInner.sort(Comparator.comparing(ArrayNode::toString));

        // Rebuild the outer array
        ArrayNode result = objectMapper.createArrayNode();
        normalizedInner.forEach(result::add);

        log.debug("[SortedNestedListNormalizer] Normalized {} inner lists", normalizedInner.size());
        return result;
    }
}
