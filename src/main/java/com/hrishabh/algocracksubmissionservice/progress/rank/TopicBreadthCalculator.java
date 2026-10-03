package com.hrishabh.algocracksubmissionservice.progress.rank;

import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TopicBreadthCalculator {

    private TopicBreadthCalculator() {
    }

    /** Distributes exactly 1.0 credit across distinct tag ids for one solve. */
    public static Map<Long, BigDecimal> creditForSolve(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = tagIds.stream().distinct().toList();
        BigDecimal share = BigDecimal.ONE.divide(BigDecimal.valueOf(distinct.size()), 4, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Long id : distinct) {
            map.put(id, share);
        }
        return map;
    }

    public static Map<Long, BigDecimal> mergeCredits(Collection<Map<Long, BigDecimal>> perSolveCredits) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Map<Long, BigDecimal> solve : perSolveCredits) {
            for (Map.Entry<Long, BigDecimal> e : solve.entrySet()) {
                totals.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }
        return totals;
    }

    public static long breadthScore(Map<Long, BigDecimal> topicCredits, RankProperties properties) {
        RankProperties.BreadthThresholds t = properties.getBreadth();
        long sum = 0;
        for (BigDecimal credit : topicCredits.values()) {
            double c = credit.doubleValue();
            if (c >= t.getSpecialistCredit()) {
                sum += t.getSpecialistPoints();
            } else if (c >= t.getPractitionerCredit()) {
                sum += t.getPractitionerPoints();
            } else if (c >= t.getExplorerCredit()) {
                sum += t.getExplorerPoints();
            }
        }
        return Math.min(sum, properties.getBreadthCap());
    }

    public static int countQualifiedTopics(Map<Long, BigDecimal> topicCredits, double minimumCredit) {
        int count = 0;
        for (BigDecimal credit : topicCredits.values()) {
            if (credit.doubleValue() >= minimumCredit) {
                count++;
            }
        }
        return count;
    }
}
