package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.recurrence;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class RecurrenceSupport {

    public enum ArgumentPattern {
        LINEAR_DECREMENT,
        HALVE,
        UNSUPPORTED
    }

    private RecurrenceSupport() {
    }

    public static ArgumentPattern classifyArgument(Expression argument) {
        if (argument instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.MINUS
                && binary.getRight() instanceof IntegerLiteralExpr lit && "1".equals(lit.getValue())) {
            return ArgumentPattern.LINEAR_DECREMENT;
        }
        if (argument instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.DIVIDE
                && binary.getRight() instanceof IntegerLiteralExpr lit && "2".equals(lit.getValue())) {
            return ArgumentPattern.HALVE;
        }
        return ArgumentPattern.UNSUPPORTED;
    }

    public static List<MethodCallExpr> directSelfCalls(MethodDeclaration method, String methodName) {
        return method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getScope().isEmpty() && methodName.equals(call.getNameAsString()))
                .toList();
    }

    public static ComplexityExpr resolveMethodRecurrence(
            MethodDeclaration method,
            String methodName,
            String sizeVar,
            Function<Expression, ComplexityExpr> expressionAnalyzer) {
        List<MethodCallExpr> selfCalls = directSelfCalls(method, methodName);
        if (selfCalls.isEmpty()) {
            return ComplexityExpr.one();
        }
        if (selfCalls.size() != 1) {
            return new ComplexityExpr.Unknown("multiple direct recursive calls");
        }
        MethodCallExpr onlyCall = selfCalls.getFirst();
        ArgumentPattern pattern = classifyArgument(
                onlyCall.getArguments().isEmpty() ? null : onlyCall.getArgument(0));
        if (pattern == ArgumentPattern.UNSUPPORTED) {
            return new ComplexityExpr.Unknown("unsupported recursion argument");
        }
        ComplexityExpr sibling = onlyCall.findAncestor(ReturnStmt.class)
                .map(ret -> siblingWorkFromReturn(
                        ret.getExpression().orElse(null),
                        methodName,
                        expressionAnalyzer))
                .orElse(ComplexityExpr.one());
        return totalTime(pattern, sibling, sizeVar);
    }

    public static ArgumentPattern unifiedStackPattern(MethodDeclaration method, String methodName) {
        List<MethodCallExpr> selfCalls = directSelfCalls(method, methodName);
        if (selfCalls.isEmpty()) {
            return ArgumentPattern.UNSUPPORTED;
        }
        if (selfCalls.size() != 1) {
            return ArgumentPattern.UNSUPPORTED;
        }
        MethodCallExpr call = selfCalls.getFirst();
        if (call.getArguments().isEmpty()) {
            return ArgumentPattern.UNSUPPORTED;
        }
        ArgumentPattern pattern = classifyArgument(call.getArgument(0));
        return pattern == ArgumentPattern.UNSUPPORTED ? ArgumentPattern.UNSUPPORTED : pattern;
    }

    public static ComplexityExpr totalTime(ArgumentPattern pattern, ComplexityExpr siblingWorkPerLevel, String sizeVar) {
        return switch (pattern) {
            case LINEAR_DECREMENT -> ComplexityExpr.var(sizeVar);
            case HALVE -> halveRecurrenceTotal(siblingWorkPerLevel, sizeVar);
            case UNSUPPORTED -> new ComplexityExpr.Unknown("unsupported recursion");
        };
    }

    public static ComplexityExpr recursionStackDepth(ArgumentPattern pattern, String sizeVar) {
        return switch (pattern) {
            case LINEAR_DECREMENT -> ComplexityExpr.var(sizeVar);
            case HALVE -> new ComplexityExpr.Log(new ComplexityExpr.Variable(sizeVar));
            case UNSUPPORTED -> ComplexityExpr.one();
        };
    }

    private static ComplexityExpr halveRecurrenceTotal(ComplexityExpr siblingWorkPerLevel, String sizeVar) {
        ComplexityExpr simplified = ComplexityExprSimplifier.simplify(siblingWorkPerLevel);
        if (simplified instanceof ComplexityExpr.Constant c && c.value() <= 1) {
            return new ComplexityExpr.Log(new ComplexityExpr.Variable(sizeVar));
        }
        if (isLinearInVariable(simplified, sizeVar)) {
            return ComplexityExpr.var(sizeVar);
        }
        return new ComplexityExpr.Unknown("unsupported halving recurrence");
    }

    private static boolean isLinearInVariable(ComplexityExpr expr, String sizeVar) {
        if (expr instanceof ComplexityExpr.Variable v && v.name().equals(sizeVar)) {
            return true;
        }
        if (expr instanceof ComplexityExpr.Product product) {
            boolean hasVar = product.factors().stream()
                    .anyMatch(f -> f instanceof ComplexityExpr.Variable v && v.name().equals(sizeVar));
            boolean onlySimple = product.factors().stream().allMatch(f ->
                    f instanceof ComplexityExpr.Variable
                            || f instanceof ComplexityExpr.Constant
                            || f instanceof ComplexityExpr.Log);
            return hasVar && onlySimple;
        }
        return false;
    }

    public static ComplexityExpr siblingWorkFromReturn(Expression returnExpression, String methodName,
            Function<Expression, ComplexityExpr> analyzer) {
        if (returnExpression == null) {
            return ComplexityExpr.one();
        }
        if (returnExpression instanceof MethodCallExpr call && methodName.equals(call.getNameAsString())) {
            return ComplexityExpr.one();
        }
        if (returnExpression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            ComplexityExpr left = expressionContainsSelfCall(binary.getLeft(), methodName)
                    ? ComplexityExpr.one()
                    : analyzer.apply(binary.getLeft());
            ComplexityExpr right = expressionContainsSelfCall(binary.getRight(), methodName)
                    ? ComplexityExpr.one()
                    : analyzer.apply(binary.getRight());
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(left, right)));
        }
        if (expressionContainsSelfCall(returnExpression, methodName)) {
            return ComplexityExpr.one();
        }
        return analyzer.apply(returnExpression);
    }

    private static boolean expressionContainsSelfCall(Expression expression, String methodName) {
        return expression.findAll(MethodCallExpr.class).stream()
                .anyMatch(c -> methodName.equals(c.getNameAsString()) && c.getScope().isEmpty());
    }

    public static Set<ArgumentPattern> distinctSelfCallPatterns(MethodDeclaration method, String methodName) {
        Set<ArgumentPattern> patterns = new HashSet<>();
        for (MethodCallExpr call : directSelfCalls(method, methodName)) {
            if (call.getArguments().isEmpty()) {
                patterns.add(ArgumentPattern.UNSUPPORTED);
            } else {
                patterns.add(classifyArgument(call.getArgument(0)));
            }
        }
        return patterns;
    }
}
