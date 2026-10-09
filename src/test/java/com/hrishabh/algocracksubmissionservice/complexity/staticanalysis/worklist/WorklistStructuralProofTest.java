package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.worklist;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorklistStructuralProofTest {

    @Test
    void provesGuardedGridQueue() {
        WhileStmt loop = onlyWhile("""
                class Solution {
                  public int solve(int n, int m) {
                    boolean[][] seen = new boolean[n][m];
                    java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    q.offer(new int[]{0, 0});
                    while (!q.isEmpty()) {
                      int[] cur = q.poll();
                      for (int[] d : dirs) {
                        int nr = cur[0] + d[0];
                        int nc = cur[1] + d[1];
                        if (nr >= 0 && nr < n && nc >= 0 && nc < m && !seen[nr][nc]) {
                          seen[nr][nc] = true;
                          q.offer(new int[]{nr, nc});
                        }
                      }
                    }
                    return 0;
                  }
                }
                """);
        WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, lookup());
        assertTrue(attempt.proof().isPresent());
        assertEquals("q", attempt.proof().get().containerName());
    }

    @Test
    void rejectsQueueWithoutAdmission() {
        WhileStmt loop = onlyWhile("""
                class Solution {
                  public int solve(int n) {
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    while (!q.isEmpty()) {
                      int x = q.poll();
                      q.offer(x + 1);
                    }
                    return 0;
                  }
                }
                """);
        WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, lookup());
        assertEquals(StaticAnalysisReasonCode.WORKLIST_DUPLICATE_ADMISSION_POSSIBLE, attempt.failure());
    }

    @Test
    void continueBeforePollDoesNotProveConsumption() {
        WhileStmt loop = onlyWhile("""
                class Solution {
                  public int solve(int n, boolean flag) {
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      if (flag) {
                        continue;
                      }
                      int x = q.poll();
                    }
                    return 0;
                  }
                }
                """);
        WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, lookup());
        assertEquals(StaticAnalysisReasonCode.WORKLIST_CONSUMPTION_NOT_PROVEN, attempt.failure());
    }

    @Test
    void breakBeforePollDoesNotRequireConsumption() {
        WhileStmt loop = onlyWhile("""
                class Solution {
                  public int solve(int n, boolean flag) {
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      if (flag) {
                        break;
                      }
                      int x = q.poll();
                    }
                    return 0;
                  }
                }
                """);
        WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, lookup());
        assertEquals(StaticAnalysisReasonCode.WORKLIST_ADMISSION_NOT_BOUNDED, attempt.failure());
    }

    @Test
    void returnAndThrowAreTerminalPaths() {
        WhileStmt returned = onlyWhile("""
                class Solution {
                  public int solve(int n, boolean flag) {
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      if (flag) {
                        return 0;
                      }
                      int x = q.poll();
                    }
                    return 0;
                  }
                }
                """);
        assertEquals(StaticAnalysisReasonCode.WORKLIST_ADMISSION_NOT_BOUNDED,
                WorklistStructuralProof.attempt(returned, lookup()).failure());
        WhileStmt thrown = onlyWhile("""
                class Solution {
                  public int solve(int n, boolean flag) {
                    java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
                    q.offer(0);
                    while (!q.isEmpty()) {
                      if (flag) {
                        throw new IllegalStateException();
                      }
                      int x = q.poll();
                    }
                    return 0;
                  }
                }
                """);
        assertEquals(StaticAnalysisReasonCode.WORKLIST_ADMISSION_NOT_BOUNDED,
                WorklistStructuralProof.attempt(thrown, lookup()).failure());
    }

    @Test
    void rejectsMarkerReset() {
        WhileStmt loop = onlyWhile("""
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
                """);
        WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, lookup());
        assertEquals(StaticAnalysisReasonCode.VISITED_MARKER_NOT_MONOTONIC, attempt.failure());
    }

    private static WhileStmt onlyWhile(String source) {
        MethodDeclaration method = StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow();
        return method.findFirst(WhileStmt.class).orElseThrow();
    }

    private static WorklistStructuralProof.Lookup lookup() {
        return new WorklistStructuralProof.Lookup() {
            @Override
            public Optional<ComplexityExpr> sizeOf(Expression expression) {
                if (expression instanceof NameExpr name) {
                    return Optional.of(ComplexityExpr.var(name.getNameAsString()));
                }
                return Optional.empty();
            }

            @Override
            public Optional<String> concreteType(String localName) {
                return Optional.ofNullable(Map.of(
                        "q", "java.util.ArrayDeque",
                        "heap", "java.util.PriorityQueue").get(localName));
            }
        };
    }
}
