package com.hrishabh.algocracksubmissionservice.judging.validator;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;

/**
 * Phase 4 of the Judging Pipeline: Output Validation (Optional).
 *
 * Verifies structural constraints beyond output equality.
 * Validators declare an execution stage to control whether they run
 * before or after comparison.
 */
public interface OutputValidator {

    /**
     * When should this validator run relative to comparison?
     * Default: POST_COMPARE (after comparison passes).
     */
    default ValidationStage getStage() {
        return ValidationStage.POST_COMPARE;
    }

    /**
     * Validate structural constraints on user output.
     *
     * @param userOutput   The extracted user output
     * @param oracleOutput The extracted oracle output (for reference)
     * @param context      Judging context with question metadata
     * @return ValidationResult with passed/failed status and details
     */
    ValidationResult validate(Object userOutput, Object oracleOutput, JudgingContext context);
}
