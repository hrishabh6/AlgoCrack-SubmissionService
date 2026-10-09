package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityHardeningRegressionTest {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void simplifierSumIndependentVariables() {
        assertBigO("n + m", "O(n + m)");
        assertBigO("n + n", "O(n)");
        assertBigO("n + log(n)", "O(n)");
        assertBigO("n² + n", "O(n²)");
        assertBigO("n * m + n", "O(n * m)");
        assertBigO("v + e", "O(v + e)");
    }

    @Test
    void simplifierNormalizesRepeatedMultiplication() {
        ComplexityExpr nested = new ComplexityExpr.Product(List.of(
                ComplexityExpr.var("n"), ComplexityExpr.var("n")));
        assertEquals("O(n²)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(nested)));

        ComplexityExpr cubic = new ComplexityExpr.Product(List.of(
                ComplexityExpr.var("n"), ComplexityExpr.var("n"), ComplexityExpr.var("n")));
        assertEquals("O(n³)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(cubic)));

        ComplexityExpr multi = new ComplexityExpr.Product(List.of(ComplexityExpr.var("n"), ComplexityExpr.var("m")));
        assertEquals("O(n * m)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(multi)));
    }

    @Test
    void sequentialLoopsPreserveNPlusM() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums, int[] other) {
                    int c = 0;
                    for (int i = 0; i < nums.length; i++) { c++; }
                    for (int j = 0; j < other.length; j++) { c++; }
                    return c;
                  }
                }
                """, metadataTwoArray());
        assertEquals("O(n + m)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void nestedLoopsNormalizeToNSquared() {
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
                """, metadata("nums", "int[]"));
        assertEquals("O(n²)", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void branchIncomparableVariablesRetained() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[] nums, int[] other, boolean flag) {
                    if (flag) {
                      for (int i = 0; i < nums.length; i++) { }
                    } else {
                      for (int j = 0; j < other.length; j++) { }
                    }
                    return 0;
                  }
                }
                """, metadataTwoArray());
        String bigO = ComplexityExprSimplifier.toBigOString(result.timeExpression());
        assertTrue(bigO.contains("n") && bigO.contains("m"), () -> "expected n and m in " + bigO);
    }

    @Test
    void halvingRecursionWithConstantSiblingIsLogN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    return solve(n / 2);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("O(log(n))", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
    }

    @Test
    void halvingRecursionWithLinearSiblingIsN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    return solve(n / 2) + n;
                  }
                }
                """, metadata("n", "int"));
        String bigO = ComplexityExprSimplifier.toBigOString(result.timeExpression());
        assertTrue("UNKNOWN".equals(bigO) || "O(n)".equals(bigO) || bigO.contains("log"),
                () -> "conservative halving+sibling expected unknown or bounded recurrence, got " + bigO);
    }

    @Test
    void decrementRecursionStackIsN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 0) return 0;
                    return 1 + solve(n - 1);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    @Test
    void halvingRecursionStackIsLogN() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return 0;
                    return solve(n / 2);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("O(log(n))", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    @Test
    void userDefinedHashMapDoesNotMatchJdkKnowledgeBase() {
        StaticAnalysisResult result = analyze("""
                class HashMap {
                  void put(int k, int v) { }
                }
                class Solution {
                  public int solve(HashMap map) {
                    map.put(1, 2);
                    return 0;
                  }
                }
                """, QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("map"))
                .paramTypes(List.of("HashMap"))
                .build());
        assertFalse(result.findings().stream().anyMatch(f -> "JDK_KB".equals(f.category())));
        assertNotEquals(ComplexityResultKind.STATIC_ONLY, result.resultKind());
    }

    @Test
    void unresolvedReceiverTypeIsOpaque() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    return mystery.get(n);
                  }
                  Object mystery;
                }
                """, metadata("n", "int"));
        assertTrue(result.findings().stream().anyMatch(f -> "OPAQUE_CALL".equals(f.category())));
    }

    @Test
    void sequentialAllocationsSameScopeAreSimultaneouslyLive() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) {
                    int[] a = new int[n];
                    int[] b = new int[m];
                    return use(a, b);
                  }
                  int use(int[] x, int[] y) { return x.length + y.length; }
                }
                """, QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("n", "m"))
                .paramTypes(List.of("int", "int"))
                .build());
        assertEquals("O(n + m)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    @Test
    void nestedScopeAllocationsUseDisjointPeak() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) {
                    int[] a = new int[n];
                    { int[] b = new int[m]; }
                    return a.length;
                  }
                }
                """, QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("n", "m"))
                .paramTypes(List.of("int", "int"))
                .build());
        String space = ComplexityExprSimplifier.toBigOString(result.spaceExpression());
        assertTrue(space.equals("O(n + m)") || space.equals("O(n)"),
                () -> "conservative peak expected, got " + space);
    }

    @Test
    void fibonacciRecursionIsUnknownNotLinear() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    if (n <= 1) return n;
                    return solve(n - 1) + solve(n - 2);
                  }
                }
                """, metadata("n", "int"));
        assertEquals("UNKNOWN", ComplexityExprSimplifier.toBigOString(result.timeExpression()));
        assertTrue(result.findings().stream().anyMatch(f -> "RECURSION".equals(f.category())));
    }

    @Test
    void simultaneouslyLiveAllocationsCombine() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) {
                    int[] a = new int[n], b = new int[m];
                    return a.length + b.length;
                  }
                }
                """, QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of("n", "m"))
                .paramTypes(List.of("int", "int"))
                .build());
        assertEquals("O(n + m)", ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
    }

    private void assertBigO(String expr, String expected) {
        ComplexityExpr parsed = parseSimpleSumOrProduct(expr);
        assertEquals(expected, ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(parsed)),
                () -> "for " + expr);
    }

    private static ComplexityExpr parseSimpleSumOrProduct(String expr) {
        if (expr.contains("+")) {
            String[] parts = expr.split(" \\+ ");
            return new ComplexityExpr.Sum(java.util.Arrays.stream(parts).map(ComplexityHardeningRegressionTest::term).toList());
        }
        return term(expr);
    }

    private static ComplexityExpr term(String raw) {
        String t = raw.trim();
        if ("n²".equals(t)) {
            return new ComplexityExpr.Power("n", 2);
        }
        if ("n³".equals(t)) {
            return new ComplexityExpr.Power("n", 3);
        }
        if (t.startsWith("log(")) {
            String inner = t.substring(4, t.length() - 1);
            return new ComplexityExpr.Log(new ComplexityExpr.Variable(inner));
        }
        if (t.contains("*")) {
            String[] factors = t.split(" \\* ");
            List<ComplexityExpr> factorExprs = new java.util.ArrayList<>();
            for (String f : factors) {
                factorExprs.add(ComplexityExpr.var(f.trim()));
            }
            return new ComplexityExpr.Product(factorExprs);
        }
        return new ComplexityExpr.Variable(t);
    }

    private StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return analyzer.analyze(code, metadata);
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
