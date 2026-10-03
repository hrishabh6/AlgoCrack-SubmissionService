package com.hrishabh.algocracksubmissionservice.progress.rank;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class RankCalculationInput {
    long easySolved;
    long mediumSolved;
    long hardSolved;
    long easyPotdCompleted;
    long mediumPotdCompleted;
    long hardPotdCompleted;
    /** tagId -> normalized credit */
    Map<Long, BigDecimal> topicCredits;
    List<LocalDate> potdCompletionDatesUtc;
}
