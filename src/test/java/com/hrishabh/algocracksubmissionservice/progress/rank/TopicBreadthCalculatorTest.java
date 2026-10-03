package com.hrishabh.algocracksubmissionservice.progress.rank;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicBreadthCalculatorTest {

    @Test
    void multiTagSolve_totalsOneCredit() {
        Map<Long, BigDecimal> credit = TopicBreadthCalculator.creditForSolve(List.of(1L, 2L));
        BigDecimal sum = credit.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.doubleValue()).isEqualTo(1.0);
    }
}
