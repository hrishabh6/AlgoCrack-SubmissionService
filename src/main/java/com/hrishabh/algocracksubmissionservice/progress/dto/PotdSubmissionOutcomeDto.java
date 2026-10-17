package com.hrishabh.algocracksubmissionservice.progress.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Daily-challenge outcome for a completed submission. Credit is server-side only;
 * navigation/query params never influence {@link #newlyCompleted}.
 */
@Value
@Builder
public class PotdSubmissionOutcomeDto {
    boolean dailyChallengeCompleted;
    boolean newlyCompleted;
    String challengeDate;
    int currentPotdStreak;
}
