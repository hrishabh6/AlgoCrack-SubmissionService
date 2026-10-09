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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Permanent replay of audit Section 12 (36 cases). Classification logic lives here only.
 */
class ComplexityAdversarialCorpus36MatrixTest {

    private static JavaStaticAnalyzer analyzer;

    @BeforeAll
    static void init() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void corpusHas36Cases() {
        assertEquals(36, ROWS.size(), "audit Section 12 corpus size");
    }

    @Test
    void classifierTreatsEquivalentSumBoundsAsMatching() {
        assertEquals(true, matchesExpectedBigO("O(n+m)", "O(n + m)"));
        assertEquals(true, matchesExpectedBigO("O(n+m)", "O(n+m)"));
    }

    @Test
    void corpusNeverCrashesAndPrintsMatrix() {
        System.out.println("CASE|AUDIT_EXPECTED|POST_TIME|POST_SPACE|POST_CONF|DISPOSITION|LATER_BATCH");
        int correct = 0, safe = 0, wrong = 0, crash = 0;
        List<String> highWrong = new ArrayList<>();
        for (Row row : ROWS) {
            String postTime;
            String postSpace;
            ComplexityConfidence conf = null;
            ComplexityResultKind kind = null;
            StaticAnalysisResult result;
            try {
                result = assertDoesNotThrow(() -> analyzer.analyze(row.source(), row.metadata()));
                postTime = bigOTime(result);
                postSpace = bigOSpace(result);
                conf = result.timeConfidence();
                kind = result.resultKind();
            } catch (Throwable t) {
                crash++;
                System.out.println(row.name() + "|" + row.auditExpected() + "|CRASH|CRASH||CRASH|P0");
                continue;
            }
            String disposition = classify(row, result, postTime, postSpace, conf, kind);
            switch (disposition) {
                case "CORRECT_CONCRETE" -> correct++;
                case "SAFE_UNKNOWN" -> safe++;
                case "STILL_WRONG" -> {
                    wrong++;
                    if (isStillWrongHighConfidence(result, postTime, postSpace, row, disposition)) {
                        highWrong.add(row.name() + " -> time=" + postTime + " space=" + postSpace
                                + " timeConf=" + conf + " spaceConf=" + result.spaceConfidence()
                                + " (" + row.laterBatch() + ")");
                    }
                }
                default -> {
                }
            }
            System.out.println(row.name() + "|" + row.auditExpected() + "|" + postTime + "|" + postSpace + "|"
                    + conf + "|" + disposition + "|" + row.laterBatch());
        }
        System.out.println("SUMMARY CORRECT_CONCRETE=" + correct + " SAFE_UNKNOWN=" + safe + " STILL_WRONG=" + wrong
                + " CRASH=" + crash);
        System.out.println("STILL_WRONG_HIGH_COUNT=" + highWrong.size());
        for (String line : highWrong) {
            System.out.println("STILL_WRONG_HIGH|" + line);
        }
        assertEquals(0, crash, "Batch 0: analyzer must not crash on adversarial corpus");
    }

    /** Reporting helper: STILL_WRONG with HIGH (or missing limitation) — not used to change disposition. */
    private static boolean isStillWrongHighConfidence(
            StaticAnalysisResult result,
            String postTime,
            String postSpace,
            Row row,
            String disposition) {
        if (!"STILL_WRONG".equals(disposition)) {
            return false;
        }
        if (row.metric() == Metric.LIMITATION) {
            return false;
        }
        boolean highTime = result.resultKind() == ComplexityResultKind.STATIC_ONLY
                && result.timeConfidence() == ComplexityConfidence.HIGH
                && !"UNKNOWN".equals(postTime);
        boolean highSpace = result.resultKind() == ComplexityResultKind.STATIC_ONLY
                && result.spaceConfidence() == ComplexityConfidence.HIGH
                && !"UNKNOWN".equals(postSpace);
        return highTime || highSpace;
    }

    static String classify(
            Row row,
            StaticAnalysisResult result,
            String postTime,
            String postSpace,
            ComplexityConfidence conf,
            ComplexityResultKind kind) {
        return switch (row.metric()) {
            case TIME -> classifyComplexity(row.auditExpected(), postTime, conf, kind, true);
            case SPACE -> classifyComplexity(row.auditExpected(), postSpace, result.spaceConfidence(), kind, false);
            case TIME_AND_SPACE -> classifyTimeAndSpace(row.auditExpected(), postTime, postSpace, conf, kind);
            case LIMITATION -> result.limitations().stream().anyMatch(l -> l.contains("MUTABLE_STATIC"))
                    || result.findings().stream().anyMatch(f -> "MUTABLE_STATIC".equals(f.category()))
                    ? "CORRECT_CONCRETE"
                    : "STILL_WRONG";
            case QUALITATIVE_OR_UNKNOWN -> classifyQualitative(row.auditExpected(), postTime, postSpace, conf, kind);
        };
    }

    private static String classifyTimeAndSpace(
            String auditExpected, String postTime, String postSpace, ComplexityConfidence conf, ComplexityResultKind kind) {
        if (!auditExpected.toLowerCase(Locale.ROOT).contains("o(n)")) {
            return classifyComplexity(auditExpected, postTime, conf, kind, true);
        }
        boolean timeOk = bigONormalized(postTime).equals("O(n)") || matchesExpectedBigO("O(n)", postTime);
        boolean spaceOk = bigONormalized(postSpace).equals("O(n)") || matchesExpectedBigO("O(n)", postSpace);
        if (timeOk && spaceOk) {
            return "CORRECT_CONCRETE";
        }
        if ("UNKNOWN".equals(postTime) && "UNKNOWN".equals(postSpace)) {
            return "SAFE_UNKNOWN";
        }
        if (kind == ComplexityResultKind.INCONCLUSIVE || kind == ComplexityResultKind.UNSUPPORTED) {
            return "SAFE_UNKNOWN";
        }
        if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY && (!timeOk || !spaceOk)) {
            return "STILL_WRONG";
        }
        if (!timeOk || !spaceOk) {
            return "STILL_WRONG";
        }
        return "CORRECT_CONCRETE";
    }

    private static String classifyQualitative(
            String auditExpected, String postTime, String postSpace, ComplexityConfidence conf, ComplexityResultKind kind) {
        String lower = auditExpected.toLowerCase(Locale.ROOT);
        if (lower.contains("unknown")) {
            if ("UNKNOWN".equals(postTime) || kind == ComplexityResultKind.INCONCLUSIVE
                    || kind == ComplexityResultKind.UNSUPPORTED) {
                return "SAFE_UNKNOWN";
            }
        }
        if (lower.contains("logarithmic") || lower.contains("log heap") || lower.contains("heap-size")) {
            if ("UNKNOWN".equals(postTime)) {
                return "SAFE_UNKNOWN";
            }
            if (postTime.toLowerCase(Locale.ROOT).contains("log") && conf != ComplexityConfidence.HIGH) {
                return "SAFE_UNKNOWN";
            }
            if (postTime.toLowerCase(Locale.ROOT).contains("log(n)") && kind == ComplexityResultKind.STATIC_ONLY
                    && conf == ComplexityConfidence.HIGH) {
                return "STILL_WRONG";
            }
            if (matchesExpectedBigO(extractPrimaryBigO(auditExpected), postTime)) {
                return "CORRECT_CONCRETE";
            }
        }
        if (lower.contains("resolve signature") || lower.contains("user call")) {
            if ("UNKNOWN".equals(postTime) || kind != ComplexityResultKind.STATIC_ONLY) {
                return "SAFE_UNKNOWN";
            }
            if (conf == ComplexityConfidence.HIGH) {
                return "STILL_WRONG";
            }
        }
        if (lower.contains("linear") && lower.contains("unknown")) {
            if ("UNKNOWN".equals(postTime) || "UNKNOWN".equals(postSpace)) {
                return "SAFE_UNKNOWN";
            }
        }
        if (matchesExpectedBigO(extractPrimaryBigO(auditExpected), postTime)) {
            return "CORRECT_CONCRETE";
        }
        if ("UNKNOWN".equals(postTime)) {
            return "SAFE_UNKNOWN";
        }
        if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY) {
            return "STILL_WRONG";
        }
        return "STILL_WRONG";
    }

    private static String classifyComplexity(
            String auditExpected, String post, ComplexityConfidence conf, ComplexityResultKind kind, boolean timeAxis) {
        String expectedBigO = extractPrimaryBigO(auditExpected);
        if (auditExpected.toLowerCase(Locale.ROOT).contains("unknown")
                || auditExpected.toLowerCase(Locale.ROOT).contains("non-terminating")) {
            if ("UNKNOWN".equals(post) || kind == ComplexityResultKind.INCONCLUSIVE
                    || kind == ComplexityResultKind.UNSUPPORTED) {
                return "SAFE_UNKNOWN";
            }
            if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY) {
                return "STILL_WRONG";
            }
            return "STILL_WRONG";
        }
        if (expectedBigO != null && matchesExpectedBigO(expectedBigO, post)) {
            return "CORRECT_CONCRETE";
        }
        if ("UNKNOWN".equals(post)) {
            if (auditExpected.toLowerCase(Locale.ROOT).contains("or unknown")
                    || auditExpected.toLowerCase(Locale.ROOT).contains("acceptable")) {
                return "SAFE_UNKNOWN";
            }
            return "SAFE_UNKNOWN";
        }
        if (conf == ComplexityConfidence.HIGH && kind == ComplexityResultKind.STATIC_ONLY) {
            return "STILL_WRONG";
        }
        return "STILL_WRONG";
    }

    private static String extractPrimaryBigO(String auditExpected) {
        if (auditExpected == null) {
            return null;
        }
        String s = auditExpected.replace('²', '2').replace("×", "*");
        int idx = s.indexOf("O(");
        if (idx < 0) {
            return null;
        }
        int end = s.indexOf(')', idx);
        if (end < 0) {
            return null;
        }
        return "O(" + s.substring(idx + 2, end).replace(" ", "") + ")";
    }

    static boolean matchesExpectedBigO(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if ("UNKNOWN".equals(actual)) {
            return false;
        }
        return bigONormalized(expected).equals(bigONormalized(actual));
    }

    static String bigONormalized(String bigO) {
        if (bigO == null || bigO.isBlank()) {
            return "UNKNOWN";
        }
        String s = bigO.trim().replace("×", "*").replace(" ", "");
        if (s.equalsIgnoreCase("UNKNOWN")) {
            return "UNKNOWN";
        }
        if (s.startsWith("O(") && s.endsWith(")")) {
            String inner = s.substring(2, s.length() - 1);
            inner = inner.replace("n2", "n²").replace("n3", "n³");
            inner = inner.replace("*log(n)", "*log(n)").replace("log(n)", "log(n)");
            return "O(" + inner + ")";
        }
        return s;
    }

    private static String bigOTime(StaticAnalysisResult result) {
        if (result.timeExpression() == null || result.timeExpression() instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(result.timeExpression());
    }

    private static String bigOSpace(StaticAnalysisResult result) {
        if (result.spaceExpression() == null || result.spaceExpression() instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        return ComplexityExprSimplifier.toBigOString(result.spaceExpression());
    }

    enum Metric {
        TIME,
        SPACE,
        TIME_AND_SPACE,
        LIMITATION,
        QUALITATIVE_OR_UNKNOWN
    }

    record Row(
            String name,
            String auditExpected,
            Metric metric,
            String laterBatch,
            String source,
            QuestionMetadataApiDto metadata) {
    }

    private static Row row(String name, String auditExpected, Metric metric, String batch, String source, QuestionMetadataApiDto metadata) {
        return new Row(name, auditExpected, metric, batch, source, metadata);
    }

    private static QuestionMetadataApiDto meta(String... nameTypePairs) {
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            names.add(nameTypePairs[i]);
            types.add(nameTypePairs[i + 1]);
        }
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(names)
                .paramTypes(types)
                .build();
    }

    /** Audit Section 12 order (36 rows). */
    private static final List<Row> ROWS = List.of(
            row("Return input value", "Time O(1)", Metric.TIME, "—", """
                    class Solution { public int solve(int n) { return n; } }
                    """, meta("n", "int")),
            row("for with no update", "Unknown/non-terminating", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int n) { for (int i=0;i<n;) {} return 0; } }
                    """, meta("n", "int")),
            row("while with no update", "Unknown/non-terminating", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int n) { int i=0; while(i<n){} return 0; } }
                    """, meta("n", "int")),
            row("Growing i*=2 loop", "O(log n)", Metric.TIME, "Loop batch", """
                    class Solution { public int solve(int n) { for (int i=1;i<n;i*=2){} return 0; } }
                    """, meta("n", "int")),
            row("Shrinking i/=2 loop", "O(log n)", Metric.TIME, "Loop batch", """
                    class Solution { public int solve(int n) { for (int i=n;i>0;i/=2){} return 0; } }
                    """, meta("n", "int")),
            row("Triangular dependent loops", "O(n²)", Metric.TIME, "Loop batch", """
                    class Solution { public int solve(int[] nums) {
                      for (int i=0;i<nums.length;i++) for (int j=0;j<i;j++) {}
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Loop inside switch", "O(n)", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int[] nums) {
                      switch (0) { default: for (int i=0;i<nums.length;i++){} }
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Loop inside try", "O(n)", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int[] nums) {
                      try { for (int i=0;i<nums.length;i++){} } catch(Exception e){}
                      return 0; } }
                    """, meta("nums", "int[]")),
            row("Opaque call in if condition", "Unknown", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int n) {
                      if (external.check(n)) return 1; return 0; } }
                    """, meta("n", "int")),
            row("Opaque call in initializer", "Unknown", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int n) {
                      int x = external.work(n); return x; } }
                    """, meta("n", "int")),
            row("Opaque call in assignment", "Unknown", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int n) {
                      int x = 0; x = external.work(n); return x; } }
                    """, meta("n", "int")),
            row("Unknown branch vs linear branch", "Unknown", Metric.TIME, "Batch0", """
                    class Solution { public int solve(int[] nums, boolean f) {
                      if (f) external.w(nums.length); else for(int i=0;i<nums.length;i++){}
                      return 0; } }
                    """, meta("nums", "int[]", "f", "boolean")),
            row("2-D array allocation", "Space O(rows*cols)", Metric.SPACE, "P1 symbols", """
                    class Solution { public int solve(int[][] matrix) {
                      int rows=matrix.length, cols=matrix[0].length;
                      boolean[][] g=new boolean[rows][cols]; return 0; } }
                    """, meta("matrix", "int[][]")),
            row("Loop over second list", "O(m)", Metric.TIME, "P1 symbols", """
                    class Solution { public int solve(java.util.List a, java.util.List b) {
                      for (int i=0;i<b.size();i++){} return 0; } }
                    """, meta("a", "java.util.List", "b", "java.util.List")),
            row("Local new int[n]", "Time/space O(n)", Metric.TIME_AND_SPACE, "P2 space", """
                    class Solution { public int solve(int n) { int[] a=new int[n]; return 0; } }
                    """, meta("n", "int")),
            row("Return required new int[n]", "Auxiliary O(1)", Metric.SPACE, "P2 space", """
                    class Solution { public int[] solve(int n) { return new int[n]; } }
                    """, meta("n", "int")),
            row("Helper allocates returned local", "Space O(n)", Metric.SPACE, "P2 space", """
                    class Solution {
                      public int solve(int n) { int[] a = make(n); return a.length; }
                      int[] make(int n) { return new int[n]; }
                    }
                    """, meta("n", "int")),
            row("Local list grows to n", "Space O(n)", Metric.SPACE, "P2 space", """
                    class Solution { public int solve(int n) {
                      java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
                      for (int i=0;i<n;i++) { list.add(i); }
                      return list.size(); } }
                    """, meta("n", "int")),
            row("Arrays.copyOf initializer", "Time/space O(n)", Metric.TIME_AND_SPACE, "P2 JDK/space", """
                    class Solution { public int solve(int[] a) {
                      int[] b = java.util.Arrays.copyOf(a, a.length); return b.length; } }
                    """, meta("a", "int[]")),
            row("T(n-1)+O(n)", "Time O(n²), stack O(n)", Metric.TIME, "P1 recurrence", """
                    class Solution { public int solve(int n) {
                      if (n<=0) return 0;
                      for (int i=0;i<n;i++){}
                      return solve(n-1);} }
                    """, meta("n", "int")),
            row("Halving recursion with complex sibling work", "O(n log n) in fixture intent", Metric.QUALITATIVE_OR_UNKNOWN, "P1 recurrence", """
                    class Solution { public int solve(int n) {
                      if (n <= 1) return 0;
                      return solve(n / 2) + n; } }
                    """, meta("n", "int")),
            row("Fibonacci two-call recursion", "Unknown acceptable", Metric.TIME, "P1 recurrence", """
                    class Solution { public int solve(int n) {
                      if (n<=1) return n; return solve(n-1)+solve(n-2);} }
                    """, meta("n", "int")),
            row("Mutual recursion", "Unknown", Metric.TIME, "Batch0", """
                    class Solution {
                      public int solve(int n) { return n<=0?0:a(n-1); }
                      int a(int n) { return n<=0?0:b(n-1); }
                      int b(int n) { return n<=0?0:a(n-1); }
                    }
                    """, meta("n", "int")),
            row("User class named Arrays", "User call/unknown", Metric.QUALITATIVE_OR_UNKNOWN, "P2 JDK", """
                    class Arrays { static void sort(int[] x){} }
                    class Solution { public int solve(int[] a) { Arrays.sort(a); return 0; } }
                    """, meta("a", "int[]")),
            row("Sort second list", "O(m log m)", Metric.TIME, "P2 JDK", """
                    class Solution { public int solve(java.util.List a, java.util.List b) {
                      java.util.Collections.sort(b); return 0; } }
                    """, meta("a", "java.util.List", "b", "java.util.List")),
            row("PriorityQueue parameter", "Heap-size logarithm", Metric.QUALITATIVE_OR_UNKNOWN, "P2 JDK", """
                    class Solution { public int solve(java.util.PriorityQueue<Integer> q) {
                      q.offer(1); return 0; } }
                    """, meta("q", "java.util.PriorityQueue")),
            row("Local PriorityQueue", "Log heap or unknown", Metric.QUALITATIVE_OR_UNKNOWN, "P2 JDK", """
                    class Solution { public int solve(int n) {
                      java.util.PriorityQueue<Integer> q = new java.util.PriorityQueue<>();
                      q.offer(n); return 0; } }
                    """, meta("n", "int")),
            row("Queue worklist", "Unknown without invariant", Metric.TIME, "P3 worklist", """
                    class Solution { public int solve(int[][] grid) {
                      java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                      q.offer(new int[]{0,0});
                      while (!q.isEmpty()) {
                        int[] cell = q.poll();
                        q.offer(new int[]{cell[0]+1, cell[1]});
                      }
                      return 0; } }
                    """, meta("grid", "int[][]")),
            row("Concrete HashMap get", "Expected O(1)", Metric.TIME, "P2 JDK", """
                    class Solution { public int solve(java.util.HashMap<Integer,Integer> map) {
                      return map.get(1); } }
                    """, meta("map", "java.util.HashMap")),
            row("TreeMap get", "Logarithmic or unknown", Metric.QUALITATIVE_OR_UNKNOWN, "P2 JDK", """
                    class Solution { public int solve(java.util.TreeMap<Integer,Integer> map) {
                      return map.get(1); } }
                    """, meta("map", "java.util.TreeMap")),
            row("String.split initializer", "Linear time/allocation or unknown", Metric.QUALITATIVE_OR_UNKNOWN, "P2 JDK", """
                    class Solution { public int solve(String s) {
                      String[] x=s.split(","); return x.length; } }
                    """, meta("s", "String")),
            row("Stream pipeline", "Unknown", Metric.TIME, "P3 streams", """
                    class Solution { public int solve(java.util.List<Integer> xs) {
                      return xs.stream().filter(x->x>0).mapToInt(x->x).sum(); } }
                    """, meta("xs", "java.util.List")),
            row("Initialized mutable static", "Dynamic-unsafe limitation", Metric.LIMITATION, "P1 static-state", """
                    class Solution {
                      static java.util.List<Integer> cache = new java.util.ArrayList<>();
                      public int solve(int n) { cache.add(n); return cache.size(); }
                    }
                    """, meta("n", "int")),
            row("Overloaded helper", "Resolve signature or unknown", Metric.QUALITATIVE_OR_UNKNOWN, "P1 interprocedural", """
                    class Solution {
                      public int solve(int n) { return helper(n); }
                      int helper(int n) { for (int i=0;i<n;i++) {} return 0; }
                      int helper(String s) { return s.length(); }
                    }
                    """, meta("n", "int")),
            row("Product of two helper calls", "O(n+m)", Metric.TIME, "P1 value/cost", """
                    class Solution {
                      public int solve(int n,int m) { return left(n)+right(m); }
                      int left(int n){ for(int i=0;i<n;i++){} return 0;}
                      int right(int m){ for(int j=0;j<m;j++){} return 0;}
                    }
                    """, meta("n", "int", "m", "int")),
            row("Independent rows result", "O(rows*result)", Metric.TIME, "P1 simplifier", """
                    class Solution { public int solve(int rows,int result) {
                      for (int i=0;i<rows;i++) for (int j=0;j<result;j++){} return 0; } }
                    """, meta("rows", "int", "result", "int"))
    );
}
