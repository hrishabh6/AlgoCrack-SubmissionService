package com.hrishabh.algocracksubmissionservice.complexity.inference;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V1 deterministic normalized growth comparison (median timings only).
 */
@Component
public class DynamicGrowthInferenceEngine {

    private static final GrowthCandidateFamily[] CANDIDATES = {
            GrowthCandidateFamily.O1,
            GrowthCandidateFamily.O_LOG_N,
            GrowthCandidateFamily.O_N,
            GrowthCandidateFamily.O_N_LOG_N,
            GrowthCandidateFamily.O_N2,
            GrowthCandidateFamily.O_N3
    };

    private final MeasurementPolicy policy;

    public DynamicGrowthInferenceEngine(MeasurementPolicy policy) {
        this.policy = policy;
    }

    public DynamicGrowthInferenceResult infer(List<TimingObservation> observations) {
        List<String> evidence = new ArrayList<>();
        Optional<String> primaryDim = resolvePrimaryDimension(observations);
        if (primaryDim.isEmpty()) {
            return DynamicGrowthInferenceResult.multiDimensional(evidence);
        }
        String dimension = primaryDim.get();
        List<TimingObservation> usable = observations.stream()
                .filter(TimingObservation::usableForInference)
                .filter(o -> dimension.equals(o.primaryDimension()))
                .sorted(Comparator.comparingLong(TimingObservation::primarySize))
                .toList();

        if (usable.size() < policy.getMinUsableSizePoints()) {
            evidence.add("BENCHMARK_INSUFFICIENT_POINTS");
            return DynamicGrowthInferenceResult.inconclusive("BENCHMARK_INSUFFICIENT_POINTS", evidence);
        }

        List<TimingObservation> filtered = new ArrayList<>();
        for (TimingObservation point : usable) {
            if (point.medianElapsedNs() < policy.getMinUsefulDurationNs()) {
                evidence.add("JIT_NOISE:" + point.caseId());
                continue;
            }
            if (point.medianElapsedNs() > 0
                    && (double) point.madElapsedNs() / (double) point.medianElapsedNs() > policy.getMaxRelativeMad()) {
                evidence.add("HIGH_MAD:" + point.caseId());
                continue;
            }
            filtered.add(point);
        }
        if (filtered.size() < policy.getMinUsableSizePoints()) {
            evidence.add("BENCHMARK_NOISY");
            return DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", evidence);
        }

        Map<GrowthCandidateFamily, Double> spreads = new LinkedHashMap<>();
        for (GrowthCandidateFamily candidate : CANDIDATES) {
            spreads.put(candidate, normalizedSpread(filtered, candidate));
        }
        double bestScore = spreads.values().stream().mapToDouble(Double::doubleValue).min().orElse(Double.MAX_VALUE);
        if (!Double.isFinite(bestScore) || bestScore > policy.getMaxNormalizedSpread()) {
            evidence.add("BENCHMARK_NOISY");
            return DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", evidence);
        }

        double tieBand = bestScore * (1.0 + policy.getMinWinnerMargin());
        GrowthCandidateFamily chosen = GrowthCandidateFamily.INCONCLUSIVE;
        for (GrowthCandidateFamily candidate : CANDIDATES) {
            double spread = spreads.get(candidate);
            if (spread <= Math.min(tieBand, policy.getMaxNormalizedSpread())) {
                if (chosen == GrowthCandidateFamily.INCONCLUSIVE
                        || candidate.simplicityRank() < chosen.simplicityRank()) {
                    chosen = candidate;
                }
            }
        }
        if (chosen == GrowthCandidateFamily.INCONCLUSIVE) {
            evidence.add("AMBIGUOUS_FAMILY");
            return DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", evidence);
        }

        GrowthCandidateFamily nextComplex = nextMoreComplex(chosen);
        if (nextComplex != null) {
            double complexSpread = spreads.get(nextComplex);
            if (complexSpread <= Math.min(tieBand, policy.getMaxNormalizedSpread())
                    && (complexSpread - spreads.get(chosen)) / Math.max(spreads.get(chosen), 1e-9)
                            < policy.getMinWinnerMargin()) {
                evidence.add("AMBIGUOUS_FAMILY");
                return DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", evidence);
            }
        }
        evidence.add("FIT:" + chosen.name());
        return new DynamicGrowthInferenceResult(chosen, "DYNAMIC_FIT", evidence, false);
    }

    private static GrowthCandidateFamily nextMoreComplex(GrowthCandidateFamily family) {
        int rank = family.simplicityRank();
        for (GrowthCandidateFamily candidate : CANDIDATES) {
            if (candidate.simplicityRank() == rank + 1) {
                return candidate;
            }
        }
        return null;
    }

    public static Optional<String> resolvePrimaryDimension(List<TimingObservation> observations) {
        List<TimingObservation> usable = observations.stream().filter(TimingObservation::usableForInference).toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Long> varying = new LinkedHashMap<>();
        for (String key : usable.getFirst().sizeVector().keySet()) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (TimingObservation o : usable) {
                Integer v = o.sizeVector().get(key);
                if (v == null) {
                    continue;
                }
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (max > min) {
                varying.put(key, max - min);
            }
        }
        if (varying.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(varying.keySet().iterator().next());
    }

    private static double normalizedSpread(List<TimingObservation> points, GrowthCandidateFamily family) {
        List<Double> ratios = new ArrayList<>();
        for (TimingObservation point : points) {
            double scale = family.scaleAt(point.primarySize());
            if (scale <= 0 || !Double.isFinite(scale)) {
                return Double.MAX_VALUE;
            }
            ratios.add(point.medianElapsedNs() / scale);
        }
        double median = median(ratios);
        if (median <= 0) {
            return Double.MAX_VALUE;
        }
        double min = ratios.stream().mapToDouble(Double::doubleValue).min().orElse(median);
        double max = ratios.stream().mapToDouble(Double::doubleValue).max().orElse(median);
        return (max - min) / median;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }
}
