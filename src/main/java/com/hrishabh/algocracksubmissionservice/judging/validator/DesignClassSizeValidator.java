package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PRE_COMPARE validator for design-class problems.
 *
 * Validates that user and oracle operation result arrays have the same size.
 * If sizes differ, the user's class didn't process all operations correctly
 * (e.g., threw an exception midway, or returned early).
 *
 * This check must happen BEFORE extraction strips the array down to
 * only the invariant elements, so we operate on the raw extracted data
 * (which is still the full operation array at PRE_COMPARE stage).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DesignClassSizeValidator implements OutputValidator {

    private final ObjectMapper objectMapper;

    @Override
    public ValidationStage getStage() {
        return ValidationStage.PRE_COMPARE;
    }

    @Override
    public ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context) {
        // Only applies to CLASS execution
        if (!"CLASS".equalsIgnoreCase(context.getExecutionStrategy())) {
            return ValidationResult.passed();
        }

        int userSize = getArraySize(userOutput);
        int oracleSize = getArraySize(oracleOutput);

        if (userSize < 0 || oracleSize < 0) {
            // Not arrays — skip this validation
            return ValidationResult.passed();
        }

        if (userSize != oracleSize) {
            log.info("[DesignClassSizeValidator] Operation count mismatch: user={}, oracle={}",
                    userSize, oracleSize);
            return ValidationResult.failed(
                    "Operation count mismatch: your class produced " + userSize
                            + " results, expected " + oracleSize);
        }

        log.debug("[DesignClassSizeValidator] Operation counts match: {}", userSize);
        return ValidationResult.passed();
    }

    private int getArraySize(Object obj) {
        if (obj instanceof JsonNode) {
            JsonNode node = (JsonNode) obj;
            return node.isArray() ? node.size() : -1;
        }
        if (obj instanceof String) {
            try {
                JsonNode node = objectMapper.readTree((String) obj);
                return node.isArray() ? node.size() : -1;
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }
}
