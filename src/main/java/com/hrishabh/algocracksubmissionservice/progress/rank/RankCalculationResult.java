package com.hrishabh.algocracksubmissionservice.progress.rank;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RankCalculationResult {
    long masteryScore;
    long potdScore;
    long breadthScore;
    long qualityScore;
    long contestScore;
    long totalRankScore;
    String rankTierCode;
    int currentPotdStreak;
    int longestPotdStreak;
    int breadthQualifiedTopics;
}
