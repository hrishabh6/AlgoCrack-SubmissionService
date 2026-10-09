package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

/**
 * Formal size/cardinality roles in JDK operation time templates (Batch 3).
 * Substituted at call sites — never authoritative as primary {@code n} alone.
 */
public enum JdkTemplateVariable {
    RECEIVER_SIZE("rcvSize"),
    RECEIVER_CARDINALITY("rcvCard"),
    ARG0_SIZE("arg0"),
    ARG1_SIZE("arg1"),
    ARG2_SIZE("arg2"),
    ARG3_SIZE("arg3"),
    ARG4_SIZE("arg4"),
    RANGE_LENGTH("rangeLen"),
    RESULT_SIZE("resultSize");

    private final String symbol;

    JdkTemplateVariable(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
