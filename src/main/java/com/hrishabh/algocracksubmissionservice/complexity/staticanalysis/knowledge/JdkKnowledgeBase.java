package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class JdkKnowledgeBase {

    public static final String VERSION = "jdk21-v1";

    private final List<JdkKnowledgeEntry> entries = List.of(
            entry("java.util.Arrays.sort(int[])", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.product(ComplexityExpr.var("n"), ComplexityExpr.var("log(n)")),
                    ComplexityExpr.one(), "int[] length n", "Dual-pivot quicksort worst-case n log n"),
            entry("java.util.Arrays.sort(Object[])", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.product(ComplexityExpr.var("n"), ComplexityExpr.var("log(n)")),
                    ComplexityExpr.one(), "array length n", "TimSort worst-case n log n"),
            entry("java.util.Collections.sort(List)", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.product(ComplexityExpr.var("n"), ComplexityExpr.var("log(n)")),
                    ComplexityExpr.one(), "list size n", "List sort"),
            entry("java.util.HashMap.get", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "hash table expected O(1)", "Not strict worst-case"),
            entry("java.util.HashMap.put", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "hash table expected O(1)", "Not strict worst-case"),
            entry("java.util.HashSet.contains", "21", ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "hash table expected O(1)", "Not strict worst-case"),
            entry("java.util.PriorityQueue.offer", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.var("log(n)"), ComplexityExpr.one(), "heap size n", "Binary heap insert"),
            entry("java.util.PriorityQueue.poll", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.var("log(n)"), ComplexityExpr.one(), "heap size n", "Binary heap extract"),
            entry("java.util.StringBuilder.append", "21", ComplexityBoundBasis.AMORTIZED_ASSUMPTION,
                    ComplexityExpr.one(), ComplexityExpr.one(), "amortized append", "Dynamic array resize amortized"),
            entry("java.lang.String.length", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.one(), ComplexityExpr.one(), "", "Constant time"),
            entry("java.util.List.size", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.one(), ComplexityExpr.one(), "", "Constant time"),
            entry("java.util.Arrays.copyOf", "21", ComplexityBoundBasis.WORST_CASE,
                    ComplexityExpr.var("n"), ComplexityExpr.var("n"), "copy length n", "Allocates new array"),
            entry("java.util.stream.Stream.sorted", "21", ComplexityBoundBasis.WORST_CASE,
                    new ComplexityExpr.Unknown("stream pipeline not fully modeled"),
                    new ComplexityExpr.Unknown("stream allocation"),
                    "streams not explicitly modeled in V1", "Reduces confidence"));

    public String version() {
        return VERSION;
    }

    public Optional<JdkKnowledgeEntry> match(String scopeType, String methodName) {
        String key = (scopeType + "." + methodName).toLowerCase(Locale.ROOT);
        for (JdkKnowledgeEntry entry : entries) {
            String pattern = entry.pattern().toLowerCase(Locale.ROOT);
            if (key.endsWith(shortPattern(pattern))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static String shortPattern(String pattern) {
        int idx = pattern.lastIndexOf('.');
        return idx >= 0 ? pattern.substring(idx + 1) : pattern;
    }

    private static JdkKnowledgeEntry entry(
            String pattern,
            String jdk,
            ComplexityBoundBasis basis,
            ComplexityExpr time,
            ComplexityExpr alloc,
            String assumptions,
            String note) {
        return new JdkKnowledgeEntry(pattern, jdk, basis, time, alloc, assumptions, note);
    }
}
