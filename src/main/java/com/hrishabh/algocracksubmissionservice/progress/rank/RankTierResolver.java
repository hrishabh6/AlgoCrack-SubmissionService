package com.hrishabh.algocracksubmissionservice.progress.rank;

import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;

import java.util.Comparator;
import java.util.List;

public final class RankTierResolver {

    private RankTierResolver() {
    }

    public record TierView(
            String code,
            String name,
            long currentMinimum,
            String nextTierCode,
            Long nextThreshold,
            long pointsIntoTier,
            Long pointsToNext,
            int progressPercent) {
    }

    public static void validateTierOrder(List<RankProperties.TierThreshold> tiers) {
        List<RankProperties.TierThreshold> sorted = tiers.stream()
                .sorted(Comparator.comparingLong(RankProperties.TierThreshold::getMinimumScore))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getMinimumScore() <= sorted.get(i - 1).getMinimumScore()) {
                throw new IllegalStateException("Tier thresholds must be strictly increasing");
            }
        }
    }

    public static String resolveTierCode(long totalScore, List<RankProperties.TierThreshold> tiers) {
        List<RankProperties.TierThreshold> sorted = tiers.stream()
                .sorted(Comparator.comparingLong(RankProperties.TierThreshold::getMinimumScore))
                .toList();
        RankProperties.TierThreshold current = sorted.getFirst();
        for (RankProperties.TierThreshold tier : sorted) {
            if (totalScore >= tier.getMinimumScore()) {
                current = tier;
            }
        }
        return current.getCode();
    }

    public static TierView describe(long totalScore, List<RankProperties.TierThreshold> tiers) {
        List<RankProperties.TierThreshold> sorted = tiers.stream()
                .sorted(Comparator.comparingLong(RankProperties.TierThreshold::getMinimumScore))
                .toList();
        RankProperties.TierThreshold current = sorted.getFirst();
        RankProperties.TierThreshold next = null;
        for (int i = 0; i < sorted.size(); i++) {
            if (totalScore >= sorted.get(i).getMinimumScore()) {
                current = sorted.get(i);
                next = i + 1 < sorted.size() ? sorted.get(i + 1) : null;
            }
        }
        long pointsInto = totalScore - current.getMinimumScore();
        Long pointsToNext = next != null ? next.getMinimumScore() - totalScore : null;
        int percent = 100;
        if (next != null) {
            long span = next.getMinimumScore() - current.getMinimumScore();
            percent = span <= 0 ? 100 : (int) Math.min(100, Math.max(0, (pointsInto * 100) / span));
        }
        return new TierView(
                current.getCode(),
                current.getName(),
                current.getMinimumScore(),
                next != null ? next.getCode() : null,
                next != null ? next.getMinimumScore() : null,
                pointsInto,
                pointsToNext,
                percent);
    }
}
