package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.NodeType;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import com.hrishabh.algocracksubmissionservice.judging.util.ListTraversalUtil;
import com.hrishabh.algocracksubmissionservice.judging.util.TreeTraversalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Class 1: Structural Safety Validator (PRE_COMPARE)
 *
 * Protects the judge from outputs that would cause crashes:
 * - Linked lists with cycles → serialized as impossibly long arrays
 * - Trees with back-edges → depth exceeds node count
 * - Outputs exceeding safe size bounds
 *
 * Runs BEFORE comparison — if output is structurally unsafe,
 * comparison is skipped entirely (prevents infinite loops, stack overflows).
 *
 * Trigger: nodeType == LIST_NODE || nodeType == TREE_NODE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuralSafetyValidator implements OutputValidator {

    private final ObjectMapper objectMapper;

    @Override
    public ValidationStage getStage() {
        return ValidationStage.PRE_COMPARE;
    }

    @Override
    public ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context) {
        NodeType nodeType = context.getNodeType();

        if (nodeType == NodeType.LIST_NODE) {
            return validateListSafety(userOutput);
        }

        if (nodeType == NodeType.TREE_NODE) {
            return validateTreeSafety(userOutput);
        }

        // No structural safety concern for other types
        return ValidationResult.passed();
    }

    private ValidationResult validateListSafety(Object userOutput) {
        JsonNode node = toJsonNode(userOutput);
        if (node == null) {
            return ValidationResult.passed(); // Can't validate non-JSON, let comparator handle
        }

        // Check 1: Size bounds (cycle would produce huge array)
        if (!ListTraversalUtil.isWithinSafeBounds(node)) {
            return ValidationResult.failed(
                    "List output exceeds maximum safe size (" + ListTraversalUtil.MAX_LIST_SIZE
                            + " elements). Possible cycle in output.");
        }

        // Check 2: Suspicious repeating pattern (strong cycle indicator)
        if (ListTraversalUtil.hasSuspiciousRepeatingPattern(node, 50)) {
            return ValidationResult.failed(
                    "List output contains suspicious repeating pattern. Possible cycle in output.");
        }

        log.debug("[StructuralSafetyValidator] List output passed safety checks (size: {})", node.size());
        return ValidationResult.passed();
    }

    private ValidationResult validateTreeSafety(Object userOutput) {
        JsonNode node = toJsonNode(userOutput);
        if (node == null || !node.isArray()) {
            return ValidationResult.passed();
        }

        // Check 1: Size bounds
        if (node.size() > ListTraversalUtil.MAX_LIST_SIZE) {
            return ValidationResult.failed(
                    "Tree output exceeds maximum safe size (" + ListTraversalUtil.MAX_LIST_SIZE
                            + " elements). Possible structural issue.");
        }

        // Check 2: Build tree and verify node count matches array size expectations
        TreeTraversalUtil.TreeNode tree = TreeTraversalUtil.buildFromLevelOrder(node);
        int nodeCount = TreeTraversalUtil.countNodes(tree);

        // In level-order, array size should be <= 2*nodeCount + 1 (with nulls)
        // A bloated array suggests back-edges or structural corruption
        if (node.size() > 2 * nodeCount + 1) {
            log.warn("[StructuralSafetyValidator] Tree array size {} >> node count {} — suspicious",
                    node.size(), nodeCount);
        }

        log.debug("[StructuralSafetyValidator] Tree output passed safety checks (nodes: {})", nodeCount);
        return ValidationResult.passed();
    }

    private JsonNode toJsonNode(Object obj) {
        if (obj instanceof JsonNode)
            return (JsonNode) obj;
        if (obj == null)
            return null;
        try {
            return objectMapper.readTree(obj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
