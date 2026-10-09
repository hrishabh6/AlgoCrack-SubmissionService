package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Replays audit Section 12 corpus against post-Batch-0 analyzer (no crashes; matrix emitted to stdout).
 */
class ComplexityAdversarialCorpus36MatrixTest {

    private static JavaStaticAnalyzer analyzer;

    @BeforeAll
    static void init() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void corpusNeverCrashesAndPrintsMatrix() {
        System.out.println("CASE|EXPECTED|POST_RESULT|POST_CONF|DISPOSITION");
        int correct = 0, safe = 0, wrong = 0, crash = 0;
        for (Row row : ROWS) {
            String post;
            ComplexityConfidence conf = null;
            ComplexityResultKind kind = null;
            try {
                StaticAnalysisResult result = assertDoesNotThrow(() -> analyzer.analyze(row.source(), row.metadata()));
                post = bigO(result);
                conf = result.timeConfidence();
                kind = result.resultKind();
            } catch (Throwable t) {
                post = "CRASH";
                crash++;
                System.out.println(row.name() + "|" + row.expected() + "|CRASH||CRASH|Batch1+");
                continue;
            }
            String disposition = classify(row.expected(), post, conf, kind);
            switch (disposition) {
                case "CORRECT_CONCRETE" -> correct++;
                case "SAFE_UNKNOWN" -> safe++;
                case "STILL_WRONG" -> wrong++;
                default -> {
                }
            }
            System.out.println(row.name() + "|" + row.expected() + "|" + post + "|" + conf + "|" + disposition + "|Batch0/P1+");
        }
        System.out.println("SUMMARY CORRECT_CONCRETE=" + correct + " SAFE_UNKNOWN=" + safe + " STILL_WRONG=" + wrong + " CRASH=" + crash);
        assertEquals(0, crash);
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected crash count " + expected + " but was " + actual);
        }
    }

    private static String classify(String expected, String post, ComplexityConfidence conf, ComplexityResultKind kind) {
        if ("CRASH".equals(post)) {
            return "CRASH";
        }
        if (post.equalsIgnoreCase(expected) || expected.contains(post.replace("O(", "").replace(")", ""))) {
            if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY) {
                return "CORRECT_CONCRETE";
            }
            if (!"UNKNOWN".equals(post)) {
                return "CORRECT_CONCRETE";
            }
        }
        if ("UNKNOWN".equals(post) || kind == ComplexityResultKind.INCONCLUSIVE || kind == ComplexityResultKind.UNSUPPORTED) {
            return "SAFE_UNKNOWN";
        }
        if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY) {
            return "STILL_WRONG";
        }
        return "STILL_WRONG";
    }

    private static String bigO(StaticAnalysisResult result) {
        if (result.timeExpression() == null || result.timeExpression() instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(result.timeExpression());
    }

    private record Row(String name, String expected, String source, QuestionMetadataApiDto metadata) {
    }

    private static Row row(String name, String expected, String source, QuestionMetadataApiDto metadata) {
        return new Row(name, expected, source, metadata);
    }

    private static QuestionMetadataApiDto meta(String... nameTypePairs) {
        var b = QuestionMetadataApiDto.builder().functionName("solve");
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            b.paramNames(List.of(nameTypePairs[i]));
            b.paramTypes(List.of(nameTypePairs[i + 1]));
        }
        return b.build();
    }

    private static QuestionMetadataApiDto meta2(String n1, String t1, String n2, String t2) {
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(List.of(n1, n2))
                .paramTypes(List.of(t1, t2))
                .build();
    }

    private static final List<Row> ROWS = List.of(
            row("Return input value", "O(1)", "class Solution { public int solve(int n) { return n; } }", meta("n", "int")),
            row("for with no update", "UNKNOWN", "class Solution { public int solve(int n) { for (int i=0;i<n;) {} return 0; } }", meta("n", "int")),
            row("while with no update", "UNKNOWN", "class Solution { public int solve(int n) { int i=0; while(i<n){} return 0; } }", meta("n", "int")),
            row("Growing i*=2 loop", "O(log n)", "class Solution { public int solve(int n) { for (int i=1;i<n;i*=2){} return 0; } }", meta("n", "int")),
            row("Shrinking i/=2 loop", "O(log n)", "class Solution { public int solve(int n) { for (int i=n;i>0;i/=2){} return 0; } }", meta("n", "int")),
            row("Triangular dependent loops", "O(n²)", """
                    class Solution { public int solve(int[] nums) {
                      for (int i=0;i<nums.length;i++) for (int j=0;j<i;j++) {}
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Loop inside switch", "O(n)", """
                    class Solution { public int solve(int[] nums) {
                      switch (0) { default: for (int i=0;i<nums.length;i++){} }
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Loop inside try", "O(n)", """
                    class Solution { public int solve(int[] nums) {
                      try { for (int i=0;i<nums.length;i++){} } catch(Exception e){}
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Opaque if condition", "UNKNOWN", """
                    class Solution { public int solve(int n) {
                      if (external.check(n)) return 1; return 0; } }
                    """, meta("n", "int")),
            row("Unknown branch vs linear", "UNKNOWN", """
                    class Solution { public int solve(int[] nums, boolean f) {
                      if (f) external.w(nums.length); else for(int i=0;i<nums.length;i++){}
                      return 0; } }
                    """, meta2("nums", "int[]", "f", "boolean")),
            row("2-D array allocation", "O(rows*cols)", """
                    class Solution { public int solve(int[][] matrix) {
                      int rows=matrix.length, cols=matrix[0].length;
                      boolean[][] g=new boolean[rows][cols]; return 0; } }
                    """, meta("matrix", "int[][]")),
            row("Loop over second list", "O(m)", """
                    class Solution { public int solve(java.util.List a, java.util.List b) {
                      for (int i=0;i<b.size();i++){} return 0; } }
                    """, meta2("a", "java.util.List", "b", "java.util.List")),
            row("Local new int[n]", "O(n)", """
                    class Solution { public int solve(int n) { int[] a=new int[n]; return 0; } }
                    """, meta("n", "int")),
            row("Return required new int[n]", "O(1) space", """
                    class Solution { public int[] solve(int n) { return new int[n]; } }
                    """, meta("n", "int")),
            row("T(n-1)+O(n)", "O(n²)", """
                    class Solution { public int solve(int n) {
                      if (n<=0) return 0;
                      for (int i=0;i<n;i++){}
                      return solve(n-1);} }
                    """, meta("n", "int")),
            row("Mutual recursion", "UNKNOWN", """
                    class Solution {
                      public int solve(int n) { return n<=0?0:a(n-1); }
                      int a(int n) { return n<=0?0:b(n-1); }
                      int b(int n) { return n<=0?0:a(n-1); }
                    }
                    """, meta("n", "int")),
            row("User class Arrays", "UNKNOWN", """
                    class Arrays { static void sort(int[] x){} }
                    class Solution { public int solve(int[] a) { Arrays.sort(a); return 0; } }
                    """, meta("a", "int[]")),
            row("Sort second list", "O(m log m)", """
                    class Solution { public int solve(java.util.List a, java.util.List b) {
                      java.util.Collections.sort(b); return 0; } }
                    """, meta2("a", "java.util.List", "b", "java.util.List")),
            row("Product two helper calls", "O(n+m)", """
                    class Solution {
                      public int solve(int n,int m) { return left(n)+right(m); }
                      int left(int n){ for(int i=0;i<n;i++){} return 0;}
                      int right(int m){ for(int j=0;j<m;j++){} return 0;}
                    }
                    """, meta2("n", "int", "m", "int")),
            row("Independent rows result", "O(rows*result)", """
                    class Solution { public int solve(int rows,int result) {
                      for (int i=0;i<rows;i++) for (int j=0;j<result;j++){} return 0; } }
                    """, meta2("rows", "int", "result", "int")),
            row("Fibonacci two-call", "UNKNOWN", """
                    class Solution { public int solve(int n) {
                      if (n<=1) return n; return solve(n-1)+solve(n-2);} }
                    """, meta("n", "int")),
            row("Concrete HashMap get", "O(1)", """
                    class Solution { public int solve(java.util.HashMap<Integer,Integer> map) {
                      return map.get(1); } }
                    """, meta("map", "java.util.HashMap")),
            row("String.split initializer", "UNKNOWN", """
                    class Solution { public int solve(String s) {
                      String[] x=s.split(","); return x.length; } }
                    """, meta("s", "String")),
            row("Stream pipeline", "UNKNOWN", """
                    class Solution { public int solve(java.util.List<Integer> xs) {
                      return xs.stream().filter(x->x>0).mapToInt(x->x).sum(); } }
                    """, meta("xs", "java.util.List"))
    );
}
