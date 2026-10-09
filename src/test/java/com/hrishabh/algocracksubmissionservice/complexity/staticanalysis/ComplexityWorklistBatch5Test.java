package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 5 structural worklist regressions.
 */
class ComplexityWorklistBatch5Test {

    private static JavaStaticAnalyzer analyzer;

    @BeforeAll
    static void init() {
        analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());
    }

    @Test
    void booleanArrayQueueIsLinear() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      int next = x + 1;
                      if (next < n && !seen[next]) {
                        seen[next] = true;
                        q.offer(next);
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("O(n)", time(result));
        assertEquals("O(n)", space(result));
        assertEquals(ComplexityBoundBasis.AMORTIZED_ASSUMPTION, result.timeBoundBasis());
    }

    @Test
    void gridQueueIsRectangular() {
        StaticAnalysisResult result = analyze(gridQueue("seen", "q", 4), meta("matrix", "int[][]"));
        assertEquals("O(n * m)", time(result));
        assertEquals("O(n * m)", space(result));
    }

    @Test
    void gridPriorityQueueIsRectangularLog() {
        StaticAnalysisResult result = analyze(gridHeap("seen", "heap"), meta("matrix", "int[][]"));
        assertEquals("O(n * m * log(n * m))", time(result));
        assertEquals("O(n * m)", space(result));
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void eightDirectionsStayConstantPerState() {
        StaticAnalysisResult result = analyze(gridQueue("mark", "frontier", 8), meta("matrix", "int[][]"));
        assertEquals("O(n * m)", time(result));
        assertEquals("O(n * m)", space(result));
    }

    @Test
    void renamedLocalsDoNotChangeTheBound() {
        StaticAnalysisResult named = analyze(gridQueue("seen", "q", 4), meta("matrix", "int[][]"));
        StaticAnalysisResult renamed = analyze(gridQueue("mark", "frontier", 4), meta("matrix", "int[][]"));
        assertEquals(time(named), time(renamed));
        assertEquals(space(named), space(renamed));
    }

    @Test
    void countedBoundarySeedsRemainRectangular() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[][] matrix) {
                    int rows = matrix.length;
                    int cols = matrix[0].length;
                    boolean[][] seen = new boolean[rows][cols];
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    for (int c = 0; c < cols; c++) {
                      q.offer(new int[]{0, c});
                      seen[0][c] = true;
                    }
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    while (!q.isEmpty()) {
                      int[] cur = q.poll();
                      for (int[] d : dirs) {
                        int nr = cur[0] + d[0];
                        int nc = cur[1] + d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !seen[nr][nc]) {
                          seen[nr][nc] = true;
                          q.offer(new int[]{nr, nc});
                        }
                      }
                    }
                    return 0;
                  }
                }
                """, meta("matrix", "int[][]"));
        assertEquals("O(n * m)", time(result));
        assertEquals("O(n * m)", space(result));
    }

    @Test
    void symbolicInnerLoopIsPreserved() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int steps) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      for (int j = 0; j < steps; j++) {
                        int next = x + 1;
                        if (next < n && !seen[next]) {
                          seen[next] = true;
                          q.offer(next);
                        }
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int", "steps", "int"));
        assertEquals("O(n * steps)", time(result));
    }

    @Test
    void queueWithoutAdmissionStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int[][] grid) {
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    q.offer(new int[]{0, 0});
                    while (!q.isEmpty()) {
                      int[] cell = q.poll();
                      q.offer(new int[]{cell[0] + 1, cell[1]});
                    }
                    return 0;
                  }
                }
                """, meta("grid", "int[][]"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.WORKLIST_DUPLICATE_ADMISSION_POSSIBLE));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void markAfterDequeueStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      if (seen[x]) continue;
                      seen[x] = true;
                      q.offer(x + 1);
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.WORKLIST_DUPLICATE_ADMISSION_POSSIBLE));
    }

    @Test
    void markerResetStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      if (!seen[x]) {
                        seen[x] = true;
                        q.offer(x);
                      }
                      seen[x] = false;
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.VISITED_MARKER_NOT_MONOTONIC));
    }

    @Test
    void mismatchedMarkerAndEnqueueStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, int m) {
                    boolean[][] seen = new boolean[n][m];
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    q.offer(new int[]{0, 0});
                    while (!q.isEmpty()) {
                      int[] cur = q.poll();
                      int nr = cur[0] + 1;
                      int nc = cur[1];
                      int other = 0;
                      if (!seen[nr][nc]) {
                        seen[nr][nc] = true;
                        q.offer(new int[]{other, other});
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int", "m", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.ADMISSION_STATE_IDENTITY_UNRESOLVED));
    }

    @Test
    void opaqueQueueMutationStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      external.touch(q);
                      if (!seen[x]) {
                        seen[x] = true;
                        q.offer(x);
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.WORKLIST_MUTATION_UNRESOLVED));
    }

    @Test
    void dynamicNeighborCollectionStaysUnknown() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n, java.util.List<java.util.List<Integer>> adj) {
                    boolean[] seen = new boolean[n];
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      int u = q.poll();
                      for (Integer nxt : adj.get(u)) {
                        if (!seen[nxt]) {
                          seen[nxt] = true;
                          q.offer(nxt);
                        }
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int", "adj", "java.util.List"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.NEIGHBOR_BOUND_UNRESOLVED)
                || result.reasonCodes().contains(StaticAnalysisReasonCode.GRAPH_EDGE_SUM_UNPROVEN));
    }

    @Test
    void stringCompareToIsNotConstantTime() {
        StaticAnalysisResult result = analyze("""
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.PriorityQueue<String> heap =
                        new java.util.PriorityQueue<>((a, b) -> a.compareTo(b));
                    heap.offer(0);
                    while (!heap.isEmpty()) {
                      int x = heap.poll();
                      int next = x + 1;
                      if (next < n && !seen[next]) {
                        seen[next] = true;
                        heap.offer(next);
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED));
        assertNotEquals(ComplexityConfidence.HIGH, result.timeConfidence());
        assertEquals("O(n)", space(result));
    }

    @Test
    void userCompareToIsNotConstantTime() {
        StaticAnalysisResult result = analyze("""
                class Item implements java.lang.Comparable<Item> {
                  int n;
                  public int compareTo(Item other) {
                    for (int i = 0; i < n; i++) {}
                    return n - other.n;
                  }
                }
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.PriorityQueue<Item> heap =
                        new java.util.PriorityQueue<>((Item a, Item b) -> a.compareTo(b));
                    heap.offer(0);
                    while (!heap.isEmpty()) {
                      int x = heap.poll();
                      int next = x + 1;
                      if (next < n && !seen[next]) {
                        seen[next] = true;
                        heap.offer(next);
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED));
        assertEquals("O(n)", space(result));
    }

    @Test
    void integerCompareOfFieldsStaysConstant() {
        StaticAnalysisResult result = analyze(gridHeap("seen", "heap"), meta("matrix", "int[][]"));
        assertEquals("O(n * m * log(n * m))", time(result));
        assertEquals(ComplexityConfidence.HIGH, result.timeConfidence());
    }

    @Test
    void unresolvedComparatorKeepsTimeUnknown() {
        StaticAnalysisResult result = analyze("""
                class Cell { int h; Cell(int h){ this.h = h; } }
                class Solution {
                  public int solve(int n) {
                    boolean[] seen = new boolean[n];
                    java.util.PriorityQueue<Cell> heap = new java.util.PriorityQueue<>();
                    heap.offer(new Cell(0));
                    while (!heap.isEmpty()) {
                      Cell cur = heap.poll();
                      int next = cur.h + 1;
                      if (next < n && !seen[next]) {
                        seen[next] = true;
                        heap.offer(new Cell(next));
                      }
                    }
                    return 0;
                  }
                }
                """, meta("n", "int"));
        assertEquals("UNKNOWN", time(result));
        assertTrue(result.reasonCodes().contains(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED));
        assertEquals("O(n)", space(result));
    }

    private static String gridQueue(String marker, String queue, int degree) {
        StringBuilder dirs = new StringBuilder();
        for (int i = 0; i < degree; i++) {
            if (i > 0) {
                dirs.append(", ");
            }
            dirs.append("{").append(i).append(", 0}");
        }
        return """
                class Solution {
                  public int solve(int[][] matrix) {
                    int rows = matrix.length;
                    int cols = matrix[0].length;
                    boolean[][] %s = new boolean[rows][cols];
                    java.util.ArrayDeque<int[]> %s = new java.util.ArrayDeque<>();
                    int[][] dirs = {%s};
                    %s.offer(new int[]{0, 0});
                    while (!%s.isEmpty()) {
                      int[] cur = %s.poll();
                      for (int[] d : dirs) {
                        int nr = cur[0] + d[0];
                        int nc = cur[1] + d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !%s[nr][nc]) {
                          %s[nr][nc] = true;
                          %s.offer(new int[]{nr, nc});
                        }
                      }
                    }
                    return 0;
                  }
                }
                """.formatted(marker, queue, dirs, queue, queue, queue, marker, marker, queue);
    }

    private static String gridHeap(String marker, String heap) {
        return """
                class Cell { int r, c, h; Cell(int r, int c, int h) { this.r = r; this.c = c; this.h = h; } }
                class Solution {
                  public int solve(int[][] matrix) {
                    int rows = matrix.length;
                    int cols = matrix[0].length;
                    boolean[][] %s = new boolean[rows][cols];
                    java.util.PriorityQueue<Cell> %s = new java.util.PriorityQueue<>((a, b) -> Integer.compare(a.h, b.h));
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    %s.offer(new Cell(0, 0, 0));
                    while (!%s.isEmpty()) {
                      Cell cur = %s.poll();
                      for (int[] d : dirs) {
                        int nr = cur.r + d[0];
                        int nc = cur.c + d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !%s[nr][nc]) {
                          %s[nr][nc] = true;
                          %s.offer(new Cell(nr, nc, 0));
                        }
                      }
                    }
                    return 0;
                  }
                }
                """.formatted(marker, heap, heap, heap, heap, marker, marker, heap);
    }

    private static String time(StaticAnalysisResult result) {
        return ComplexityExprSimplifier.toBigOString(result.timeExpression());
    }

    private static String space(StaticAnalysisResult result) {
        return ComplexityExprSimplifier.toBigOString(result.spaceExpression());
    }

    private static StaticAnalysisResult analyze(String code, QuestionMetadataApiDto metadata) {
        return analyzer.analyze(code, metadata);
    }

    private static QuestionMetadataApiDto meta(String... namesAndTypes) {
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (int i = 0; i < namesAndTypes.length; i += 2) {
            names.add(namesAndTypes[i]);
            types.add(namesAndTypes[i + 1]);
        }
        return QuestionMetadataApiDto.builder()
                .functionName("solve")
                .paramNames(names)
                .paramTypes(types)
                .build();
    }
}
