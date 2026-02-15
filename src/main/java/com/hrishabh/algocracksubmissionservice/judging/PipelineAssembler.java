package com.hrishabh.algocracksubmissionservice.judging;

import com.hrishabh.algocrackentityservice.models.NodeType;
import com.hrishabh.algocracksubmissionservice.judging.comparator.ExactMatchComparator;
import com.hrishabh.algocracksubmissionservice.judging.comparator.JsonDeepComparator;
import com.hrishabh.algocracksubmissionservice.judging.comparator.OutputComparator;
import com.hrishabh.algocracksubmissionservice.judging.comparator.SetEqualityComparator;
import com.hrishabh.algocracksubmissionservice.judging.comparator.StructuralTreeComparator;
import com.hrishabh.algocracksubmissionservice.judging.extractor.DesignClassExtractor;
import com.hrishabh.algocracksubmissionservice.judging.extractor.IdentityExtractor;
import com.hrishabh.algocracksubmissionservice.judging.extractor.JsonArrayExtractor;
import com.hrishabh.algocracksubmissionservice.judging.extractor.OutputExtractor;
import com.hrishabh.algocracksubmissionservice.judging.normalizer.*;
import com.hrishabh.algocracksubmissionservice.judging.validator.DeepCopyValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.DesignClassSizeValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.LinkedListShapeValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.OutputValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.StructuralSafetyValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.SudokuConstraintValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a JudgingPipeline from reusable phase components based on question
 * metadata.
 *
 * Each select*() method is independent — normalizer selection doesn't affect
 * extractor selection.
 * Adding a new normalizer doesn't require changing comparator logic.
 * Each method stays small and testable in isolation.
 *
 * Phase 1: Identity pipeline (ExactMatch behavior).
 * Phase 2: Adds unordered comparison (JsonArray extraction, sorting
 * normalizers, set/deep comparators).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineAssembler {

    // Phase 1 components (identity pipeline)
    private final IdentityExtractor identityExtractor;
    private final IdentityNormalizer identityNormalizer;
    private final ExactMatchComparator exactMatchComparator;

    // Phase 2 components (unordered comparison)
    private final JsonArrayExtractor jsonArrayExtractor;
    private final SortedListNormalizer sortedListNormalizer;
    private final SortedNestedListNormalizer sortedNestedListNormalizer;
    private final EdgeNormalizer edgeNormalizer;
    private final JsonDeepComparator jsonDeepComparator;
    private final SetEqualityComparator setEqualityComparator;

    // Phase 3 components (node type support)
    private final StructuralTreeComparator structuralTreeComparator;

    // Phase 4 components (validators)
    private final StructuralSafetyValidator structuralSafetyValidator;
    private final LinkedListShapeValidator linkedListShapeValidator;
    private final SudokuConstraintValidator sudokuConstraintValidator;
    private final DeepCopyValidator deepCopyValidator;

    // Representation normalizers (trait-based)
    private final CharMatrixNormalizer charMatrixNormalizer;

    // Phase 7 components (design-class behavioral validation)
    private final DesignClassExtractor designClassExtractor;
    private final DesignClassSizeValidator designClassSizeValidator;

    /**
     * Assemble a judging pipeline based on question metadata.
     * Pipeline is assembled ONCE per question (outside per-testcase loop).
     *
     * @param context Judging context with question metadata
     * @return Assembled JudgingPipeline ready to judge testcases
     */
    public JudgingPipeline assemble(JudgingContext context) {
        log.debug(
                "[PipelineAssembler] Assembling pipeline for question {} — returnType={}, effectiveOutputType={}, orderMatters={}, nodeType={}",
                context.getQuestionId(), context.getReturnType(), context.getEffectiveOutputType(),
                context.getIsOutputOrderMatters(), context.getNodeType());

        return JudgingPipeline.builder()
                .extractor(selectExtractor(context))
                .normalizer(selectNormalizer(context))
                .comparator(selectComparator(context))
                .validators(selectValidators(context))
                .build();
    }

    // ---- Phase selection methods (each independent, no combinatorial coupling)
    // ----

    private OutputExtractor selectExtractor(JudgingContext ctx) {
        String returnType = ctx.getReturnType();
        NodeType nodeType = ctx.getNodeType();

        // Phase 7: Design-class questions → extract meaningful invariant
        if ("CLASS".equalsIgnoreCase(ctx.getExecutionStrategy())) {
            log.debug("[PipelineAssembler] Using DesignClassExtractor for CLASS execution");
            return designClassExtractor;
        }

        // Tree/list node output → parse as JSON array (level-order format)
        if (nodeType == NodeType.TREE_NODE || nodeType == NodeType.LIST_NODE) {
            log.debug("[PipelineAssembler] Using JsonArrayExtractor for nodeType: {}", nodeType);
            return jsonArrayExtractor;
        }

        // If output is array-shaped, parse as JSON array
        if (returnType != null && isListType(returnType)) {
            log.debug("[PipelineAssembler] Using JsonArrayExtractor for returnType: {}", returnType);
            return jsonArrayExtractor;
        }

        // Default: identity (pass-through)
        return identityExtractor;
    }

    private OutputNormalizer selectNormalizer(JudgingContext ctx) {
        // Representation normalization — applies regardless of order.
        // Jackson serializes char[] as String, so char[][] becomes ["abc","def"]
        // instead of [["a","b","c"],["d","e","f"]]. Canonicalize to 2D array.
        // Use effectiveOutputType (resolves void + mutationTarget → param type).
        String effectiveType = ctx.getEffectiveOutputType();
        if ("char[][]".equals(effectiveType)) {
            log.debug("[PipelineAssembler] Using CharMatrixNormalizer for effectiveOutputType: {}", effectiveType);
            return charMatrixNormalizer;
        }

        // Order-dependent normalization
        Boolean orderMatters = ctx.getIsOutputOrderMatters();
        if (orderMatters == null || orderMatters) {
            // Order matters or unknown → no normalization
            return identityNormalizer;
        }

        // Order doesn't matter → need sorting
        String returnType = ctx.getReturnType();
        NodeType nodeType = ctx.getNodeType();

        // Graph edge output → normalize edge directions
        if (nodeType == NodeType.GRAPH_NODE
                || (returnType != null && returnType.toLowerCase().contains("edge"))) {
            log.debug("[PipelineAssembler] Using EdgeNormalizer");
            return edgeNormalizer;
        }

        // Nested list → sort inner and outer
        if (returnType != null && isNestedListType(returnType)) {
            log.debug("[PipelineAssembler] Using SortedNestedListNormalizer for: {}", returnType);
            return sortedNestedListNormalizer;
        }

        // Simple list → sort top-level elements
        if (returnType != null && isListType(returnType)) {
            log.debug("[PipelineAssembler] Using SortedListNormalizer for: {}", returnType);
            return sortedListNormalizer;
        }

        // Fallback: identity
        return identityNormalizer;
    }

    private OutputComparator selectComparator(JudgingContext ctx) {
        Boolean orderMatters = ctx.getIsOutputOrderMatters();
        NodeType nodeType = ctx.getNodeType();

        // Tree node output → structural tree comparison
        // For CLASS questions, nodeType drives comparator selection on the EXTRACTED
        // output
        // (e.g., Codec extracts deserialized tree → compare structurally)
        if (nodeType == NodeType.TREE_NODE) {
            log.debug("[PipelineAssembler] Using StructuralTreeComparator for TREE_NODE");
            return structuralTreeComparator;
        }

        // Unordered comparison → set equality (works with pre-sorted normalized output)
        if (orderMatters != null && !orderMatters) {
            log.debug("[PipelineAssembler] Using SetEqualityComparator (unordered)");
            return setEqualityComparator;
        }

        // Phase 7: For CLASS with STATEFUL_SEQUENCE, use JSON deep comparison
        // (observable outputs are arrays that need element-wise comparison)
        if ("CLASS".equalsIgnoreCase(ctx.getExecutionStrategy())) {
            log.debug("[PipelineAssembler] Using JsonDeepComparator for CLASS execution");
            return jsonDeepComparator;
        }

        // Ordered list output → JSON deep comparison
        String returnType = ctx.getReturnType();
        if (returnType != null && isListType(returnType)) {
            log.debug("[PipelineAssembler] Using JsonDeepComparator for list output");
            return jsonDeepComparator;
        }

        // Default: exact match (string + JSON fallback)
        return exactMatchComparator;
    }

    private List<OutputValidator> selectValidators(JudgingContext ctx) {
        List<OutputValidator> validators = new ArrayList<>();

        // Class 1: Structural safety — trait-driven (PRE_COMPARE)
        NodeType nodeType = ctx.getNodeType();
        if (nodeType == NodeType.LIST_NODE || nodeType == NodeType.TREE_NODE) {
            log.debug("[PipelineAssembler] Adding StructuralSafetyValidator for nodeType: {}", nodeType);
            validators.add(structuralSafetyValidator);
        }

        // Classes 2 & 4: Hint-driven validators
        List<String> hints = ctx.getValidationHints();
        if (hints != null) {
            if (hints.contains("EXPECT_LINEAR_FORM")) {
                log.debug("[PipelineAssembler] Adding LinkedListShapeValidator (EXPECT_LINEAR_FORM)");
                validators.add(linkedListShapeValidator);
            }
            if (hints.contains("SUDOKU_RULES")) {
                log.debug("[PipelineAssembler] Adding SudokuConstraintValidator (SUDOKU_RULES)");
                validators.add(sudokuConstraintValidator);
            }
            if (hints.contains("REQUIRE_DEEP_COPY")) {
                log.debug("[PipelineAssembler] Adding DeepCopyValidator (REQUIRE_DEEP_COPY)");
                validators.add(deepCopyValidator);
            }
        }

        // Phase 7: Design-class size mismatch guard (PRE_COMPARE)
        if ("CLASS".equalsIgnoreCase(ctx.getExecutionStrategy())) {
            log.debug("[PipelineAssembler] Adding DesignClassSizeValidator for CLASS execution");
            validators.add(designClassSizeValidator);
        }

        return validators;
    }

    // ---- Type detection helpers ----

    /**
     * Check if returnType represents a list/array type.
     * Matches: "List<Integer>", "List<List<Integer>>", "int[]", "String[]", etc.
     */
    private boolean isListType(String returnType) {
        if (returnType == null)
            return false;
        String lower = returnType.toLowerCase();
        return lower.startsWith("list") || lower.endsWith("[]") || lower.contains("array");
    }

    /**
     * Check if returnType represents a nested list (List<List<...>>).
     * Matches: "List<List<Integer>>", "List<List<String>>", etc.
     */
    private boolean isNestedListType(String returnType) {
        if (returnType == null)
            return false;
        String lower = returnType.toLowerCase();
        return lower.contains("list<list") || lower.contains("[][]");
    }
}
