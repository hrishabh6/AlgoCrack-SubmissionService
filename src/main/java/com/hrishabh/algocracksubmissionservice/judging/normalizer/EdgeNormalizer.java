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
 * Normalizes graph edge lists for unordered comparison.
 *
 * Normalizes each edge [a,b] → [min(a,b), max(a,b)] so that
 * edge direction doesn't matter, then sorts the edge list.
 *
 * Example: [[1,2],[3,1],[2,3]] → [[1,2],[1,3],[2,3]]
 *
 * For questions like Critical Connections (#3) where edge order and
 * direction within edges don't matter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeNormalizer implements OutputNormalizer {

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

        // Step 1: Normalize each edge [a,b] → [min,max]
        List<ArrayNode> normalizedEdges = new ArrayList<>();
        for (JsonNode edge : node) {
            if (edge.isArray() && edge.size() == 2) {
                int a = edge.get(0).asInt();
                int b = edge.get(1).asInt();

                ArrayNode normalizedEdge = objectMapper.createArrayNode();
                normalizedEdge.add(Math.min(a, b));
                normalizedEdge.add(Math.max(a, b));
                normalizedEdges.add(normalizedEdge);
            } else {
                // Non-standard edge, keep as-is
                ArrayNode wrapper = objectMapper.createArrayNode();
                edge.forEach(wrapper::add);
                normalizedEdges.add(wrapper);
            }
        }

        // Step 2: Sort edges by string representation
        normalizedEdges.sort(Comparator.comparing(ArrayNode::toString));

        // Rebuild the edge list
        ArrayNode result = objectMapper.createArrayNode();
        normalizedEdges.forEach(result::add);

        log.debug("[EdgeNormalizer] Normalized {} edges", normalizedEdges.size());
        return result;
    }
}
