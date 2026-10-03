package com.hrishabh.algocracksubmissionservice.progress.rank;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

public final class PotdStreakCalculator {

    private PotdStreakCalculator() {
    }

    public record StreakResult(int currentStreak, int longestStreak) {
    }

    public static StreakResult calculate(List<LocalDate> completionDatesUtc, LocalDate todayUtc) {
        if (completionDatesUtc == null || completionDatesUtc.isEmpty()) {
            return new StreakResult(0, 0);
        }
        TreeSet<LocalDate> dates = new TreeSet<>(completionDatesUtc);

        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            if (prev != null && d.equals(prev.plusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            longest = Math.max(longest, run);
            prev = d;
        }

        int current = 0;
        LocalDate cursor = todayUtc;
        while (dates.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        if (current == 0 && dates.contains(todayUtc.minusDays(1))) {
            cursor = todayUtc.minusDays(1);
            while (dates.contains(cursor)) {
                current++;
                cursor = cursor.minusDays(1);
            }
        }

        return new StreakResult(current, longest);
    }
}
