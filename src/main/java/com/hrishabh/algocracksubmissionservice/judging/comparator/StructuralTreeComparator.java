package com.hrishabh.algocracksubmissionservice.judging.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import com.hrishabh.algocracksubmissionservice.judging.util.TreeTraversalUtil;
import com.hrishabh.algocracksubmissionservice.judging.util.TreeTraversalUtil.TreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Structural tree comparator — compares two tree outputs for structural
 * equivalence.
 *
 * Parses level-order JSON arrays into tree structures, then performs
 * recursive structural comparison. This handles cases where different
 * serialization orderings might produce different level-order arrays
 * for structurally equivalent trees.
 *
 * Used for:
 * - Serialize/Deserialize Binary Tree (#4)
 * - Clone Graph (#8) — treats adjacency list as structural data
 *
 * Delegates traversal logic to TreeTraversalUtil to keep this class
 * focused on comparison only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuralTreeComparator implements OutputComparator {

    private final ObjectMapper objectMapper;

    @Override
    public ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context) {

        // Both must be parseable as JSON arrays (level-order tree format)
        JsonNode userJson = toJsonNode(userNormalized);
        JsonNode oracleJson = toJsonNode(oracleNormalized);

        if (userJson == null || oracleJson == null) {
            // Fallback: string comparison if not JSON
            String userStr = userNormalized != null ? userNormalized.toString().trim() : null;
            String oracleStr = oracleNormalized != null ? oracleNormalized.toString().trim() : null;
            if (userStr != null && userStr.equals(oracleStr)) {
                return ComparisonResult.passed();
            }
            return ComparisonResult.failed("Cannot parse tree output for structural comparison");
        }

        // Build trees from level-order arrays
        TreeNode userTree = TreeTraversalUtil.buildFromLevelOrder(userJson);
        TreeNode oracleTree = TreeTraversalUtil.buildFromLevelOrder(oracleJson);

        // Node count fast-fail
        int userCount = TreeTraversalUtil.countNodes(userTree);
        int oracleCount = TreeTraversalUtil.countNodes(oracleTree);
        if (userCount != oracleCount) {
            return ComparisonResult.failed(
                    "Tree size mismatch: got " + userCount + " nodes, expected " + oracleCount);
        }

        // Recursive structural comparison
        if (TreeTraversalUtil.isStructurallyEqual(userTree, oracleTree)) {
            log.debug("[StructuralTreeComparator] Trees are structurally equal ({} nodes)", userCount);
            return ComparisonResult.passed();
        }

        return ComparisonResult.failed("Tree structure mismatch");
    }

    /**
     * Convert an Object to JsonNode. Handles both pre-parsed JsonNode
     * and raw strings.
     */
    private JsonNode toJsonNode(Object obj) {
        if (obj instanceof JsonNode) {
            return (JsonNode) obj;
        }
        if (obj == null)
            return null;

        try {
            return objectMapper.readTree(obj.toString().trim());
        } catch (Exception e) {
            log.debug("[StructuralTreeComparator] Failed to parse as JSON: {}", e.getMessage());
            return null;
        }
    }
}
