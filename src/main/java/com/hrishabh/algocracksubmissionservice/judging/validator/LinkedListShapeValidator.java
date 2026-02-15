package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import com.hrishabh.algocracksubmissionservice.judging.util.TreeTraversalUtil;
import com.hrishabh.algocracksubmissionservice.judging.util.TreeTraversalUtil.TreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Class 2: Shape/Form Validator — LinkedListShapeValidator (POST_COMPARE)
 *
 * Validates that a tree output is actually in linked list form:
 * - All left children must be null
 * - Right pointers form a valid chain
 *
 * This catches the case where comparison passes (same values)
 * but the structural shape violates the problem's requirement.
 *
 * Example: Flatten Binary Tree (#6) requires output as right-only linked list.
 *
 * Trigger: validationHints contains "EXPECT_LINEAR_FORM"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedListShapeValidator implements OutputValidator {

    private final ObjectMapper objectMapper;

    @Override
    public ValidationStage getStage() {
        return ValidationStage.POST_COMPARE;
    }

    @Override
    public ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context) {
        JsonNode userJson = toJsonNode(userOutput);
        if (userJson == null || !userJson.isArray()) {
            // Can't validate non-JSON or non-array output
            return ValidationResult.passed();
        }

        // Build tree from level-order representation
        TreeNode tree = TreeTraversalUtil.buildFromLevelOrder(userJson);
        if (tree == null) {
            // Empty tree is technically a valid linked list
            return ValidationResult.passed();
        }

        // Check: must be a linked list (all left = null, only right pointers)
        if (!TreeTraversalUtil.isLinkedList(tree)) {
            return ValidationResult.failed(
                    "Output must be in linked list form (all left children null, " +
                            "only right pointers used). Got a tree with left children.");
        }

        int depth = countLinkedListDepth(tree);
        log.debug("[LinkedListShapeValidator] Output is valid linked list form ({} nodes)", depth);
        return ValidationResult.passed();
    }

    private int countLinkedListDepth(TreeNode root) {
        int count = 0;
        TreeNode current = root;
        while (current != null) {
            count++;
            current = current.right;
        }
        return count;
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
