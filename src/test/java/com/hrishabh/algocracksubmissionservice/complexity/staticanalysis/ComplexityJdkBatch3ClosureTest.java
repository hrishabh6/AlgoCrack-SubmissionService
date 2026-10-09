package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.AnalysisDimensionCompleteness;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasisMerge;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityJdkBatch3ClosureTest {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void arraysCopyOfComposesLocalAuxiliarySpaceAfterBatch4() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(int[] a) {
                  int[] b = java.util.Arrays.copyOf(a, a.length);
                  return b.length;
                } }
                """, meta("a", "int[]"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
        assertEquals(AnalysisDimensionCompleteness.COMPLETE, result.spaceCompleteness());
        assertFalse(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_ALLOCATION_NOT_COMPOSED));
        assertEquals(ComplexityConfidence.HIGH, result.spaceConfidence());
    }

    @Test
    void stringToCharArrayComposesLocalAuxiliarySpaceAfterBatch4() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(String n) {
                  char[] chars = n.toCharArray();
                  return chars.length;
                } }
                """, meta("n", "String"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
        assertFalse(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_ALLOCATION_NOT_COMPOSED));
    }

    @Test
    void hashMapWithIntegerKeyRemainsExpectedO1Time() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(java.util.HashMap<Integer,Integer> map) {
                  return map.get(1);
                } }
                """, meta("map", "java.util.HashMap"));
        assertEquals("O(1)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertEquals(ComplexityBoundBasis.EXPECTED_ASSUMPTION, result.timeBoundBasis());
    }

    @Test
    void hashMapWithUserKeyTypeDoesNotAssumeConstantHashCallbacks() {
        StaticAnalysisResult result = analyze("""
                class Key { int n; Key(int n){this.n=n;} public int hashCode(){ return n; } }
                class Solution { public int solve(java.util.HashMap<Key,Integer> map, Key k) {
                  return map.get(k);
                } }
                """, meta("map", "java.util.HashMap", "k", "Key"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED));
    }

    @Test
    void basisMergeIsOrderIndependentForMixedOperations() {
        StaticAnalysisResult expectedFirst = analyzeMixedBasis(
                "map.get(1); list.add(0, n);");
        StaticAnalysisResult worstFirst = analyzeMixedBasis(
                "list.add(0, n); map.get(1);");
        assertResultsEquivalent(expectedFirst, worstFirst);
    }

    @Test
    void basisMergePreservesDominantAssumptionAcrossPairs() {
        assertEquals(ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityBoundBasisMerge.merge(ComplexityBoundBasis.WORST_CASE, ComplexityBoundBasis.EXPECTED_ASSUMPTION));
        assertEquals(ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityBoundBasisMerge.merge(ComplexityBoundBasis.EXPECTED_ASSUMPTION, ComplexityBoundBasis.WORST_CASE));
        assertEquals(ComplexityBoundBasis.AMORTIZED_ASSUMPTION,
                ComplexityBoundBasisMerge.merge(ComplexityBoundBasis.WORST_CASE, ComplexityBoundBasis.AMORTIZED_ASSUMPTION));
        assertEquals(ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityBoundBasisMerge.merge(ComplexityBoundBasis.AMORTIZED_ASSUMPTION, ComplexityBoundBasis.EXPECTED_ASSUMPTION));
        assertEquals(ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityBoundBasisMerge.merge(ComplexityBoundBasis.EXPECTED_ASSUMPTION, ComplexityBoundBasis.AMORTIZED_ASSUMPTION));
    }

    private static StaticAnalysisResult analyzeMixedBasis(String body) {
        return analyze("""
                class Solution {
                  public int solve(int n, java.util.HashMap<Integer,Integer> map, java.util.ArrayList<Integer> list) {
                    %s
                    return 0;
                  }
                }
                """.formatted(body), meta("n", "int", "map", "java.util.HashMap", "list", "java.util.ArrayList"));
    }

    private static void assertResultsEquivalent(StaticAnalysisResult a, StaticAnalysisResult b) {
        assertEquals(ComplexityExprSimplifier.toBigOString(a.timeExpression()),
                ComplexityExprSimplifier.toBigOString(b.timeExpression()));
        assertEquals(a.timeBoundBasis(), b.timeBoundBasis());
        assertEquals(a.timeConfidence(), b.timeConfidence());
        assertEquals(a.resultKind(), b.resultKind());
    }

    private static StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return new JavaStaticAnalyzer(new JdkKnowledgeBase()).analyze(code, metadata);
    }

    private static QuestionMetadataApiDto meta(String p1, String t1) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(p1))
                .paramTypes(List.of(t1))
                .build();
    }

    private static QuestionMetadataApiDto meta(String p1, String t1, String p2, String t2) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(p1, p2))
                .paramTypes(List.of(t1, t2))
                .build();
    }

    private static QuestionMetadataApiDto meta(String p1, String t1, String p2, String t2, String p3, String t3) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(p1, p2, p3))
                .paramTypes(List.of(t1, t2, t3))
                .build();
    }
}
