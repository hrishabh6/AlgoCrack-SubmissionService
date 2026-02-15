package com.hrishabh.algocracksubmissionservice.judging.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Shared tree traversal and serialization utilities.
 *
 * Supports level-order (BFS) serialized tree format used by LeetCode:
 * [1, 2, 3, null, null, 4, 5]
 *
 * This utility class is used by extractors, comparators, and validators
 * that deal with tree structures. Keeping traversal logic here avoids
 * duplication across components.
 */
@Slf4j
public final class TreeTraversalUtil {

    private TreeTraversalUtil() {
        // Utility class — no instantiation
    }

    /**
     * Internal tree node representation for comparison purposes.
     */
    public static class TreeNode {
        public final JsonNode value;
        public TreeNode left;
        public TreeNode right;

        public TreeNode(JsonNode value) {
            this.value = value;
        }
    }

    /**
     * Build a tree from a level-order JSON array.
     * Format: [1, 2, 3, null, null, 4, 5]
     *
     * @param levelOrder JSON array in level-order format
     * @return Root TreeNode, or null if array is empty/null
     */
    public static TreeNode buildFromLevelOrder(JsonNode levelOrder) {
        if (levelOrder == null || !levelOrder.isArray() || levelOrder.isEmpty()) {
            return null;
        }

        if (levelOrder.get(0).isNull()) {
            return null;
        }

        TreeNode root = new TreeNode(levelOrder.get(0));
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);

        int i = 1;
        while (!queue.isEmpty() && i < levelOrder.size()) {
            TreeNode current = queue.poll();

            // Left child
            if (i < levelOrder.size()) {
                JsonNode leftVal = levelOrder.get(i++);
                if (!leftVal.isNull()) {
                    current.left = new TreeNode(leftVal);
                    queue.offer(current.left);
                }
            }

            // Right child
            if (i < levelOrder.size()) {
                JsonNode rightVal = levelOrder.get(i++);
                if (!rightVal.isNull()) {
                    current.right = new TreeNode(rightVal);
                    queue.offer(current.right);
                }
            }
        }

        return root;
    }

    /**
     * Serialize a tree to level-order JSON array.
     * Trailing nulls are trimmed for canonical form.
     *
     * @param root   The root TreeNode
     * @param mapper ObjectMapper for creating JSON nodes
     * @return JSON array in level-order format
     */
    public static ArrayNode toLevelOrder(TreeNode root, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        if (root == null) {
            return result;
        }

        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        List<JsonNode> elements = new ArrayList<>();

        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            if (current != null) {
                elements.add(current.value);
                queue.offer(current.left);
                queue.offer(current.right);
            } else {
                elements.add(NullNode.getInstance());
            }
        }

        // Trim trailing nulls for canonical form
        int lastNonNull = elements.size() - 1;
        while (lastNonNull >= 0 && elements.get(lastNonNull).isNull()) {
            lastNonNull--;
        }

        for (int i = 0; i <= lastNonNull; i++) {
            result.add(elements.get(i));
        }

        return result;
    }

    /**
     * Count the total number of nodes in a tree.
     */
    public static int countNodes(TreeNode root) {
        if (root == null)
            return 0;
        return 1 + countNodes(root.left) + countNodes(root.right);
    }

    /**
     * Check if two trees are structurally identical (same shape and values).
     *
     * @param a First tree root
     * @param b Second tree root
     * @return true if trees have identical structure and values
     */
    public static boolean isStructurallyEqual(TreeNode a, TreeNode b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;

        // Compare node values
        if (!a.value.equals(b.value))
            return false;

        // Recurse on both subtrees
        return isStructurallyEqual(a.left, b.left)
                && isStructurallyEqual(a.right, b.right);
    }

    /**
     * Check if a tree is a valid linked list (all left children are null).
     * Used by FlattenedTreeValidator.
     */
    public static boolean isLinkedList(TreeNode root) {
        TreeNode current = root;
        while (current != null) {
            if (current.left != null)
                return false;
            current = current.right;
        }
        return true;
    }
}
