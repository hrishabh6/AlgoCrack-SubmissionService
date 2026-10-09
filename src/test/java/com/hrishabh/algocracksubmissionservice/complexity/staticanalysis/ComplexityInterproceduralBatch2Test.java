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

class ComplexityInterproceduralBatch2Test {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void overloadedHelperResolvesIntOverload() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) { return helper(n); }
                  int helper(int n) { for (int i = 0; i < n; i++) {} return 0; }
                  int helper(String s) { return s.length(); }
                }
                """, metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void overloadedHelperDeclarationOrderInvariant() {
        String a = """
                class Solution {
                  public int solve(int n) { return helper(n); }
                  int helper(int n) { for (int i = 0; i < n; i++) {} return 0; }
                  int helper(String s) { return s.length(); }
                }
                """;
        String b = """
                class Solution {
                  public int solve(int n) { return helper(n); }
                  int helper(String s) { return s.length(); }
                  int helper(int n) { for (int i = 0; i < n; i++) {} return 0; }
                }
                """;
        String t1 = ComplexityExprSimplifier.toBigOString(analyze(a, metadata("n", "int")).timeExpression());
        String t2 = ComplexityExprSimplifier.toBigOString(analyze(b, metadata("n", "int")).timeExpression());
        assertEquals(t1, t2);
        assertEquals("O(n)", t1);
    }

    @Test
    void unresolvedOverloadWhenArgumentTypeUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  int x;
                  public int solve(int n) { return helper(x); }
                  int helper(int n) { return n; }
                  long helper(long n) { return (int) n; }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNRESOLVED_HELPER_TARGET));
    }

    @Test
    void helperUsesCallSiteSymbolNotPrimaryN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) { helper(m); return 0; }
                  void helper(int x) { for (int i = 0; i < x; i++) {} }
                }
                """, metadata("n", "int", "m", "int"));
        assertEquals("O(m)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void sequentialHelpersComposeAsSum() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) { helperA(n); helperB(m); return 0; }
                  void helperA(int x) { for (int i = 0; i < x; i++) {} }
                  void helperB(int y) { for (int j = 0; j < y; j++) {} }
                }
                """, metadata("n", "int", "m", "int"));
        String bigO = ComplexityExprSimplifier.toBigOString(result.timeExpression());
        assertTrue(bigO.contains("n") && bigO.contains("m"));
        assertTrue(bigO.contains("+") || bigO.equals("O(n+m)"));
    }

    @Test
    void decrementRecurrenceWithLinearSiblingIsQuadratic() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    for (int i = 0; i < n; i++) {}
                    return solve(n - 1);
                  }
                }
                """, metadata("n", "int"));
        assertTrue(ComplexityAdversarialCorpus36MatrixTest.matchesExpectedBigO("O(n²)", bigO(result)),
                () -> "expected O(n²), got " + bigO(result));
    }

    @Test
    void halvingRecurrenceWithLinearSiblingIsLinear() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    return solve(n / 2) + n;
                  }
                }
                """, metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void mutualRecursionDoesNotCrashAndIsUnknown() {
        StaticAnalysisResult result = assertDoesNotThrow(() -> analyze("""
                class Solution {
                  public int solve(int n) { return n <= 0 ? 0 : a(n - 1); }
                  int a(int n) { return n <= 0 ? 0 : b(n - 1); }
                  int b(int n) { return n <= 0 ? 0 : a(n - 1); }
                }
                """, metadata("n", "int")));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void singleCandidateDoesNotResolveKnownIncompatibleStringToIntArray() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(String s) { helper(s); return 0; }
                  void helper(int[] x) { for (int i = 0; i < x.length; i++) {} }
                }
                """, metadata("s", "String"));
        assertEquals("UNKNOWN", bigO(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNRESOLVED_HELPER_TARGET));
    }

    @Test
    void singleCandidateDoesNotResolveKnownIncompatibleIntToString() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) { helper(n); return 0; }
                  void helper(String x) { return; }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", bigO(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNRESOLVED_HELPER_TARGET));
    }

    @Test
    void singleCandidateDoesNotResolveKnownIncompatibleIntArrayToInt() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) { helper(nums); return 0; }
                  void helper(int x) { for (int i = 0; i < x; i++) {} }
                }
                """, metadata("nums", "int[]"));
        assertEquals("UNKNOWN", bigO(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNRESOLVED_HELPER_TARGET));
    }

    @Test
    void singleCandidateResolvesWhenArgumentTypeMatches() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) { helper(nums); return 0; }
                  void helper(int[] x) { for (int i = 0; i < x.length; i++) {} }
                }
                """, metadata("nums", "int[]"));
        assertEquals("O(n)", bigO(result));
        assertEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
    }

    @Test
    void recurrenceUnknownWhenOpaqueSiblingBeforeDecrementCall() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    externalUnknown(n);
                    return solve(n - 1);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", bigO(result));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void recurrenceUnknownWhenOpaqueWorkOnPossibleBranchBeforeHalvingCall() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    if (n > 2) {
                      externalUnknown(n);
                    }
                    return solve(n / 2);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", bigO(result));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void recurrenceUnknownWhenRecursiveMethodCallsHelperWithOpaqueWork() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    helperWithOpaque(n);
                    return solve(n - 1);
                  }
                  void helperWithOpaque(int n) { externalUnknown(n); }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", bigO(result));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void recursiveStackDepthDecrementIsN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    return solve(n - 1);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    private static String bigO(StaticAnalysisResult result) {
        return ComplexityExprSimplifier.toBigOString(result.timeExpression());
    }

    private static StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return new JavaStaticAnalyzer(new JdkKnowledgeBase()).analyze(code, metadata);
    }

    private static QuestionMetadataApiDto metadata(String p1, String t1) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(p1))
                .paramTypes(List.of(t1))
                .build();
    }

    private static QuestionMetadataApiDto metadata(String p1, String t1, String p2, String t2) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(p1, p2))
                .paramTypes(List.of(t1, t2))
                .build();
    }
}
