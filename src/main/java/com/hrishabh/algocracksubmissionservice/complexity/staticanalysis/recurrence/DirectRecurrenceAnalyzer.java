package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.recurrence;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.MethodIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Narrow direct self-recursion analysis with full sibling work (Batch 2).
 */
public final class DirectRecurrenceAnalyzer {

    public record DirectRecurrenceResult(
            RecurrenceSupport.ArgumentPattern pattern,
            ComplexityExpr siblingWorkPerLevel,
            boolean progressProven,
            boolean baseCaseProven) {
    }

    private DirectRecurrenceAnalyzer() {
    }

    public static Optional<DirectRecurrenceResult> analyze(
            MethodDeclaration method,
            MethodIdentity identity,
            String sizeParameterName,
            Function<Statement, ComplexityExpr> statementCostAnalyzer,
            Function<Expression, ComplexityExpr> expressionCostAnalyzer) {
        List<MethodCallExpr> selfCalls = directSelfCalls(method, identity);
        if (selfCalls.isEmpty()) {
            return Optional.empty();
        }
        if (selfCalls.size() != 1) {
            return Optional.empty();
        }
        MethodCallExpr onlyCall = selfCalls.getFirst();
        if (onlyCall.getArguments().isEmpty()) {
            return Optional.empty();
        }
        Expression arg = onlyCall.getArgument(0);
        if (!argumentUsesSizeParameter(arg, sizeParameterName)) {
            return Optional.empty();
        }
        RecurrenceSupport.ArgumentPattern pattern = RecurrenceSupport.classifyArgument(arg);
        if (pattern == RecurrenceSupport.ArgumentPattern.UNSUPPORTED) {
            return Optional.empty();
        }
        if (!hasRecognizableBaseCase(method, sizeParameterName, pattern)) {
            return Optional.empty();
        }
        ComplexityExpr sibling = siblingWorkExcludingRecursiveCall(
                method, onlyCall, statementCostAnalyzer, expressionCostAnalyzer);
        if (sibling instanceof ComplexityExpr.Unknown) {
            return Optional.empty();
        }
        return Optional.of(new DirectRecurrenceResult(pattern, sibling, true, true));
    }

    public static List<MethodCallExpr> directSelfCalls(MethodDeclaration method, MethodIdentity identity) {
        String methodName = identity.methodName();
        return method.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getScope().isEmpty() && methodName.equals(call.getNameAsString()))
                .toList();
    }

    private static boolean argumentUsesSizeParameter(Expression argument, String sizeParameterName) {
        return argument.findAll(NameExpr.class).stream()
                .anyMatch(n -> sizeParameterName.equals(n.getNameAsString()));
    }

    private static boolean hasRecognizableBaseCase(
            MethodDeclaration method, String sizeParameterName, RecurrenceSupport.ArgumentPattern pattern) {
        List<IfStmt> guards = method.findAll(IfStmt.class);
        for (IfStmt guard : guards) {
            if (conditionImpliesProgressStop(guard.getCondition(), sizeParameterName, pattern)
                    && branchReturns(guard.getThenStmt())) {
                return true;
            }
        }
        return false;
    }

    private static boolean branchReturns(Statement stmt) {
        if (stmt instanceof ReturnStmt) {
            return true;
        }
        if (stmt instanceof BlockStmt block) {
            return block.getStatements().stream().anyMatch(s -> s instanceof ReturnStmt);
        }
        return false;
    }

    private static boolean conditionImpliesProgressStop(
            Expression condition, String sizeParameterName, RecurrenceSupport.ArgumentPattern pattern) {
        if (condition instanceof BinaryExpr binary) {
            return switch (binary.getOperator()) {
                case LESS_EQUALS, LESS, EQUALS -> comparesSizeParameter(binary, sizeParameterName);
                default -> false;
            };
        }
        if (condition instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            return conditionImpliesProgressStop(unary.getExpression(), sizeParameterName, pattern);
        }
        return false;
    }

    private static boolean comparesSizeParameter(BinaryExpr binary, String sizeParameterName) {
        return referencesParameter(binary.getLeft(), sizeParameterName)
                || referencesParameter(binary.getRight(), sizeParameterName);
    }

    private static boolean referencesParameter(Expression expr, String sizeParameterName) {
        if (expr instanceof NameExpr name) {
            return sizeParameterName.equals(name.getNameAsString());
        }
        if (expr instanceof FieldAccessExpr access && access.getScope() instanceof NameExpr name) {
            return sizeParameterName.equals(name.getNameAsString());
        }
        return expr.findAll(NameExpr.class).stream()
                .anyMatch(n -> sizeParameterName.equals(n.getNameAsString()));
    }

    private static ComplexityExpr siblingWorkExcludingRecursiveCall(
            MethodDeclaration method,
            MethodCallExpr recursiveCall,
            Function<Statement, ComplexityExpr> statementCostAnalyzer,
            Function<Expression, ComplexityExpr> expressionCostAnalyzer) {
        BlockStmt body = method.getBody().orElse(new BlockStmt());
        List<ComplexityExpr> parts = new ArrayList<>();
        for (Statement statement : body.getStatements()) {
            parts.add(statementCostExcludingCall(statement, recursiveCall, statementCostAnalyzer, expressionCostAnalyzer));
        }
        if (parts.isEmpty()) {
            return ComplexityExpr.one();
        }
        return new ComplexityExpr.Sum(parts);
    }

    private static ComplexityExpr statementCostExcludingCall(
            Statement statement,
            MethodCallExpr recursiveCall,
            Function<Statement, ComplexityExpr> statementCostAnalyzer,
            Function<Expression, ComplexityExpr> expressionCostAnalyzer) {
        if (statement instanceof ReturnStmt ret) {
            Optional<Expression> expr = ret.getExpression();
            if (expr.isEmpty()) {
                return ComplexityExpr.one();
            }
            return expressionCostExcludingCall(expr.get(), recursiveCall, expressionCostAnalyzer);
        }
        return statementCostAnalyzer.apply(statement);
    }

    private static ComplexityExpr expressionCostExcludingCall(
            Expression expression,
            MethodCallExpr recursiveCall,
            Function<Expression, ComplexityExpr> expressionCostAnalyzer) {
        if (expression instanceof MethodCallExpr call && call == recursiveCall) {
            ComplexityExpr argCost = ComplexityExpr.one();
            if (!call.getArguments().isEmpty()) {
                argCost = expressionCostExcludingCall(call.getArgument(0), recursiveCall, expressionCostAnalyzer);
            }
            return argCost;
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            return new ComplexityExpr.Sum(List.of(
                    expressionCostExcludingCall(binary.getLeft(), recursiveCall, expressionCostAnalyzer),
                    expressionCostExcludingCall(binary.getRight(), recursiveCall, expressionCostAnalyzer)));
        }
        if (expression.findAll(MethodCallExpr.class).stream().anyMatch(c -> c == recursiveCall)) {
            return expressionCostAnalyzer.apply(expression);
        }
        return expressionCostAnalyzer.apply(expression);
    }
}
