package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.StreakDto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Computes daily activity streaks from the set of days a user submitted on.
 * A streak stays "alive" through today if the last active day was yesterday.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public static StreakDto calculate(Collection<LocalDate> activeDays, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(activeDays);
        days.removeIf(d -> d.isAfter(today));

        if (days.isEmpty()) {
            return StreakDto.builder()
                    .currentStreak(0)
                    .longestStreak(0)
                    .activeToday(false)
                    .lastActiveDate(null)
                    .totalActiveDays(0)
                    .build();
        }

        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }

        LocalDate last = days.last();
        boolean activeToday = last.equals(today);
        int current = 0;
        if (activeToday || last.equals(today.minusDays(1))) {
            LocalDate cursor = last;
            while (days.contains(cursor)) {
                current++;
                cursor = cursor.minusDays(1);
            }
        }

        return StreakDto.builder()
                .currentStreak(current)
                .longestStreak(longest)
                .activeToday(activeToday)
                .lastActiveDate(last.toString())
                .totalActiveDays(days.size())
                .build();
    }

    /** Parses DATE() results, which drivers may return as java.sql.Date, LocalDate or String. */
    public static List<LocalDate> toLocalDates(List<Object> rawDates) {
        return rawDates.stream()
                .filter(java.util.Objects::nonNull)
                .map(raw -> raw instanceof java.sql.Date sqlDate
                        ? sqlDate.toLocalDate()
                        : raw instanceof LocalDate localDate ? localDate : LocalDate.parse(raw.toString()))
                .toList();
    }
}
