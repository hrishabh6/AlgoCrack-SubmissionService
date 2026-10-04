package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class JdkKnowledgeBase {

    public static final String VERSION = "jdk21-v3";

    private final List<JdkKnowledgeEntry> entries = List.of(
            entry("java.util.Arrays.sort", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.nLogN("n"), ComplexityExpr.one(), "int[] or Object[] length n", "Sort full array", 1),
            entry("java.util.Collections.sort", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.nLogN("n"), ComplexityExpr.one(), "list size n", "Sort list", 1),
            entry("java.util.HashMap.get", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "expected O(1)", "Hash map get", 1),
            entry("java.util.HashMap.put", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "expected O(1)", "Hash map put", 2),
            entry("java.util.HashSet.contains", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "expected O(1)", "Hash set contains", 1),
            entry("java.util.PriorityQueue.offer", "21", ComplexityBoundBasis.WORST_CASE,
                    new ComplexityExpr.Log(new ComplexityExpr.Variable("n")), ComplexityExpr.one(), "heap size n", "Heap insert", 1),
            entry("java.util.PriorityQueue.poll", "21", ComplexityBoundBasis.WORST_CASE,
                    new ComplexityExpr.Log(new ComplexityExpr.Variable("n")), ComplexityExpr.one(), "heap size n", "Heap extract", -1),
            entry("java.lang.String.length", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.one(), ComplexityExpr.one(), "", "Constant", 0),
            entry("java.util.Arrays.copyOf", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.var("n"), ComplexityExpr.var("n"), "copy length n", "Array copy", 2));

    public String version() {
        return VERSION;
    }

    public Optional<JdkKnowledgeEntry> matchQualified(JdkCallTarget target, String methodName, int argumentCount) {
        String key = (target.qualifiedType() + "." + methodName).toLowerCase(Locale.ROOT);
        for (JdkKnowledgeEntry entry : entries) {
            String pattern = entry.pattern().toLowerCase(Locale.ROOT);
            if (!key.equals(pattern)) {
                continue;
            }
            if (entry.requiredArgumentCount() >= 0 && entry.requiredArgumentCount() != argumentCount) {
                continue;
            }
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    private static JdkKnowledgeEntry entry(
            String pattern,
            String jdk,
            ComplexityBoundBasis basis,
            ComplexityExpr time,
            ComplexityExpr alloc,
            String assumptions,
            String note,
            int requiredArgumentCount) {
        return new JdkKnowledgeEntry(pattern, jdk, basis, time, alloc, assumptions, note, requiredArgumentCount);
    }
}
