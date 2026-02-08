package com.hrishabh.algocracksubmissionservice.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal representation of a testcase input.
 * CXE-agnostic - used throughout the Submission Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseInput {

    /**
     * JSON-formatted input data.
     * Example: {"nums": [2,7,11,15], "target": 9}
     */
    private String input;

    /**
     * Optional index for ordering/tracking.
     */
    private Integer index;

    /**
     * Whether this is a user-provided custom testcase.
     * False for testcases from the database.
     */
    private boolean isCustom;
}

