package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdkKnowledgeBasePrecisionTest {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void arraysSortSingleArrayOverloadModeled() {
        StaticAnalysisResult result = analyzer.analyze("""
                import java.util.Arrays;
                class Solution {
                  public void solve(int[] nums) {
                    Arrays.sort(nums);
                  }
                }
                """, metadata("nums", "int[]"));
        assertTrue(result.findings().stream().anyMatch(f -> "JDK_CALL".equals(f.category())));
        assertTrue(ComplexityExprSimplifier.toBigOString(result.timeExpression()).contains("log"));
    }

    @Test
    void arraysSortRangeOverloadIsModeledWithRangeLength() {
        StaticAnalysisResult result = analyzer.analyze("""
                import java.util.Arrays;
                class Solution {
                  public void solve(int[] nums) {
                    Arrays.sort(nums, 0, nums.length);
                  }
                }
                """, metadata("nums", "int[]"));
        assertTrue(result.findings().stream().anyMatch(f -> "JDK_CALL".equals(f.category())));
        assertTrue(ComplexityExprSimplifier.toBigOString(result.timeExpression()).contains("log"));
    }

    @Test
    void userDefinedArraysDoesNotReceiveJdkSortSemantics() {
        StaticAnalysisResult result = analyzer.analyze("""
                class Arrays { static void sort(int[] x) {} }
                class Solution { public int solve(int[] a) { Arrays.sort(a); return 0; } }
                """, metadata("a", "int[]"));
        assertFalse(result.findings().stream().anyMatch(f -> "JDK_CALL".equals(f.category())));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void listInterfaceReceiverDoesNotMatchJdkListSemantics() {
        StaticAnalysisResult result = analyzer.analyze("""
                import java.util.List;
                class Solution {
                  public int solve(List<Integer> values) {
                    return values.size();
                  }
                }
                """, QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("values"))
                .paramTypes(List.of("List"))
                .build());
        assertTrue(result.findings().stream().anyMatch(f -> "OPAQUE_CALL".equals(f.category())));
    }

    private static QuestionMetadataApiDto metadata(String paramName, String paramType) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(paramName))
                .paramTypes(List.of(paramType))
                .build();
    }
}
