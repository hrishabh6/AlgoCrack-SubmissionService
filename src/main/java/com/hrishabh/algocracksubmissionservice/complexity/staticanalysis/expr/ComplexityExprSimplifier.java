package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dominant-term simplification for sums; preserves multi-variable products where possible.
 */
public final class ComplexityExprSimplifier {

    private ComplexityExprSimplifier() {
    }

    public static ComplexityExpr simplify(ComplexityExpr expr) {
        return switch (expr) {
            case ComplexityExpr.Sum sum -> simplifySum(sum.terms());
            case ComplexityExpr.Product product -> simplifyProduct(product.factors());
            case ComplexityExpr.Log log -> new ComplexityExpr.Log(simplify(log.argument()));
            default -> expr;
        };
    }

    private static ComplexityExpr simplifySum(List<ComplexityExpr> terms) {
        List<ComplexityExpr> flattened = new ArrayList<>();
        for (ComplexityExpr term : terms) {
            ComplexityExpr simplified = simplify(term);
            if (simplified instanceof ComplexityExpr.Sum nested) {
                flattened.addAll(nested.terms());
            } else {
                flattened.add(simplified);
            }
        }
        if (flattened.stream().anyMatch(t -> t instanceof ComplexityExpr.Unknown)) {
            return new ComplexityExpr.Unknown("unresolved sum term");
        }
        if (flattened.size() == 1) {
            return flattened.getFirst();
        }
        flattened.sort(Comparator.comparingInt(ComplexityExprSimplifier::rank).reversed());
        return flattened.getFirst();
    }

    private static ComplexityExpr simplifyProduct(List<ComplexityExpr> factors) {
        List<ComplexityExpr> normalized = new ArrayList<>();
        int constant = 1;
        for (ComplexityExpr factor : factors) {
            ComplexityExpr simplified = simplify(factor);
            if (simplified instanceof ComplexityExpr.Unknown u) {
                return u;
            }
            if (simplified instanceof ComplexityExpr.Constant c) {
                constant *= c.value();
                continue;
            }
            normalized.add(simplified);
        }
        if (constant == 0) {
            return new ComplexityExpr.Constant(0);
        }
        if (normalized.isEmpty()) {
            return new ComplexityExpr.Constant(Math.max(constant, 1));
        }
        if (constant != 1) {
            normalized.add(new ComplexityExpr.Constant(constant));
        }
        if (normalized.size() == 1) {
            return normalized.getFirst();
        }
        return new ComplexityExpr.Product(normalized);
    }

    public static int rank(ComplexityExpr expr) {
        return switch (expr) {
            case ComplexityExpr.Unknown u -> -1;
            case ComplexityExpr.Factorial f -> 900;
            case ComplexityExpr.Exponential e -> 800 + e.base();
            case ComplexityExpr.Power p -> 500 + p.exponent() * 50;
            case ComplexityExpr.Product prod -> prod.factors().stream().mapToInt(ComplexityExprSimplifier::rank).sum();
            case ComplexityExpr.Log l -> 120;
            case ComplexityExpr.Variable v -> 100;
            case ComplexityExpr.Constant c -> c.value() <= 1 ? 0 : 10;
            case ComplexityExpr.Sum s -> s.terms().stream().mapToInt(ComplexityExprSimplifier::rank).max().orElse(0);
        };
    }

    public static String toExpressionString(ComplexityExpr expr) {
        return switch (expr) {
            case ComplexityExpr.Constant c -> String.valueOf(c.value());
            case ComplexityExpr.Variable v -> v.name();
            case ComplexityExpr.Log l -> "log(" + toExpressionString(l.argument()) + ")";
            case ComplexityExpr.Sum s -> {
                Set<String> parts = new LinkedHashSet<>();
                for (ComplexityExpr term : s.terms()) {
                    parts.add(toExpressionString(term));
                }
                yield String.join(" + ", parts);
            }
            case ComplexityExpr.Product p -> p.factors().stream()
                    .map(ComplexityExprSimplifier::toExpressionString)
                    .reduce((a, b) -> a + " * " + b)
                    .orElse("1");
            case ComplexityExpr.Power pow -> pow.variable() + "^" + pow.exponent();
            case ComplexityExpr.Exponential exp -> exp.base() + "^" + exp.variable();
            case ComplexityExpr.Factorial f -> f.variable() + "!";
            case ComplexityExpr.Unknown u -> "UNKNOWN";
        };
    }

    public static String toBigOString(ComplexityExpr expr) {
        ComplexityExpr simplified = simplify(expr);
        if (simplified instanceof ComplexityExpr.Unknown) {
            return "UNKNOWN";
        }
        if (simplified instanceof ComplexityExpr.Constant c && c.value() <= 1) {
            return "O(1)";
        }
        return "O(" + toExpressionString(simplified) + ")";
    }
}
