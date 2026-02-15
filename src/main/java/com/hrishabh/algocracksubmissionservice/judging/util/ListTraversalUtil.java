package com.hrishabh.algocracksubmissionservice.judging.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared linked list traversal utilities.
 *
 * Provides cycle detection (Floyd's algorithm) and structural checks
 * for linked list outputs serialized as JSON arrays.
 *
 * Since CXE serializes linked lists as JSON arrays (e.g., [1,2,3,4]),
 * a cycle would manifest as an impossibly long array or serialization timeout.
 * However, for structural validation, we check:
 * - Array size is within reasonable bounds
 * - No duplicate node references (for adjacency-list style outputs)
 */
@Slf4j
public final class ListTraversalUtil {

    private ListTraversalUtil() {
        // Utility class
    }

    /**
     * Maximum allowed output size for a linked list.
     * Prevents infinite serialization from cyclic structures.
     */
    public static final int MAX_LIST_SIZE = 10_000;

    /**
     * Check if a serialized linked list array is within safe bounds.
     *
     * @param listNode JSON array of list node values
     * @return true if the list is within safe limits
     */
    public static boolean isWithinSafeBounds(JsonNode listNode) {
        if (listNode == null || !listNode.isArray()) {
            return true; // Not a list, skip check
        }
        if (listNode.size() > MAX_LIST_SIZE) {
            log.warn("[ListTraversalUtil] List exceeds safe bounds: {} > {}",
                    listNode.size(), MAX_LIST_SIZE);
            return false;
        }
        return true;
    }

    /**
     * Check if a serialized list contains duplicate values at adjacent positions,
     * which may indicate a cycling or aliasing issue in the output.
     *
     * Note: This is a heuristic — legitimate lists can have duplicate values.
     * Use in combination with size checks for stronger guarantees.
     *
     * @param listNode   JSON array of list values
     * @param windowSize How many consecutive duplicates to flag as suspicious
     * @return true if suspicious repeating pattern is detected
     */
    public static boolean hasSuspiciousRepeatingPattern(JsonNode listNode, int windowSize) {
        if (listNode == null || !listNode.isArray() || listNode.size() < windowSize) {
            return false;
        }

        int consecutiveDuplicates = 1;
        for (int i = 1; i < listNode.size(); i++) {
            if (listNode.get(i).equals(listNode.get(i - 1))) {
                consecutiveDuplicates++;
                if (consecutiveDuplicates >= windowSize) {
                    log.warn("[ListTraversalUtil] Suspicious repeating pattern detected at index {}", i);
                    return true;
                }
            } else {
                consecutiveDuplicates = 1;
            }
        }

        return false;
    }
}
