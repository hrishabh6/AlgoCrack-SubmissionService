package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.StreakDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    @Test
    void noActivityMeansNoStreak() {
        StreakDto streak = StreakCalculator.calculate(List.of(), TODAY);

        assertThat(streak.getCurrentStreak()).isZero();
        assertThat(streak.getLongestStreak()).isZero();
        assertThat(streak.isActiveToday()).isFalse();
        assertThat(streak.getLastActiveDate()).isNull();
        assertThat(streak.getTotalActiveDays()).isZero();
    }

    @Test
    void countsConsecutiveDaysEndingToday() {
        StreakDto streak = StreakCalculator.calculate(
                List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(5)), TODAY);

        assertThat(streak.getCurrentStreak()).isEqualTo(3);
        assertThat(streak.getLongestStreak()).isEqualTo(3);
        assertThat(streak.isActiveToday()).isTrue();
        assertThat(streak.getTotalActiveDays()).isEqualTo(4);
    }

    @Test
    void streakStaysAliveUntilEndOfTodayWhenLastActiveYesterday() {
        StreakDto streak = StreakCalculator.calculate(
                List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);

        assertThat(streak.getCurrentStreak()).isEqualTo(2);
        assertThat(streak.isActiveToday()).isFalse();
        assertThat(streak.getLastActiveDate()).isEqualTo(TODAY.minusDays(1).toString());
    }

    @Test
    void streakBreaksAfterAMissedDay() {
        StreakDto streak = StreakCalculator.calculate(
                List.of(TODAY.minusDays(2), TODAY.minusDays(3), TODAY.minusDays(4), TODAY.minusDays(5)), TODAY);

        assertThat(streak.getCurrentStreak()).isZero();
        assertThat(streak.getLongestStreak()).isEqualTo(4);
    }

    @Test
    void ignoresDuplicatesAndFutureDays() {
        StreakDto streak = StreakCalculator.calculate(
                List.of(TODAY, TODAY, TODAY.plusDays(1)), TODAY);

        assertThat(streak.getCurrentStreak()).isEqualTo(1);
        assertThat(streak.getTotalActiveDays()).isEqualTo(1);
    }

    @Test
    void parsesDriverDateTypes() {
        List<LocalDate> parsed = StreakCalculator.toLocalDates(List.of(
                java.sql.Date.valueOf("2026-10-01"), LocalDate.of(2026, 10, 2), "2026-10-03"));

        assertThat(parsed).containsExactly(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));
    }
}
