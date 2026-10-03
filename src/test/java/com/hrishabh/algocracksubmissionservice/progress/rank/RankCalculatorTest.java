package com.hrishabh.algocracksubmissionservice.progress.rank;

import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RankCalculatorTest {

    private RankCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RankCalculator(new RankProperties());
    }

    @Test
    void emptyInput_isZeroNovice() {
        RankCalculationResult result = calculator.calculate(
                RankCalculationInput.builder()
                        .easySolved(0)
                        .mediumSolved(0)
                        .hardSolved(0)
                        .easyPotdCompleted(0)
                        .mediumPotdCompleted(0)
                        .hardPotdCompleted(0)
                        .topicCredits(Map.of())
                        .potdCompletionDatesUtc(List.of())
                        .build(),
                LocalDate.of(2026, 10, 3));
        assertThat(result.getTotalRankScore()).isZero();
        assertThat(result.getRankTierCode()).isEqualTo("NOVICE");
    }

    @Test
    void masteryWeights_matchPlan() {
        RankCalculationResult result = calculator.calculate(
                RankCalculationInput.builder()
                        .easySolved(1)
                        .mediumSolved(1)
                        .hardSolved(1)
                        .topicCredits(Map.of())
                        .potdCompletionDatesUtc(List.of())
                        .build(),
                LocalDate.of(2026, 10, 3));
        assertThat(result.getMasteryScore()).isEqualTo(10 + 25 + 45);
        assertThat(result.getTotalRankScore()).isEqualTo(80);
    }

    @Test
    void topicBreadth_capsAt300() {
        Map<Long, BigDecimal> heavy = Map.of(1L, BigDecimal.valueOf(6), 2L, BigDecimal.valueOf(6));
        RankCalculationResult result = calculator.calculate(
                RankCalculationInput.builder()
                        .hardSolved(2)
                        .topicCredits(heavy)
                        .potdCompletionDatesUtc(List.of())
                        .build(),
                LocalDate.of(2026, 10, 3));
        assertThat(result.getBreadthScore()).isLessThanOrEqualTo(300);
    }
}
