package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityJdkBatch3Test {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void collectionsSortUsesSecondListSymbol() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public void solve(java.util.List a, java.util.List b) {
                    java.util.Collections.sort(b);
                  }
                }
                """, meta("a", "java.util.List", "b", "java.util.List"));
        assertEquals("O(m*log(m))", normalize(result));
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
    }

    @Test
    void fullyQualifiedJdkArraysStillResolves() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public void solve(int[] nums) {
                    java.util.Arrays.sort(nums);
                  }
                }
                """, meta("nums", "int[]"));
        assertTrue(result.findings().stream().anyMatch(f -> "JDK_CALL".equals(f.category())));
        assertTrue(normalize(result).contains("log"));
    }

    @Test
    void userDefinedPriorityQueueDoesNotGetJdkOfferSemantics() {
        StaticAnalysisResult result = analyze("""
                class PriorityQueue { void offer(int x) {} }
                class Solution {
                  public int solve(int n) {
                    PriorityQueue q = new PriorityQueue();
                    q.offer(n);
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertFalse(result.findings().stream().anyMatch(f -> "JDK_CALL".equals(f.category())));
    }

    @Test
    void priorityQueueParameterOfferWithoutCardinalityIsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(java.util.PriorityQueue<Integer> q) {
                  q.offer(1); return 0; } }
                """, meta("q", "java.util.PriorityQueue"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_CARDINALITY_UNRESOLVED)
                || result.resultKind() != ComplexityResultKind.STATIC_ONLY);
    }

    @Test
    void localPriorityQueueOfferWithoutInputCardinalityRelationIsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(int n) {
                  java.util.PriorityQueue<Integer> q = new java.util.PriorityQueue<>();
                  q.offer(n);
                  return 0; } }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void hashMapGetRemainsExpectedBasisNotHighWorstCaseAdvertisement() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(java.util.HashMap<Integer,Integer> map) {
                  return map.get(1); } }
                """, meta("map", "java.util.HashMap"));
        assertEquals("O(1)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void unsupportedStreamPipelineRemainsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve(java.util.List<Integer> xs) {
                  return xs.stream().filter(x -> x > 0).mapToInt(x -> x).sum(); } }
                """, meta("xs", "java.util.List"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void jdkKnowledgeBaseVersionIsV4() {
        assertEquals("jdk21-v4", new JdkKnowledgeBase().version());
    }

    private static String normalize(StaticAnalysisResult result) {
        return ComplexityExprSimplifier.toBigOString(result.timeExpression()).replace(" ", "");
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
}
