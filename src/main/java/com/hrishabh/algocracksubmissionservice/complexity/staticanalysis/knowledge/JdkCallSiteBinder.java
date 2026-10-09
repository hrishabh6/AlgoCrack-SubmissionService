package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Substitutes JDK template roles at call sites (Batch 3).
 */
public final class JdkCallSiteBinder {

    public enum FailureKind {
        NONE,
        CARDINALITY_UNRESOLVED,
        SUBSTITUTION_FAILED,
        CALLBACK_UNRESOLVED
    }

    public record BindResult(
            boolean success,
            FailureKind failureKind,
            ComplexityExpr time,
            ComplexityExpr allocation) {

        public static BindResult failed(FailureKind kind) {
            return new BindResult(false, kind, new ComplexityExpr.Unknown("jdk bind failed"), ComplexityExpr.one());
        }

        public static BindResult ok(ComplexityExpr time, ComplexityExpr allocation) {
            return new BindResult(true, FailureKind.NONE, time, allocation);
        }
    }

    private JdkCallSiteBinder() {
    }

    public static BindResult bind(
            JdkKnowledgeEntry entry,
            MethodCallExpr call,
            JdkCallTarget target,
            Function<Expression, Optional<ComplexityExpr>> sizeForExpression,
            Function<Expression, Optional<ComplexityExpr>> cardinalityForReceiver,
            Set<String> documentedCallerSymbols) {
        Map<String, ComplexityExpr> bindings = new HashMap<>();
        for (JdkTemplateVariable role : entry.requiredRoles()) {
            Optional<ComplexityExpr> resolved = resolveRole(role, call, target, sizeForExpression, cardinalityForReceiver);
            if (resolved.isEmpty() || resolved.get() instanceof ComplexityExpr.Unknown) {
                return BindResult.failed(role == JdkTemplateVariable.RECEIVER_CARDINALITY
                        || role == JdkTemplateVariable.RECEIVER_SIZE
                        ? FailureKind.CARDINALITY_UNRESOLVED
                        : FailureKind.SUBSTITUTION_FAILED);
            }
            ComplexityExpr simplified = ComplexityExprSimplifier.simplify(resolved.get());
            if (!bindRoleSymbol(role, simplified, bindings, documentedCallerSymbols)) {
                return BindResult.failed(FailureKind.SUBSTITUTION_FAILED);
            }
        }
        ComplexityExpr time = JdkTemplateRenamer.rename(entry.timeExpression(), bindings);
        ComplexityExpr alloc = JdkTemplateRenamer.rename(entry.allocationExpression(), bindings);
        time = ComplexityExprSimplifier.simplify(time);
        alloc = ComplexityExprSimplifier.simplify(alloc);
        if (!ComplexityExprSimplifier.allVariablesDocumented(time, documentedCallerSymbols)) {
            return BindResult.failed(FailureKind.SUBSTITUTION_FAILED);
        }
        return BindResult.ok(time, alloc);
    }

    private static boolean bindRoleSymbol(
            JdkTemplateVariable role,
            ComplexityExpr value,
            Map<String, ComplexityExpr> bindings,
            Set<String> documentedCallerSymbols) {
        if (value instanceof ComplexityExpr.Variable v) {
            if (!documentedCallerSymbols.contains(v.name())) {
                return false;
            }
            bindings.put(role.symbol(), v);
            return true;
        }
        if (value instanceof ComplexityExpr.Constant c) {
            bindings.put(role.symbol(), c);
            return true;
        }
        if (value instanceof ComplexityExpr.Product p) {
            bindings.put(role.symbol(), p);
            return p.factors().stream()
                    .filter(f -> f instanceof ComplexityExpr.Variable var && !documentedCallerSymbols.contains(var.name()))
                    .findAny()
                    .isEmpty();
        }
        return false;
    }

    private static Optional<ComplexityExpr> resolveRole(
            JdkTemplateVariable role,
            MethodCallExpr call,
            JdkCallTarget target,
            Function<Expression, Optional<ComplexityExpr>> sizeForExpression,
            Function<Expression, Optional<ComplexityExpr>> cardinalityForReceiver) {
        return switch (role) {
            case RECEIVER_SIZE -> call.getScope().flatMap(sizeForExpression::apply);
            case RECEIVER_CARDINALITY -> call.getScope().flatMap(cardinalityForReceiver::apply);
            case ARG0_SIZE -> call.getArguments().isEmpty()
                    ? Optional.empty()
                    : sizeForExpression.apply(call.getArgument(0));
            case ARG1_SIZE -> call.getArguments().size() < 2
                    ? Optional.empty()
                    : sizeForExpression.apply(call.getArgument(1));
            case ARG2_SIZE -> call.getArguments().size() < 3
                    ? Optional.empty()
                    : sizeForExpression.apply(call.getArgument(2));
            case ARG3_SIZE -> call.getArguments().size() < 4
                    ? Optional.empty()
                    : sizeForExpression.apply(call.getArgument(3));
            case ARG4_SIZE -> call.getArguments().size() < 5
                    ? Optional.empty()
                    : sizeForExpression.apply(call.getArgument(4));
            case RANGE_LENGTH -> resolveRangeLength(call, sizeForExpression);
            case RESULT_SIZE -> Optional.empty();
        };
    }

    private static Optional<ComplexityExpr> resolveRangeLength(
            MethodCallExpr call,
            Function<Expression, Optional<ComplexityExpr>> sizeForExpression) {
        if (call.getArguments().size() < 3) {
            return Optional.empty();
        }
        return sizeForExpression.apply(call.getArgument(2));
    }

    public static EnumSet<JdkTemplateVariable> roles(JdkTemplateVariable... vars) {
        return EnumSet.copyOf(java.util.Arrays.asList(vars));
    }
}
