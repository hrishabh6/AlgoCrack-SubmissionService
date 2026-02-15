package com.hrishabh.algocracksubmissionservice.judging;

import com.hrishabh.algocrackentityservice.models.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context passed to all judging pipeline phases.
 * Contains all metadata needed for judging decisions.
 * 
 * Decoupled from entity persistence — assembled from metadata
 * already fetched during execution setup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgingContext {

    /**
     * Return type of the function (e.g., "int", "void", "ListNode",
     * "List<List<Integer>>").
     */
    private String returnType;

    /**
     * Structural hint for frontend visualization (tree, graph, list).
     * Null if not applicable.
     */
    private NodeType nodeType;

    /**
     * Whether output array order matters during comparison.
     * False means unordered comparison is needed.
     */
    private Boolean isOutputOrderMatters;

    /**
     * Execution strategy (e.g., "function", "class").
     */
    private String executionStrategy;

    /**
     * Question ID for logging/tracking.
     */
    private Long questionId;

    /**
     * Which parameter is mutated (for void return types).
     * e.g., "input[0]"
     */
    private String mutationTarget;

    /**
     * How to serialize the result (for void return types).
     * e.g., "LEVEL_ORDER", "PREORDER", "ARRAY"
     */
    private String serializationStrategy;

    /**
     * Declarative hints for validator selection.
     * e.g., ["SUDOKU_RULES", "EXPECT_LINEAR_FORM", "REQUIRE_DEEP_COPY"]
     */
    private List<String> validationHints;

    /**
     * Question type from metadata (e.g. "DESIGN_CLASS", "FUNCTION").
     * Used by PipelineAssembler for design-class routing.
     */
    private String questionType;

    /**
     * The actual type of the serialized output.
     * For non-void returns: same as returnType.
     * For void returns with mutationTarget: the mutation target's parameter type
     * (e.g., "char[][]" for solveSudoku which mutates the board in-place).
     * 
     * This allows normalizers and comparators to route based on the
     * actual output format, not the function signature.
     */
    private String effectiveOutputType;
}
