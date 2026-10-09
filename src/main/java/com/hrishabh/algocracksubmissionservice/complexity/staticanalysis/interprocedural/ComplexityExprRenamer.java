package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Substitutes complexity variable names (formal helper symbols → call-site symbols).
 */
public final class ComplexityExprRenamer {

    private ComplexityExprRenamer() {
    }

    public static ComplexityExpr rename(ComplexityExpr expr, Map<String, String> symbolMap) {
        if (expr == null || symbolMap == null || symbolMap.isEmpty()) {
            return expr;
        }
        return switch (expr) {
            case ComplexityExpr.Constant c -> c;
            case ComplexityExpr.Variable v -> {
                String mapped = symbolMap.get(v.name());
                yield mapped != null ? ComplexityExpr.var(mapped) : v;
            }
            case ComplexityExpr.Log log -> new ComplexityExpr.Log(rename(log.argument(), symbolMap));
            case ComplexityExpr.Sum sum -> {
                List<ComplexityExpr> terms = new ArrayList<>();
                for (ComplexityExpr term : sum.terms()) {
                    terms.add(rename(term, symbolMap));
                }
                yield new ComplexityExpr.Sum(terms);
            }
            case ComplexityExpr.Product product -> {
                List<ComplexityExpr> factors = new ArrayList<>();
                for (ComplexityExpr factor : product.factors()) {
                    factors.add(rename(factor, symbolMap));
                }
                yield new ComplexityExpr.Product(factors);
            }
            case ComplexityExpr.Power power -> {
                String mapped = symbolMap.getOrDefault(power.variable(), power.variable());
                yield new ComplexityExpr.Power(mapped, power.exponent());
            }
            case ComplexityExpr.Exponential exp -> {
                String mapped = symbolMap.getOrDefault(exp.variable(), exp.variable());
                yield new ComplexityExpr.Exponential(exp.base(), mapped);
            }
            case ComplexityExpr.Factorial fact -> {
                String mapped = symbolMap.getOrDefault(fact.variable(), fact.variable());
                yield new ComplexityExpr.Factorial(mapped);
            }
            case ComplexityExpr.Unknown u -> u;
        };
    }
}
