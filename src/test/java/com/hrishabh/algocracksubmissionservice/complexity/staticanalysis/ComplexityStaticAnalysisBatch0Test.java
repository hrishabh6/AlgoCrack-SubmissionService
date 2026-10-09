package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.AnalysisDimensionCompleteness;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Permanent Batch 0 safety-containment regressions (fail-closed AST, loops, branches, recursion).
 */
class ComplexityStaticAnalysisBatch0Test {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void branchWorstCaseUnknownContagious() {
        ComplexityExpr known = ComplexityExpr.var("n");
        ComplexityExpr unknown = new ComplexityExpr.Unknown("opaque");
        assertTrue(ComplexityExprSimplifier.branchWorstCase(known, unknown) instanceof ComplexityExpr.Unknown);
        assertTrue(ComplexityExprSimplifier.branchWorstCase(unknown, known) instanceof ComplexityExpr.Unknown);
        assertTrue(ComplexityExprSimplifier.branchWorstCase(unknown, unknown) instanceof ComplexityExpr.Unknown);
    }

    @Test
    void switchContainingLinearLoopIsNotConstantHigh() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) {
                    switch (nums.length) {
                      default:
                        for (int i = 0; i < nums.length; i++) { }
                    }
                    return 0;
                  }
                }
                """, questionMetadata("nums", "int[]"));
        assertNotHighWrongConcrete(result);
        assertNotEquals("O(1)", bigO(result));
    }

    @Test
    void tryContainingLinearLoopIsNotConstantHigh() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums) {
                    try {
                      for (int i = 0; i < nums.length; i++) { }
                    } catch (Exception e) { }
                    return 0;
                  }
                }
                """, questionMetadata("nums", "int[]"));
        assertNotHighWrongConcrete(result);
        assertNotEquals("O(1)", bigO(result));
    }

    @Test
    void opaqueCallInIfConditionIsNotO1High() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (external.check(n)) { return 1; }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void opaqueCallInVariableInitializerMarksIncomplete() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    int x = external.work(n);
                    return x;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.OPAQUE_CALL)
                || result.timeCompleteness() == AnalysisDimensionCompleteness.INCOMPLETE);
    }

    @Test
    void opaqueCallInAssignmentRhsMarksIncomplete() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    int x = 0;
                    x = external.work(n);
                    return x;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
    }

    @Test
    void unknownBranchVersusKnownLinearBranchIsAuthoritativeUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums, boolean flag) {
                    if (flag) {
                      external.work(nums.length);
                    } else {
                      for (int i = 0; i < nums.length; i++) { }
                    }
                    return 0;
                  }
                }
                """, questionMetadata("nums", "int[]"));
        assertEquals("UNKNOWN", bigO(result));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
        assertNotEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
    }

    @Test
    void forLoopWithNoUpdateIsNotLinearHigh() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    for (int i = 0; i < n; ) { }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
        assertNotEquals("O(n)", bigO(result));
    }

    @Test
    void whileLoopWithNoUpdateIsNotLinearHigh() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    int i = 0;
                    while (i < n) { }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
        assertNotEquals("O(n)", bigO(result));
    }

    @Test
    void wrongDirectionLoopUpdateIsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    for (int i = 0; i < n; i--) { }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
    }

    @Test
    void continueBypassingUpdateIsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    for (int i = 0; i < n; i++) {
                      if (i % 2 == 0) continue;
                    }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN)
                || "UNKNOWN".equals(bigO(result)));
    }

    @Test
    void bodyMutationOfInductionVariableIsUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    for (int i = 0; i < n; i++) {
                      i = 0;
                    }
                    return 0;
                  }
                }
                """, questionMetadata("n", "int"));
        assertSafeUnknownOrLow(result);
    }

    @Test
    void mutualRecursionTerminatesWithoutStackOverflow() {
        assertDoesNotThrow(() -> analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    return a(n - 1);
                  }
                  int a(int n) {
                    if (n <= 0) return 0;
                    return b(n - 1);
                  }
                  int b(int n) {
                    if (n <= 0) return 0;
                    return a(n - 1);
                  }
                }
                """, questionMetadata("n", "int")));
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    return a(n - 1);
                  }
                  int a(int n) {
                    if (n <= 0) return 0;
                    return b(n - 1);
                  }
                  int b(int n) {
                    if (n <= 0) return 0;
                    return a(n - 1);
                  }
                }
                """, questionMetadata("n", "int"));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNKNOWN_RECURSIVE_CYCLE)
                || "UNKNOWN".equals(bigO(result)));
    }

    @Test
    void longerHelperCycleTerminates() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    return a(n);
                  }
                  int a(int n) { return b(n); }
                  int b(int n) { return c(n); }
                  int c(int n) { return a(n - 1); }
                }
                """, questionMetadata("n", "int"));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.UNKNOWN_RECURSIVE_CYCLE)
                || "UNKNOWN".equals(bigO(result)));
    }

    @Test
    void analyzerVersionIsBatch0Dev() {
        assertEquals("static-v2.2-dev", JavaStaticAnalyzer.ANALYZER_VERSION);
        assertEquals("static-confidence-v2.1-dev", JavaStaticAnalyzer.CONFIDENCE_MODEL_VERSION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adversarialCorpus")
    void adversarialCorpusNeverCrashes(AdversarialCase c) {
        StaticAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze(c.source(), c.metadata()));
        assertNotNull(result);
        if (c.postBatch0MustNotBeHighWrong()) {
            assertNotHighWrongConcrete(result, c.name());
        }
    }

    static Stream<AdversarialCase> adversarialCorpus() {
        return AdversarialCase.all().stream();
    }

    record AdversarialCase(
            String name,
            String source,
            QuestionMetadataApiDto metadata,
            boolean postBatch0MustNotBeHighWrong) {

        static List<AdversarialCase> all() {
            return List.of(
                    c("Return input value", """
                            class Solution { public int solve(int n) { return n; } }
                            """, questionMetadata("n", "int"), false),
                    c("for with no update", """
                            class Solution { public int solve(int n) { for (int i=0;i<n;) {} return 0; } }
                            """, questionMetadata("n", "int"), true),
                    c("while with no update", """
                            class Solution { public int solve(int n) { int i=0; while(i<n){} return 0; } }
                            """, questionMetadata("n", "int"), true),
                    c("Loop inside switch", """
                            class Solution { public int solve(int[] nums) {
                              switch (0) { default: for (int i=0;i<nums.length;i++){} }
                              return 0; } }
                            """, questionMetadata("nums", "int[]"), true),
                    c("Loop inside try", """
                            class Solution { public int solve(int[] nums) {
                              try { for (int i=0;i<nums.length;i++){} } catch(Exception e){}
                              return 0; } }
                            """, questionMetadata("nums", "int[]"), true),
                    c("Opaque if condition", """
                            class Solution { public int solve(int n) {
                              if (external.check(n)) return 1; return 0; } }
                            """, questionMetadata("n", "int"), true),
                    c("Unknown branch vs linear", """
                            class Solution { public int solve(int[] nums, boolean f) {
                              if (f) external.w(nums.length); else for(int i=0;i<nums.length;i++){}
                              return 0; } }
                            """, questionMetadata("nums", "int[]"), true),
                    c("Mutual recursion", """
                            class Solution {
                              public int solve(int n) { return n<=0?0:a(n-1); }
                              int a(int n) { return n<=0?0:b(n-1); }
                              int b(int n) { return n<=0?0:a(n-1); }
                            }
                            """, questionMetadata("n", "int"), true),
                    c("Fibonacci two-call", """
                            class Solution { public int solve(int n) {
                              if (n<=1) return n; return solve(n-1)+solve(n-2); } }
                            """, questionMetadata("n", "int"), false),
                    c("Concrete HashMap get", """
                            class Solution { public int solve(java.util.HashMap<Integer,Integer> map) {
                              return map.get(1); } }
                            """, questionMetadata("map", "java.util.HashMap"), false));
        }
    }

    private static AdversarialCase c(
            String name, String source, QuestionMetadataApiDto metadata, boolean mustNotHighWrong) {
        return new AdversarialCase(name, source, metadata, mustNotHighWrong);
    }

    private StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return analyzer.analyze(code, metadata);
    }

    private static String bigO(StaticAnalysisResult result) {
        if (result.timeExpression() == null) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(result.timeExpression());
    }

    private static void assertSafeUnknownOrLow(StaticAnalysisResult result) {
        assertNotHighWrongConcrete(result);
        assertTrue("UNKNOWN".equals(bigO(result))
                        || result.resultKind() != ComplexityResultKind.STATIC_ONLY
                        || result.timeConfidence() == ComplexityConfidence.LOW
                        || result.timeConfidence() == null,
                () -> "expected safe unknown/low, got " + bigO(result) + " kind=" + result.resultKind()
                        + " conf=" + result.timeConfidence());
    }

    private static void assertNotHighWrongConcrete(StaticAnalysisResult result) {
        assertNotHighWrongConcrete(result, null);
    }

    private static void assertNotHighWrongConcrete(StaticAnalysisResult result, String caseName) {
        if (result.timeConfidence() == ComplexityConfidence.HIGH
                && result.resultKind() == ComplexityResultKind.STATIC_ONLY
                && result.timeExpression() != null
                && !(result.timeExpression() instanceof ComplexityExpr.Unknown)) {
            fail(() -> (caseName != null ? caseName + ": " : "")
                    + "unsafe HIGH concrete static " + bigO(result));
        }
    }

    private static QuestionMetadataApiDto questionMetadata(String paramName, String paramType) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(paramName))
                .paramTypes(List.of(paramType))
                .build();
    }
}
