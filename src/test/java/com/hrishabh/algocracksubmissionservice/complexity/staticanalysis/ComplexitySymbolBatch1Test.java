package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComplexitySymbolBatch1Test {

    private JavaStaticAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void returnInputIsConstantTime() {
        StaticAnalysisResult r = analyze("""
                class Solution { public int solve(int n) { return n; } }
                """, meta("n", "int"));
        assertEquals("O(1)", bigO(r));
    }

    @Test
    void multiplyHelpersSumCostsNotProduct() {
        StaticAnalysisResult r = analyze("""
                class Solution {
                  public int solve(int n, int m) { return left(n) * right(m); }
                  int left(int n) { for (int i=0;i<n;i++){} return 0; }
                  int right(int m) { for (int j=0;j<m;j++){} return 0; }
                }
                """, meta("n", "int", "m", "int"));
        assertEquals("O(n + m)", bigO(r));
    }

    @Test
    void independentRowsAndResultDoNotCollapse() {
        StaticAnalysisResult r = analyze("""
                class Solution {
                  public int solve(int rows, int result) {
                    for (int i=0;i<rows;i++) for (int j=0;j<result;j++) {}
                    return 0;
                  }
                }
                """, meta("rows", "int", "result", "int"));
        String bigO = bigO(r);
        assertTrue(bigO.contains("rows") && bigO.contains("result"), () -> bigO);
        assertFalse(bigO.contains("rows²") || bigO.contains("rows^2"), () -> bigO);
    }

    @Test
    void matrixTwoDimensionalSpace() {
        StaticAnalysisResult r = analyze("""
                class Solution {
                  public int solve(int[][] matrix) {
                    int rows = matrix.length;
                    int cols = matrix[0].length;
                    boolean[][] g = new boolean[rows][cols];
                    return 0;
                  }
                }
                """, meta("matrix", "int[][]"));
        assertTrue(r.variables().containsKey("n") && r.variables().containsKey("m"));
        String space = bigOSpace(r);
        assertEquals("O(n * m)", space, () -> "space=" + space + " vars=" + r.variables());
        assertNotEquals("O(n)", space);
    }

    @Test
    void simplifierNeverMergesDistinctVariablesInProduct() {
        ComplexityExpr expr = new ComplexityExpr.Product(List.of(
                ComplexityExpr.var("rows"), ComplexityExpr.var("result")));
        assertEquals(
                ComplexityExprSimplifier.normalizeShape(expr),
                ComplexityExprSimplifier.normalizeShape(ComplexityExprSimplifier.simplify(expr)));
    }

    @Test
    void simplifierPreservesIndependentSum() {
        assertBigO(new ComplexityExpr.Sum(List.of(ComplexityExpr.var("n"), ComplexityExpr.var("m"))), "O(n + m)");
        assertBigO(new ComplexityExpr.Sum(List.of(ComplexityExpr.var("v"), ComplexityExpr.var("e"))), "O(v + e)");
    }

    @Test
    void undefinedSymbolInJdkExpressionIsNotHighStatic() {
        StaticAnalysisResult r = analyze("""
                class Solution { public int solve(java.util.PriorityQueue<Integer> q) {
                  q.offer(1); return 0; } }
                """, meta("q", "java.util.PriorityQueue"));
        assertNotEquals(ComplexityResultKind.STATIC_ONLY, r.resultKind());
        assertNotEquals(ComplexityConfidence.HIGH, r.timeConfidence());
    }

    @Test
    void documentedSymbolsCoverExpression() {
        ComplexityExpr expr = ComplexityExprSimplifier.simplify(
                new ComplexityExpr.Product(List.of(ComplexityExpr.var("n"), ComplexityExpr.var("m"))));
        assertTrue(ComplexityExprSimplifier.allVariablesDocumented(expr, Set.of("n", "m")));
        assertFalse(ComplexityExprSimplifier.allVariablesDocumented(expr, Set.of("n")));
    }

    private static void assertBigO(ComplexityExpr expr, String expected) {
        assertEquals(expected, ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(expr)));
    }

    private static String bigO(StaticAnalysisResult r) {
        if (r.timeExpression() instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(r.timeExpression());
    }

    private static String bigOSpace(StaticAnalysisResult r) {
        if (r.spaceExpression() == null || r.spaceExpression() instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(r.spaceExpression());
    }

    private StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return analyzer.analyze(code, metadata);
    }

    private static QuestionMetadataApiDto meta(String... pairs) {
        var b = QuestionMetadataApiDto.builder().functionName("solve");
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<String> types = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            names.add(pairs[i]);
            types.add(pairs[i + 1]);
        }
        return b.paramNames(names).paramTypes(types).build();
    }
}
