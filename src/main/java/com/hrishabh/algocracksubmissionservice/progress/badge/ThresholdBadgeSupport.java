package com.hrishabh.algocracksubmissionservice.progress.badge;

import java.util.ArrayList;
import java.util.List;

public final class ThresholdBadgeSupport {

    private ThresholdBadgeSupport() {
    }

    public static List<BadgeAwardCandidate> uniqueSolved(long uniqueSolved) {
        List<BadgeAwardCandidate> list = new ArrayList<>();
        list.add(threshold(BadgeCodes.FIRST_SOLVE, uniqueSolved, 1));
        list.add(threshold(BadgeCodes.SOLVED_10, uniqueSolved, 10));
        list.add(threshold(BadgeCodes.SOLVED_50, uniqueSolved, 50));
        list.add(threshold(BadgeCodes.SOLVED_100, uniqueSolved, 100));
        return list;
    }

    public static List<BadgeAwardCandidate> difficulty(long mediumSolved, long hardSolved) {
        List<BadgeAwardCandidate> list = new ArrayList<>();
        list.add(threshold(BadgeCodes.MEDIUM_10, mediumSolved, 10));
        list.add(threshold(BadgeCodes.MEDIUM_25, mediumSolved, 25));
        list.add(threshold(BadgeCodes.HARD_5, hardSolved, 5));
        list.add(threshold(BadgeCodes.HARD_10, hardSolved, 10));
        list.add(threshold(BadgeCodes.HARD_25, hardSolved, 25));
        return list;
    }

    public static List<BadgeAwardCandidate> potd(long totalPotdCompleted) {
        List<BadgeAwardCandidate> list = new ArrayList<>();
        list.add(threshold(BadgeCodes.POTD_FIRST, totalPotdCompleted, 1));
        list.add(threshold(BadgeCodes.POTD_7, totalPotdCompleted, 7));
        list.add(threshold(BadgeCodes.POTD_30, totalPotdCompleted, 30));
        list.add(threshold(BadgeCodes.POTD_100, totalPotdCompleted, 100));
        return list;
    }

    public static List<BadgeAwardCandidate> streak(int longestPotdStreak) {
        long streak = longestPotdStreak;
        return List.of(
                threshold(BadgeCodes.POTD_STREAK_7, streak, 7),
                threshold(BadgeCodes.POTD_STREAK_30, streak, 30));
    }

    public static BadgeAwardCandidate threshold(String code, long current, long target) {
        return new BadgeAwardCandidate(code, current >= target, Math.min(current, target), target);
    }
}
