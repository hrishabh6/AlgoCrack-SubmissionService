package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaStaticAnalyzerTest {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void constantTimeEmptyMethod() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve() { return 0; }
                }
                """, "solve");
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
        assertEquals("O(1)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void linearSingleLoop() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) {
                    int s = 0;
                    for (int i = 0; i < nums.length; i++) { s += nums[i]; }
                    return s;
                  }
                }
                """, "solve", metadata("nums", "int[]"));
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void quadraticNestedLoops() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) {
                    int c = 0;
                    for (int i = 0; i < nums.length; i++) {
                      for (int j = 0; j < nums.length; j++) { c++; }
                    }
                    return c;
                  }
                }
                """, "solve", metadata("nums", "int[]"));
        String bigO = ComplexityExprSimplifier.toBigOString(result.timeExpression());
        assertTrue(bigO.equals("O(n^2)") || bigO.equals("O(n * n)"), () -> "unexpected " + bigO);
    }

    @Test
    void sequentialLoopsDominantTerm() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums, int[] other) {
                    int c = 0;
                    for (int i = 0; i < nums.length; i++) { c++; }
                    for (int j = 0; j < other.length; j++) { c++; }
                    return c;
                  }
                }
                """, "solve", metadataTwoArray());
        assertTrue(result.variables().containsKey("n"));
        assertTrue(result.variables().containsKey("m"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void linearRecursionDecrement() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    return 1 + solve(n - 1);
                  }
                }
                """, "solve", metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void auxiliarySpaceFromArrayAllocation() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int[] solve(int n) {
                    return new int[n];
                  }
                }
                """, "solve", metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    @Test
    void opaqueExternalCallDoesNotReturnHighConfidenceO1() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    return externalLib.process(n);
                  }
                }
                """, "solve", metadata("n", "int"));
        assertEquals(ComplexityResultKind.INCONCLUSIVE, result.resultKind());
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
        assertTrue(result.findings().stream().anyMatch(f -> f.category().equals("OPAQUE_CALL")));
    }

    @Test
    void unsupportedSyntaxProducesUnsupportedResult() {
        StaticAnalysisResult result = analyzer.analyze("class Solution { public void solve( {", null);
        assertEquals(ComplexityResultKind.UNSUPPORTED, result.resultKind());
        assertEquals("PARSER_UNSUPPORTED", result.errorCode());
    }

    @Test
    void unresolvedEntryMethodUnsupported() {
        StaticAnalysisResult result = analyzer.analyze("class Solution { }", null);
        assertEquals(ComplexityResultKind.UNSUPPORTED, result.resultKind());
        assertEquals("ENTRY_METHOD_UNRESOLVED", result.errorCode());
    }

    @Test
    void staticOnlyIncludesProfileUnavailableLimitation() {
        StaticAnalysisResult result = analyze("""
                class Solution { public int solve() { return 1; } }
                """, "solve");
        assertTrue(result.limitations().contains("PROFILE_UNAVAILABLE"));
    }

    private StaticAnalysisResult analyze(String code, String functionName) {
        return analyze(code, functionName, metadata("n", "int"));
    }

    private StaticAnalysisResult analyze(String code, String functionName, QuestionMetadataApiDto metadata) {
        QuestionMetadataApiDto effective = QuestionMetadataApiDto.builder()
                .functionName(functionName)
                .paramNames(metadata.getParamNames())
                .paramTypes(metadata.getParamTypes())
                .build();
        return analyzer.analyze(code, effective);
    }

    private static QuestionMetadataApiDto metadata(String paramName, String paramType) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(paramName))
                .paramTypes(List.of(paramType))
                .build();
    }

    private static QuestionMetadataApiDto metadataTwoArray() {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("nums", "other"))
                .paramTypes(List.of("int[]", "int[]"))
                .build();
    }
}
