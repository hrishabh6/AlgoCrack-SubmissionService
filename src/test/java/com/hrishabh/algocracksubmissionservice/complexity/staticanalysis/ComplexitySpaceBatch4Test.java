package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 4 auxiliary-space regression suite.
 */
class ComplexitySpaceBatch4Test {

    private static JavaStaticAnalyzer analyzer;

    @BeforeAll
    static void init() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void returnRequiredArrayIsNotAuxiliary() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int[] solve(int n) { return new int[n]; }
                }
                """, meta("n", "int"));
        assertEquals("O(1)", space(result));
        assertEquals(ComplexityConfidence.HIGH, result.spaceConfidence());
    }

    @Test
    void helperReturnedAllocationBecomesCallerAuxiliary() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) { int[] a = make(n); return a.length; }
                  int[] make(int n) { return new int[n]; }
                }
                """, meta("n", "int"));
        assertEquals("O(n)", space(result));
    }

    @Test
    void passThroughHelperReturnExcludesOutputAllocation() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int[] solve(int n) { return make(n); }
                  int[] make(int n) { return new int[n]; }
                }
                """, meta("n", "int"));
        assertEquals("O(1)", space(result));
    }

    @Test
    void helperTemporaryPeakSurvivesScalarReturn() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) { return helper(n); }
                  int helper(int n) {
                    int[] temp = new int[n];
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("O(n)", space(result));
    }

    @Test
    void localListGrowsWithProvenLoop() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(int n) {
                  java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
                  for (int i=0;i<n;i++) { list.add(i); }
                  return list.size(); } }
                """, meta("n", "int"));
        assertEquals("O(n)", space(result));
    }

    @Test
    void arraysCopyOfLocalCountsAsAuxiliary() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(int[] a) {
                  int[] b = java.util.Arrays.copyOf(a, a.length); return b.length; } }
                """, meta("a", "int[]"));
        assertEquals("O(n)", space(result));
    }

    @Test
    void initializedMutableStaticEmitsLimitation() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  static java.util.List<Integer> cache = new java.util.ArrayList<>();
                  public int solve(int n) { cache.add(n); return cache.size(); }
                }
                """, meta("n", "int"));
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("MUTABLE_STATIC"))
                || result.findings().stream().anyMatch(f -> "MUTABLE_STATIC".equals(f.category())));
    }

    @Test
    void incompleteSpaceCannotBeHighWhenOwnershipUnresolved() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int[] solve(int n) {
                    int[] temp = new int[n];
                    return external.transform(temp);
                  }
                }
                """, meta("n", "int"));
        if (!"UNKNOWN".equals(space(result))) {
            assertNotEquals(ComplexityConfidence.HIGH, result.spaceConfidence());
        }
    }

    @Test
    void timeCanStayCompleteWhileSpaceUsesJdkAllocation() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(int[] a) {
                  int[] b = java.util.Arrays.copyOf(a, a.length); return b.length; } }
                """, meta("a", "int[]"));
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
        assertEquals("O(n)", space(result));
    }

    private static String space(StaticAnalysisResult result) {
        return ComplexityExprSimplifier.toBigOString(result.spaceExpression());
    }

    private static StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return analyzer.analyzeSnippet(code, metadata);
    }

    private static QuestionMetadataApiDto meta(String... namesAndTypes) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(namesAndTypes).subList(0, namesAndTypes.length / 2))
                .paramTypes(List.of(namesAndTypes).subList(namesAndTypes.length / 2, namesAndTypes.length))
                .build();
    }
}
