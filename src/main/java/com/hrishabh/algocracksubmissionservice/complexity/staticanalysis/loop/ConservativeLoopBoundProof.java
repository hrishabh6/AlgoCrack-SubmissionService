package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.loop;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;

import java.util.Optional;
import java.util.Set;

/**
 * Batch 0 loop gate: emit a concrete iteration bound only when required facts are present.
 */
public final class ConservativeLoopBoundProof {

    private ConservativeLoopBoundProof() {
    }

    public record ProofResult(Optional<ComplexityExpr> iterationBound, Optional<StaticAnalysisReasonCode> failure) {
        public static ProofResult success(ComplexityExpr bound) {
            return new ProofResult(Optional.of(bound), Optional.empty());
        }

        public static ProofResult failure(StaticAnalysisReasonCode code) {
            return new ProofResult(Optional.empty(), Optional.of(code));
        }
    }

    public interface BoundMapper {
        Optional<ComplexityExpr> mapBoundExpression(Expression expression);

        Optional<ComplexityExpr> mapIterableSize(Expression iterable);
    }

    public static ProofResult proveForLoop(ForStmt forStmt, BoundMapper mapper) {
        Optional<String> inductionVar = extractComparisonInductionVar(forStmt.getCompare().orElse(null));
        if (inductionVar.isEmpty()) {
            return ProofResult.failure(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        String var = inductionVar.get();
        if (forStmt.getUpdate().isEmpty()) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        if (!initBindsVariable(forStmt.getInitialization(), var)) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        if (!updateListProvesProgress(forStmt.getUpdate(), var)) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        if (bodyMayInvalidateInduction(forStmt.getBody(), var)) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        Optional<ComplexityExpr> bound = extractBoundFromCompare(forStmt.getCompare().orElse(null), mapper);
        if (bound.isEmpty()) {
            return ProofResult.failure(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        return ProofResult.success(bound.get());
    }

    public static ProofResult proveWhileLoop(WhileStmt whileStmt, BoundMapper mapper) {
        Optional<String> inductionVar = extractComparisonInductionVar(whileStmt.getCondition());
        if (inductionVar.isEmpty()) {
            return ProofResult.failure(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        String var = inductionVar.get();
        if (!bodyContainsProgressUpdate(whileStmt.getBody(), var)) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        if (bodyMayInvalidateInduction(whileStmt.getBody(), var)) {
            return ProofResult.failure(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
        }
        Optional<ComplexityExpr> bound = extractBoundFromCompare(whileStmt.getCondition(), mapper);
        if (bound.isEmpty()) {
            return ProofResult.failure(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        return ProofResult.success(bound.get());
    }

    private static Optional<String> extractComparisonInductionVar(Expression compare) {
        if (!(compare instanceof BinaryExpr binary)) {
            return Optional.empty();
        }
        if (binary.getOperator() != BinaryExpr.Operator.LESS
                && binary.getOperator() != BinaryExpr.Operator.LESS_EQUALS) {
            return Optional.empty();
        }
        if (binary.getLeft() instanceof NameExpr name) {
            return Optional.of(name.getNameAsString());
        }
        return Optional.empty();
    }

    private static Optional<ComplexityExpr> extractBoundFromCompare(Expression compare, BoundMapper mapper) {
        if (!(compare instanceof BinaryExpr binary)) {
            return Optional.empty();
        }
        if (binary.getOperator() == BinaryExpr.Operator.LESS
                || binary.getOperator() == BinaryExpr.Operator.LESS_EQUALS) {
            return mapper.mapBoundExpression(binary.getRight());
        }
        return Optional.empty();
    }

    private static boolean initBindsVariable(com.github.javaparser.ast.NodeList<Expression> init, String var) {
        if (init == null || init.isEmpty()) {
            return false;
        }
        for (Expression node : init) {
            if (node instanceof VariableDeclarationExpr varDecl) {
                for (VariableDeclarator declarator : varDecl.getVariables()) {
                    if (var.equals(declarator.getNameAsString()) && declarator.getInitializer().isPresent()) {
                        return true;
                    }
                }
            }
            if (node instanceof AssignExpr assign && assign.getTarget() instanceof NameExpr name) {
                if (var.equals(name.getNameAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean updateListProvesProgress(com.github.javaparser.ast.NodeList<Expression> updates, String var) {
        if (updates == null || updates.isEmpty()) {
            return false;
        }
        for (Expression update : updates) {
            if (!updateProvesProgress(update, var)) {
                return false;
            }
        }
        return true;
    }

    private static boolean updateProvesProgress(Expression update, String var) {
        if (update instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT) {
            return unary.getExpression() instanceof NameExpr name && var.equals(name.getNameAsString());
        }
        if (update instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT) {
            return unary.getExpression() instanceof NameExpr name && var.equals(name.getNameAsString());
        }
        if (update instanceof AssignExpr assign && assign.getOperator() == AssignExpr.Operator.PLUS) {
            if (assign.getTarget() instanceof NameExpr name && var.equals(name.getNameAsString())) {
                return isNonZeroConstant(assign.getValue());
            }
        }
        if (update instanceof AssignExpr assign && assign.getOperator() == AssignExpr.Operator.ASSIGN) {
            if (assign.getTarget() instanceof NameExpr name && var.equals(name.getNameAsString())) {
                Expression value = assign.getValue();
                if (value instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
                    boolean leftIsVar = binary.getLeft() instanceof NameExpr n && var.equals(n.getNameAsString());
                    boolean rightIsVar = binary.getRight() instanceof NameExpr n && var.equals(n.getNameAsString());
                    if (leftIsVar && isNonZeroConstant(binary.getRight())) {
                        return true;
                    }
                    if (rightIsVar && isNonZeroConstant(binary.getLeft())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNonZeroConstant(Expression expression) {
        if (expression instanceof IntegerLiteralExpr literal) {
            try {
                return Integer.parseInt(literal.getValue()) > 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private static boolean bodyContainsProgressUpdate(Statement body, String var) {
        return findProgressUpdate(body, var);
    }

    private static boolean findProgressUpdate(Statement statement, String var) {
        return switch (statement) {
            case BlockStmt block -> block.getStatements().stream().anyMatch(s -> findProgressUpdate(s, var));
            case ExpressionStmt exprStmt -> exprStmt.getExpression() instanceof Expression expr
                    && expressionUpdatesVar(expr, var);
            case IfStmt ifStmt -> {
                boolean thenOk = findProgressUpdate(ifStmt.getThenStmt(), var);
                boolean elseOk = ifStmt.getElseStmt().map(s -> findProgressUpdate(s, var)).orElse(true);
                yield thenOk && elseOk;
            }
            case ForStmt forStmt -> updateListProvesProgress(forStmt.getUpdate(), var);
            case WhileStmt whileStmt -> findProgressUpdate(whileStmt.getBody(), var);
            case ContinueStmt ignored -> false;
            default -> false;
        };
    }

    private static boolean expressionUpdatesVar(Expression expression, String var) {
        if (updateProvesProgress(expression, var)) {
            return true;
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            return varDecl.getVariables().stream()
                    .anyMatch(v -> v.getInitializer().isPresent() && expressionUpdatesVar(v.getInitializer().get(), var));
        }
        return false;
    }

    private static boolean bodyMayInvalidateInduction(Statement body, String var) {
        if (containsContinue(body)) {
            return true;
        }
        return containsAssignmentToVar(body, var, Set.of());
    }

    private static boolean containsContinue(Statement statement) {
        return switch (statement) {
            case ContinueStmt ignored -> true;
            case BlockStmt block -> block.getStatements().stream().anyMatch(ConservativeLoopBoundProof::containsContinue);
            case IfStmt ifStmt -> containsContinue(ifStmt.getThenStmt())
                    || ifStmt.getElseStmt().map(ConservativeLoopBoundProof::containsContinue).orElse(false);
            case ForStmt forStmt -> containsContinue(forStmt.getBody());
            case WhileStmt whileStmt -> containsContinue(whileStmt.getBody());
            default -> false;
        };
    }

    private static boolean containsAssignmentToVar(Statement statement, String var, Set<String> skipUnaryOn) {
        return switch (statement) {
            case BlockStmt block -> block.getStatements().stream()
                    .anyMatch(s -> containsAssignmentToVar(s, var, skipUnaryOn));
            case ExpressionStmt exprStmt -> assignmentToVar(exprStmt.getExpression(), var, skipUnaryOn);
            case IfStmt ifStmt -> containsAssignmentToVar(ifStmt.getThenStmt(), var, skipUnaryOn)
                    || ifStmt.getElseStmt().map(s -> containsAssignmentToVar(s, var, skipUnaryOn)).orElse(false);
            case ForStmt forStmt -> containsAssignmentToVar(forStmt.getBody(), var, skipUnaryOn);
            case WhileStmt whileStmt -> containsAssignmentToVar(whileStmt.getBody(), var, skipUnaryOn);
            default -> false;
        };
    }

    private static boolean assignmentToVar(Expression expression, String var, Set<String> skipUnaryOn) {
        if (expression instanceof AssignExpr assign && assign.getTarget() instanceof NameExpr name) {
            if (var.equals(name.getNameAsString())) {
                if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
                    return true;
                }
                if (assign.getOperator() == AssignExpr.Operator.PLUS) {
                    return false;
                }
            }
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            return varDecl.getVariables().stream()
                    .anyMatch(v -> var.equals(v.getNameAsString()) && v.getInitializer().isPresent());
        }
        return false;
    }
}
