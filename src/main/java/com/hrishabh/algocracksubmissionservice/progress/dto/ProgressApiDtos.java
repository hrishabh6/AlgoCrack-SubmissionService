package com.hrishabh.algocracksubmissionservice.progress.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

public final class ProgressApiDtos {

    private ProgressApiDtos() {
    }

    @Value
    @Builder
    public static class ScoreBreakdownDto {
        long mastery;
        long potd;
        long breadth;
        long quality;
        long contest;
        long total;
        String qualityStatus;
        String contestStatus;
    }

    @Value
    @Builder
    public static class TierDto {
        String code;
        String name;
        long currentMinimum;
        String nextTierCode;
        Long nextThreshold;
        Long pointsToNext;
        int progressPercent;
    }

    @Value
    @Builder
    public static class ProgressMetricsDto {
        long uniqueSolved;
        long easySolved;
        long mediumSolved;
        long hardSolved;
        long totalPotdCompleted;
        int currentPotdStreak;
        int longestPotdStreak;
        int breadthQualifiedTopics;
    }

    @Value
    @Builder
    public static class ProgressResponse {
        String userId;
        String algorithmVersion;
        boolean staleVersion;
        ScoreBreakdownDto score;
        TierDto tier;
        ProgressMetricsDto metrics;
        Long leaderboardPosition;
        LocalDateTime calculatedAt;
    }

    @Value
    @Builder
    public static class BadgeDefinitionDto {
        String code;
        String name;
        String description;
        String category;
        String iconKey;
        String displayTier;
    }

    @Value
    @Builder
    public static class EarnedBadgeDto {
        String code;
        String name;
        String description;
        String category;
        String iconKey;
        LocalDateTime earnedAt;
    }

    @Value
    @Builder
    public static class LockedBadgeDto {
        String code;
        String name;
        String description;
        String category;
        String iconKey;
        Long progressCurrent;
        Long progressTarget;
    }

    @Value
    @Builder
    public static class UserBadgesResponse {
        String userId;
        List<EarnedBadgeDto> earned;
        List<LockedBadgeDto> locked;
    }

    @Value
    @Builder
    public static class LeaderboardRowDto {
        long position;
        String userId;
        long totalScore;
        String tierCode;
        long uniqueSolved;
        long hardSolved;
        long totalPotdCompleted;
        boolean currentUser;
    }

    @Value
    @Builder
    public static class LeaderboardResponse {
        List<LeaderboardRowDto> content;
        int page;
        int size;
        long totalElements;
        int totalPages;
    }

    @Value
    @Builder
    public static class LeaderboardPositionResponse {
        String userId;
        Long position;
        long totalScore;
        String tierCode;
    }

    @Value
    @Builder
    public static class RecalculateResponse {
        String userId;
        long totalRankScore;
        String rankTierCode;
    }

    @Value
    @Builder
    public static class RecalculateAllResponse {
        int usersProcessed;
    }
}
