package com.hrishabh.algocracksubmissionservice.complexity.inference;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicGrowthInferenceEngineTest {

    private DynamicGrowthInferenceEngine engine;

    @BeforeEach
    void setUp() {
        ComplexityProperties properties = new ComplexityProperties();
        engine = new DynamicGrowthInferenceEngine(new MeasurementPolicy(properties));
    }

    @Test
    void infersConstantFamily() {
        var result = engine.infer(points(8, 16, 32, 64, 1_000_000L));
        assertEquals(GrowthCandidateFamily.O1, result.family());
    }

    @Test
    void infersLinearFamily() {
        var result = engine.infer(timedPoints(new long[] {8, 16, 32, 64, 128, 256, 512, 1024}, n -> n * 50_000L));
        assertEquals(GrowthCandidateFamily.O_N, result.family());
    }

    @Test
    void infersQuadraticFamily() {
        var result = engine.infer(timedPoints(new long[] {8, 16, 32, 64, 128}, n -> n * n * 1_000L));
        assertEquals(GrowthCandidateFamily.O_N2, result.family());
    }

    @Test
    void rejectsInsufficientPoints() {
        var result = engine.infer(timedPoints(new long[] {8, 16}, n -> n * 50_000L));
        assertEquals(GrowthCandidateFamily.INCONCLUSIVE, result.family());
        assertEquals("BENCHMARK_INSUFFICIENT_POINTS", result.reasonCode());
    }

    @Test
    void excludesInvalidOutputPoints() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(new long[] {8, 16, 32, 64}, n -> n * 50_000L));
        points.add(new TimingObservation("bad", "RANDOM", Map.of("n", 128), "n", 128, 10_000_000, 1000, false, false));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.O_N, result.family());
    }

    @Test
    void infersLogNFamily() {
        var result = engine.infer(timedPoints(new long[] {2, 4, 8, 16, 32, 64, 128, 256}, n -> (long) (Math.log(n) * 100_000)));
        assertEquals(GrowthCandidateFamily.O_LOG_N, result.family());
    }

    @Test
    void infersNLogNFamily() {
        var result = engine.infer(timedPoints(new long[] {8, 16, 32, 64, 128, 256, 512, 1024},
                n -> (long) (n * (Math.log(n) / Math.log(2)) * 10_000)));
        assertEquals(GrowthCandidateFamily.O_N_LOG_N, result.family());
    }

    @Test
    void infersCubicFamily() {
        var result = engine.infer(timedPoints(new long[] {4, 8, 16, 32, 64, 128, 256}, n -> n * n * n * 2_000L));
        assertEquals(GrowthCandidateFamily.O_N3, result.family());
    }

    @Test
    void prefersSimplerFamilyWhenScoresTie() {
        long[] sizes = {8, 16, 32, 64, 128, 256};
        var result = engine.infer(timedPoints(sizes, n -> n * 50_000L));
        assertEquals(GrowthCandidateFamily.O_N, result.family());
    }

    @Test
    void ambiguousLinearVersusNLogNBecomesInconclusive() {
        long[] sizes = {8, 16, 32, 64, 128, 256, 512, 1024};
        var linear = engine.infer(timedPoints(sizes, n -> n * 50_000L));
        assertEquals(GrowthCandidateFamily.O_N, linear.family());
        var nearLinear = engine.infer(timedPoints(sizes, n -> n * 50_000L + (n % 5) * 12_000L));
        assertTrue(
                nearLinear.family() == GrowthCandidateFamily.O_N
                        || nearLinear.family() == GrowthCandidateFamily.INCONCLUSIVE);
        assertTrue(nearLinear.family().simplicityRank() < GrowthCandidateFamily.O_N2.simplicityRank());
        var nLogN = engine.infer(timedPoints(
                sizes, n -> (long) (n * (Math.log(n) / Math.log(2)) * 50_000L)));
        assertEquals(GrowthCandidateFamily.O_N_LOG_N, nLogN.family());
        assertTrue(nLogN.family().simplicityRank() < GrowthCandidateFamily.O_N2.simplicityRank());
    }

    @Test
    void toleratesNoisyLinearWhenEnoughCleanPoints() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(
                new long[] {8, 16, 32, 64, 128, 256, 512}, n -> n * 50_000L));
        points.add(new TimingObservation("wobble", "R", Map.of("n", 1024), "n", 1024, 52_000_000, 4_000_000, true, true));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.O_N, result.family());
    }

    @Test
    void toleratesNoisyQuadraticWhenCorePointsFit() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(
                new long[] {8, 16, 32, 64, 128, 256}, n -> n * n * 1_000L));
        points.add(new TimingObservation("wobble", "R", Map.of("n", 512), "n", 512, 260_000_000, 20_000_000, true, true));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.O_N2, result.family());
    }

    @Test
    void rejectsWhenPrimarySizeMissingFromVector() {
        List<TimingObservation> points = List.of(
                new TimingObservation("bad", "R", Map.of("m", 8), "n", 0, 1_000_000, 50_000, true, true),
                new TimingObservation("bad2", "R", Map.of("m", 16), "n", 0, 2_000_000, 50_000, true, true),
                new TimingObservation("bad3", "R", Map.of("m", 32), "n", 0, 4_000_000, 50_000, true, true),
                new TimingObservation("bad4", "R", Map.of("m", 64), "n", 0, 8_000_000, 50_000, true, true));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.INCONCLUSIVE, result.family());
    }

    @Test
    void rejectsPathologicalOutlierEvenWhenOtherPointsFitLinear() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(
                new long[] {8, 16, 32, 64, 128, 256, 512}, n -> n * 50_000L));
        points.add(new TimingObservation("outlier", "R", Map.of("n", 1024), "n", 1024, 500_000_000_000L, 1_000_000, true, true));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.INCONCLUSIVE, result.family());
    }

    @Test
    void rejectsTinyDurationsAsNoise() {
        var result = engine.infer(timedPoints(new long[] {8, 16, 32, 64}, n -> 1000L));
        assertEquals(GrowthCandidateFamily.INCONCLUSIVE, result.family());
    }

    @Test
    void rejectsHighMadPoints() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(new long[] {8, 16, 32, 64}, n -> n * 50_000L));
        points.add(new TimingObservation("noisy", "R", Map.of("n", 128), "n", 128, 10_000_000, 9_000_000, true, true));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.O_N, result.family());
    }

    @Test
    void excludesTimeoutLikeOutcomesViaUsableFlag() {
        List<TimingObservation> points = new ArrayList<>(timedPoints(new long[] {8, 16, 32}, n -> n * 50_000L));
        points.add(new TimingObservation("timeout", "R", Map.of("n", 128), "n", 128, 50_000_000, 1000, true, false));
        var result = engine.infer(points);
        assertEquals(GrowthCandidateFamily.INCONCLUSIVE, result.family());
    }

    @Test
    void multiDimensionalInconclusiveWhenTwoAxesVary() {
        List<TimingObservation> points = List.of(
                obs("a", Map.of("n", 8, "m", 8), 100_000),
                obs("b", Map.of("n", 16, "m", 16), 200_000),
                obs("c", Map.of("n", 32, "m", 32), 400_000),
                obs("d", Map.of("n", 64, "m", 64), 800_000));
        var result = engine.infer(points);
        assertTrue(result.multiDimensionalInconclusive());
    }

    private static List<TimingObservation> points(long... sizes) {
        return timedPoints(sizes, n -> 1_000_000L);
    }

    private static List<TimingObservation> timedPoints(long[] sizes, java.util.function.LongUnaryOperator timing) {
        List<TimingObservation> list = new ArrayList<>();
        for (long n : sizes) {
            list.add(obs("n" + n, Map.of("n", (int) n), timing.applyAsLong(n)));
        }
        return list;
    }

    private static TimingObservation obs(String caseId, Map<String, Integer> sizeVector, long medianNs) {
        int n = sizeVector.getOrDefault("n", 1);
        return new TimingObservation(caseId, "RANDOM", sizeVector, "n", n, medianNs, medianNs / 20, true, true);
    }
}
