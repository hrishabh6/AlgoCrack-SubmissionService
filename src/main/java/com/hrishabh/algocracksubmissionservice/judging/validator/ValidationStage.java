package com.hrishabh.algocracksubmissionservice.judging.validator;

/**
 * Determines when a validator runs relative to the comparison phase.
 */
public enum ValidationStage {

    /**
     * Run BEFORE comparison.
     * Use for structural constraints that must hold regardless of equality
     * (e.g., cycle detection in linked list output).
     */
    PRE_COMPARE,

    /**
     * Run AFTER comparison passes (default).
     * Use for constraints verified after equality is confirmed
     * (e.g., Sudoku rules, flattened tree structure).
     */
    POST_COMPARE
}
