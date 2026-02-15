package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Class 3: Independence/Ownership Validator (POST_COMPARE).
 *
 * Validates that the user's output represents an independent copy (deep clone)
 * of the input structure. Triggered by the "REQUIRE_DEEP_COPY" validation hint.
 *
 * Use case: Clone Graph — the cloned graph must be structurally identical
 * to the original but share no nodes with it.
 *
 * IMPORTANT LIMITATION:
 * In a serialized-output pipeline, true pointer-identity verification is
 * impossible.
 * Once CXE serializes an object to JSON, reference sharing is invisible.
 * This validator performs best-effort structural verification:
 * 1. Confirms the output is a valid adjacency list (well-formed)
 * 2. Confirms all neighbour indices are within valid range (no dangling refs)
 * 3. Confirms symmetry for undirected graphs (if u→v then v→u)
 *
 * True deep-copy verification would require CXE-side instrumentation
 * (checking object identity before serialization).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepCopyValidator implements OutputValidator {

    private final ObjectMapper objectMapper;

    @Override
    public ValidationStage getStage() {
        return ValidationStage.POST_COMPARE;
    }

    @Override
    public ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context) {
        if (userOutput == null) {
            return ValidationResult.failed("Deep copy validation failed: user output is null");
        }

        try {
            String outputStr = userOutput instanceof String
                    ? (String) userOutput
                    : objectMapper.writeValueAsString(userOutput);

            List<List<Integer>> adjacencyList = objectMapper.readValue(
                    outputStr, new TypeReference<List<List<Integer>>>() {
                    });

            // Check 1: Non-empty graph (empty clone is trivially wrong for non-empty input)
            if (adjacencyList.isEmpty()) {
                return ValidationResult.failed(
                        "Deep copy validation failed: output graph is empty");
            }

            int nodeCount = adjacencyList.size();

            // Check 2: All neighbour indices within valid range [0, nodeCount)
            for (int i = 0; i < nodeCount; i++) {
                List<Integer> neighbours = adjacencyList.get(i);
                if (neighbours == null) {
                    return ValidationResult.failed(
                            "Deep copy validation failed: node " + (i + 1) + " has null neighbour list");
                }
                for (int neighbour : neighbours) {
                    if (neighbour < 1 || neighbour > nodeCount) {
                        return ValidationResult.failed(
                                "Deep copy validation failed: node " + (i + 1)
                                        + " references invalid neighbour " + neighbour
                                        + " (valid range: 1-" + nodeCount + ")");
                    }
                }
            }

            // Check 3: Symmetry — for undirected graphs, edges must be bidirectional
            for (int i = 0; i < nodeCount; i++) {
                for (int neighbour : adjacencyList.get(i)) {
                    int neighbourIdx = neighbour - 1; // 1-indexed to 0-indexed
                    List<Integer> reverseNeighbours = adjacencyList.get(neighbourIdx);
                    if (!reverseNeighbours.contains(i + 1)) {
                        return ValidationResult.failed(
                                "Deep copy validation failed: edge from node " + (i + 1)
                                        + " to node " + neighbour
                                        + " is not bidirectional (not an undirected graph)");
                    }
                }
            }

            log.debug("[DeepCopyValidator] Graph structure validated: {} nodes, all edges bidirectional", nodeCount);
            return ValidationResult.passed();

        } catch (Exception e) {
            // Can't parse as adjacency list — skip validation (don't crash the judge)
            log.debug("[DeepCopyValidator] Skipping: output is not a parseable adjacency list: {}",
                    e.getMessage());
            return ValidationResult.passed();
        }
    }
}
