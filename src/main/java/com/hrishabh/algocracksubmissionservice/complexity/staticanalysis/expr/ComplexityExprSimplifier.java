package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dominant-term simplification for sums; preserves independent variables (n + m stays n + m).
 */
public final class ComplexityExprSimplifier {

    private static final List<String> CANONICAL_VARIABLE_ORDER = List.of("n", "m", "v", "e");

    private ComplexityExprSimplifier() {
    }

    public static ComplexityExpr simplify(ComplexityExpr expr) {
        return switch (expr) {
            case ComplexityExpr.Sum sum -> simplifySum(sum.terms());
            case ComplexityExpr.Product product -> simplifyProduct(product.factors());
            case ComplexityExpr.Log log -> {
                ComplexityExpr arg = simplify(log.argument());
                if (arg instanceof ComplexityExpr.Variable v && "n".equals(v.name())) {
                    yield new ComplexityExpr.Log(new ComplexityExpr.Variable("n"));
                }
                yield new ComplexityExpr.Log(arg);
            }
            case ComplexityExpr.Power p -> p;
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
        if (flattened.isEmpty()) {
            return ComplexityExpr.one();
        }

        List<ComplexityExpr> merged = mergeLikeTerms(flattened);
        List<ComplexityExpr> reduced = removeDominatedTerms(merged);
        reduced.sort(Comparator.comparingInt(ComplexityExprSimplifier::termOrder)
                .thenComparing(ComplexityExprSimplifier::normalizeShape));
        if (reduced.size() == 1) {
            return reduced.getFirst();
        }
        return new ComplexityExpr.Sum(reduced);
    }

    private static List<ComplexityExpr> mergeLikeTerms(List<ComplexityExpr> terms) {
        Map<String, ComplexityExpr> canonical = new HashMap<>();
        for (ComplexityExpr term : terms) {
            canonical.putIfAbsent(canonicalKey(term), term);
        }
        return new ArrayList<>(canonical.values());
    }

    private static String canonicalKey(ComplexityExpr expr) {
        return normalizeShape(expr);
    }

    public static String normalizeShape(ComplexityExpr expr) {
        ComplexityExpr normalized = simplifyProductFactors(expr);
        return toExpressionString(normalized);
    }

    private static ComplexityExpr simplifyProduct(List<ComplexityExpr> factors) {
        Map<String, Integer> variablePowers = new HashMap<>();
        int constant = 1;
        List<ComplexityExpr> nonVarFactors = new ArrayList<>();

        for (ComplexityExpr factor : simplifyFactorsFlat(factors)) {
            if (factor instanceof ComplexityExpr.Unknown u) {
                return u;
            }
            if (factor instanceof ComplexityExpr.Constant c) {
                constant *= c.value();
                continue;
            }
            if (factor instanceof ComplexityExpr.Variable v) {
                variablePowers.merge(v.name(), 1, Integer::sum);
                continue;
            }
            if (factor instanceof ComplexityExpr.Power p) {
                variablePowers.merge(p.variable(), p.exponent(), Integer::sum);
                continue;
            }
            if (factor instanceof ComplexityExpr.Log log && log.argument() instanceof ComplexityExpr.Variable v) {
                nonVarFactors.add(new ComplexityExpr.Log(new ComplexityExpr.Variable(v.name())));
                continue;
            }
            nonVarFactors.add(factor);
        }

        List<ComplexityExpr> rebuilt = new ArrayList<>();
        if (constant != 1) {
            rebuilt.add(new ComplexityExpr.Constant(constant));
        }
        variablePowers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(displayOrderComparator()))
                .forEach(entry -> {
                    if (entry.getValue() == 1) {
                        rebuilt.add(new ComplexityExpr.Variable(entry.getKey()));
                    } else {
                        rebuilt.add(new ComplexityExpr.Power(entry.getKey(), entry.getValue()));
                    }
                });
        rebuilt.addAll(nonVarFactors);

        if (rebuilt.isEmpty()) {
            return new ComplexityExpr.Constant(Math.max(constant, 1));
        }
        if (rebuilt.size() == 1) {
            return rebuilt.getFirst();
        }
        return new ComplexityExpr.Product(rebuilt);
    }

    private static List<ComplexityExpr> simplifyFactorsFlat(List<ComplexityExpr> factors) {
        List<ComplexityExpr> flat = new ArrayList<>();
        for (ComplexityExpr factor : factors) {
            ComplexityExpr simplified = simplify(factor);
            if (simplified instanceof ComplexityExpr.Product product) {
                flat.addAll(product.factors());
            } else {
                flat.add(simplified);
            }
        }
        return flat;
    }

    private static ComplexityExpr simplifyProductFactors(ComplexityExpr expr) {
        return switch (expr) {
            case ComplexityExpr.Product p -> simplifyProduct(p.factors());
            default -> simplify(expr);
        };
    }

    private static List<ComplexityExpr> removeDominatedTerms(List<ComplexityExpr> terms) {
        List<ComplexityExpr> kept = new ArrayList<>();
        for (ComplexityExpr candidate : terms) {
            boolean dominated = false;
            for (ComplexityExpr other : terms) {
                if (candidate != other && strictlyDominates(other, candidate)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /**
     * True only when {@code dominant} is asymptotically >= {@code subordinate} under shared variables.
     */
    public static boolean strictlyDominates(ComplexityExpr dominant, ComplexityExpr subordinate) {
        if (dominant.equals(subordinate)) {
            return false;
        }
        if (subordinate instanceof ComplexityExpr.Constant c && c.value() <= 1) {
            return !(dominant instanceof ComplexityExpr.Constant);
        }
        if (dominant instanceof ComplexityExpr.Constant) {
            return false;
        }
        if (subordinate instanceof ComplexityExpr.Log logSub
                && logSub.argument() instanceof ComplexityExpr.Variable vSub) {
            if (dominant instanceof ComplexityExpr.Variable vDom && vDom.name().equals(vSub.name())) {
                return true;
            }
            if (dominant instanceof ComplexityExpr.Power p && p.variable().equals(vSub.name()) && p.exponent() >= 1) {
                return true;
            }
            if (dominant instanceof ComplexityExpr.Product prod
                    && prod.factors().stream().anyMatch(f -> f instanceof ComplexityExpr.Variable vv
                    && vv.name().equals(vSub.name()))) {
                return true;
            }
        }
        OptionalTerm dom = classifyTerm(dominant);
        OptionalTerm sub = classifyTerm(subordinate);
        if (dom.kind == TermKind.CONSTANT && sub.kind != TermKind.CONSTANT) {
            return false;
        }
        if (dom.kind == TermKind.POWER && sub.kind == TermKind.POWER
                && dom.variable.equals(sub.variable) && dom.exponent > sub.exponent) {
            return true;
        }
        if (dom.kind == TermKind.VARIABLE && sub.kind == TermKind.VARIABLE
                && dom.variable.equals(sub.variable)) {
            return false;
        }
        if (dom.kind == TermKind.POWER && sub.kind == TermKind.VARIABLE
                && dom.variable.equals(sub.variable) && dom.exponent >= 1) {
            return true;
        }
        if (dom.kind == TermKind.VARIABLE && sub.kind == TermKind.LOG
                && dom.variable.equals(sub.variable)) {
            return true;
        }
        if (dom.kind == TermKind.PRODUCT && sub.kind == TermKind.PRODUCT
                && sameVariableMultiset(dom.productVars, sub.productVars)) {
            return coefficient(dom) > coefficient(sub);
        }
        if (dom.kind == TermKind.PRODUCT && sub.kind == TermKind.VARIABLE
                && dom.productVars.containsKey(sub.variable) && dom.productVars.size() > 1) {
            return true;
        }
        if (dom.kind == TermKind.PRODUCT && sub.kind == TermKind.POWER
                && dom.productVars.containsKey(sub.variable)
                && totalDegree(dom.productVars) > sub.exponent) {
            return true;
        }
        return false;
    }

    private static int totalDegree(Map<String, Integer> productVars) {
        return productVars.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int coefficient(OptionalTerm term) {
        return Math.max(term.constantFactor, 1);
    }

    private static boolean sameVariableMultiset(Map<String, Integer> a, Map<String, Integer> b) {
        return a.equals(b);
    }

    private enum TermKind { CONSTANT, VARIABLE, LOG, POWER, PRODUCT, OTHER }

    private record OptionalTerm(TermKind kind, String variable, int exponent, int constantFactor,
                                Map<String, Integer> productVars) {
    }

    private static OptionalTerm classifyTerm(ComplexityExpr expr) {
        ComplexityExpr normalized = simplify(expr);
        return switch (normalized) {
            case ComplexityExpr.Constant c -> new OptionalTerm(TermKind.CONSTANT, "", 0, c.value(), Map.of());
            case ComplexityExpr.Variable v -> new OptionalTerm(TermKind.VARIABLE, v.name(), 1, 1, Map.of(v.name(), 1));
            case ComplexityExpr.Log l when l.argument() instanceof ComplexityExpr.Variable v ->
                    new OptionalTerm(TermKind.LOG, v.name(), 0, 1, Map.of());
            case ComplexityExpr.Power p -> new OptionalTerm(TermKind.POWER, p.variable(), p.exponent(), 1, Map.of(p.variable(), p.exponent()));
            case ComplexityExpr.Product prod -> {
                Map<String, Integer> vars = new HashMap<>();
                int constant = 1;
                for (ComplexityExpr f : prod.factors()) {
                    if (f instanceof ComplexityExpr.Constant c) {
                        constant *= c.value();
                    } else if (f instanceof ComplexityExpr.Variable v) {
                        vars.merge(v.name(), 1, Integer::sum);
                    } else if (f instanceof ComplexityExpr.Power p) {
                        vars.merge(p.variable(), p.exponent(), Integer::sum);
                    }
                }
                yield new OptionalTerm(TermKind.PRODUCT, "", 0, constant, vars);
            }
            default -> new OptionalTerm(TermKind.OTHER, "", 0, 1, Map.of());
        };
    }

    /**
     * Worst-case branch composition: comparable terms use dominance; incomparable variables stay summed.
     */
    public static ComplexityExpr branchWorstCase(ComplexityExpr left, ComplexityExpr right) {
        left = simplify(left);
        right = simplify(right);
        if (left instanceof ComplexityExpr.Unknown || right instanceof ComplexityExpr.Unknown) {
            return new ComplexityExpr.Unknown("unknown possible branch");
        }
        if (structurallyEqual(left, right)) {
            return left;
        }
        if (strictlyDominates(left, right)) {
            return left;
        }
        if (strictlyDominates(right, left)) {
            return right;
        }
        if (!sharePrimaryVariable(left, right)) {
            return simplify(new ComplexityExpr.Sum(List.of(left, right)));
        }
        return simplify(new ComplexityExpr.Sum(List.of(left, right)));
    }

    private static boolean sharePrimaryVariable(ComplexityExpr left, ComplexityExpr right) {
        Set<String> leftVars = variableNames(left);
        Set<String> rightVars = variableNames(right);
        for (String v : leftVars) {
            if (rightVars.contains(v)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> variableNames(ComplexityExpr expr) {
        Set<String> names = new LinkedHashSet<>();
        collectVariableNames(expr, names);
        return names;
    }

    private static void collectVariableNames(ComplexityExpr expr, Set<String> names) {
        switch (expr) {
            case ComplexityExpr.Variable v -> names.add(v.name());
            case ComplexityExpr.Power p -> names.add(p.variable());
            case ComplexityExpr.Log l -> collectVariableNames(l.argument(), names);
            case ComplexityExpr.Product p -> p.factors().forEach(f -> collectVariableNames(f, names));
            case ComplexityExpr.Sum s -> s.terms().forEach(t -> collectVariableNames(t, names));
            default -> {
            }
        }
    }

    private static boolean structurallyEqual(ComplexityExpr a, ComplexityExpr b) {
        return normalizeShape(a).equals(normalizeShape(b));
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
        ComplexityExpr normalized = switch (expr) {
            case ComplexityExpr.Product p -> simplifyProduct(p.factors());
            case ComplexityExpr.Sum s -> simplifySum(s.terms());
            default -> expr;
        };
        return switch (normalized) {
            case ComplexityExpr.Constant c -> String.valueOf(c.value());
            case ComplexityExpr.Variable v -> v.name();
            case ComplexityExpr.Log l -> "log(" + toExpressionString(l.argument()) + ")";
            case ComplexityExpr.Sum s -> {
                List<String> parts = s.terms().stream().map(ComplexityExprSimplifier::toExpressionString).toList();
                yield String.join(" + ", parts);
            }
            case ComplexityExpr.Product p -> p.factors().stream()
                    .map(ComplexityExprSimplifier::toExpressionString)
                    .reduce((a, b) -> a + " * " + b)
                    .orElse("1");
            case ComplexityExpr.Power pow -> formatPower(pow.variable(), pow.exponent());
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

    public static boolean exprEquals(ComplexityExpr a, ComplexityExpr b) {
        return Objects.equals(normalizeShape(a), normalizeShape(b));
    }

    private static String formatPower(String variable, int exponent) {
        return switch (exponent) {
            case 2 -> variable + "²";
            case 3 -> variable + "³";
            default -> variable + "^" + exponent;
        };
    }

    private static int termOrder(ComplexityExpr expr) {
        int best = Integer.MAX_VALUE;
        for (String name : variableNames(expr)) {
            best = Math.min(best, variableOrderIndex(name));
        }
        return best;
    }

    /**
     * Total order for display/canonical sorting only — never used for semantic equality.
     */
    public static Comparator<String> displayOrderComparator() {
        return (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            int order = Integer.compare(variableOrderIndex(a), variableOrderIndex(b));
            if (order != 0) {
                return order;
            }
            return a.compareTo(b);
        };
    }

    public static java.util.Set<String> referencedVariableNames(ComplexityExpr expr) {
        return variableNames(expr);
    }

    public static boolean allVariablesDocumented(ComplexityExpr expr, java.util.Set<String> documentedSymbols) {
        for (String name : referencedVariableNames(expr)) {
            if (!documentedSymbols.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private static int variableOrderIndex(String variable) {
        int idx = CANONICAL_VARIABLE_ORDER.indexOf(variable);
        if (idx >= 0) {
            return idx;
        }
        return CANONICAL_VARIABLE_ORDER.size() + 1000 + Math.abs(variable.hashCode() % 10000);
    }
}
