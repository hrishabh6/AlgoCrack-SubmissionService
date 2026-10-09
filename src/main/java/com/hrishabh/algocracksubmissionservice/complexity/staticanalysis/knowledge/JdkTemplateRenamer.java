package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class JdkTemplateRenamer {

    private JdkTemplateRenamer() {
    }

    static ComplexityExpr rename(ComplexityExpr template, Map<String, ComplexityExpr> bindings) {
        if (template == null || bindings == null || bindings.isEmpty()) {
            return template;
        }
        return switch (template) {
            case ComplexityExpr.Constant c -> c;
            case ComplexityExpr.Variable v -> bindings.getOrDefault(v.name(), v);
            case ComplexityExpr.Log log -> new ComplexityExpr.Log(rename(log.argument(), bindings));
            case ComplexityExpr.Sum sum -> {
                List<ComplexityExpr> terms = new ArrayList<>();
                for (ComplexityExpr term : sum.terms()) {
                    terms.add(rename(term, bindings));
                }
                yield new ComplexityExpr.Sum(terms);
            }
            case ComplexityExpr.Product product -> {
                List<ComplexityExpr> factors = new ArrayList<>();
                for (ComplexityExpr factor : product.factors()) {
                    factors.add(rename(factor, bindings));
                }
                yield new ComplexityExpr.Product(factors);
            }
            case ComplexityExpr.Power power -> {
                ComplexityExpr mapped = bindings.get(power.variable());
                if (mapped instanceof ComplexityExpr.Variable var) {
                    yield new ComplexityExpr.Power(var.name(), power.exponent());
                }
                yield template;
            }
            case ComplexityExpr.Exponential exp -> {
                if (bindings.get(exp.variable()) instanceof ComplexityExpr.Variable var) {
                    yield new ComplexityExpr.Exponential(exp.base(), var.name());
                }
                yield template;
            }
            case ComplexityExpr.Factorial fact -> {
                if (bindings.get(fact.variable()) instanceof ComplexityExpr.Variable var) {
                    yield new ComplexityExpr.Factorial(var.name());
                }
                yield template;
            }
            case ComplexityExpr.Unknown u -> u;
        };
    }
}
