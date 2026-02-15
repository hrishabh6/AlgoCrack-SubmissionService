package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Class 4: Constraint/Rule Validator — SudokuConstraintValidator (POST_COMPARE)
 *
 * Validates Sudoku domain rules:
 * - Every row has digits 1-9 with no repeats
 * - Every column has digits 1-9 with no repeats
 * - Every 3x3 box has digits 1-9 with no repeats
 *
 * This is necessary because multiple valid solutions exist — oracle comparison
 * alone is insufficient for correctness.
 *
 * Trigger: validationHints contains "SUDOKU_RULES"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SudokuConstraintValidator implements OutputValidator {

    private final ObjectMapper objectMapper;

    @Override
    public ValidationStage getStage() {
        return ValidationStage.POST_COMPARE;
    }

    @Override
    public ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context) {
        JsonNode userJson = toJsonNode(userOutput);
        if (userJson == null || !userJson.isArray() || userJson.size() != 9) {
            return ValidationResult.failed("Sudoku output must be a 9x9 grid (got invalid format)");
        }

        // Parse into int grid
        int[][] grid = new int[9][9];
        for (int row = 0; row < 9; row++) {
            JsonNode rowNode = userJson.get(row);
            if (rowNode == null || !rowNode.isArray() || rowNode.size() != 9) {
                return ValidationResult.failed("Sudoku row " + row + " must have 9 elements");
            }
            for (int col = 0; col < 9; col++) {
                grid[row][col] = rowNode.get(col).asInt();
                if (grid[row][col] < 1 || grid[row][col] > 9) {
                    return ValidationResult.failed(
                            "Invalid digit at (" + row + "," + col + "): " + grid[row][col]);
                }
            }
        }

        // Validate rows
        for (int row = 0; row < 9; row++) {
            Set<Integer> seen = new HashSet<>();
            for (int col = 0; col < 9; col++) {
                if (!seen.add(grid[row][col])) {
                    return ValidationResult.failed(
                            "Duplicate " + grid[row][col] + " in row " + row);
                }
            }
        }

        // Validate columns
        for (int col = 0; col < 9; col++) {
            Set<Integer> seen = new HashSet<>();
            for (int row = 0; row < 9; row++) {
                if (!seen.add(grid[row][col])) {
                    return ValidationResult.failed(
                            "Duplicate " + grid[row][col] + " in column " + col);
                }
            }
        }

        // Validate 3x3 boxes
        for (int boxRow = 0; boxRow < 3; boxRow++) {
            for (int boxCol = 0; boxCol < 3; boxCol++) {
                Set<Integer> seen = new HashSet<>();
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        int val = grid[boxRow * 3 + r][boxCol * 3 + c];
                        if (!seen.add(val)) {
                            return ValidationResult.failed(
                                    "Duplicate " + val + " in box (" + boxRow + "," + boxCol + ")");
                        }
                    }
                }
            }
        }

        log.debug("[SudokuConstraintValidator] Sudoku output passes all constraint checks");
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
