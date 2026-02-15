package com.hrishabh.algocracksubmissionservice.judging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of judging a single testcase through the pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgingResult {

    /**
     * Whether the testcase passed.
     */
    private boolean passed;

    /**
     * Normalized user output for frontend display.
     */
    private String normalizedUserOutput;

    /**
     * Normalized oracle output for frontend display.
     */
    private String normalizedOracleOutput;

    /**
     * Reason for failure (optional, null when passed).
     */
    private String failureReason;

    /**
     * Whether this is a judge/system error (oracle failure, internal error).
     */
    private boolean judgeError;

    public static JudgingResult passed(String userDisplay, String oracleDisplay) {
        return JudgingResult.builder()
                .passed(true)
                .normalizedUserOutput(userDisplay)
                .normalizedOracleOutput(oracleDisplay)
                .build();
    }

    public static JudgingResult failed(String reason, String userDisplay, String oracleDisplay) {
        return JudgingResult.builder()
                .passed(false)
                .failureReason(reason)
                .normalizedUserOutput(userDisplay)
                .normalizedOracleOutput(oracleDisplay)
                .build();
    }

    /**
     * Oracle or system error — user should not be penalized.
     */
    public static JudgingResult judgeError(String reason) {
        return JudgingResult.builder()
                .passed(false)
                .judgeError(true)
                .failureReason(reason)
                .build();
    }
}
