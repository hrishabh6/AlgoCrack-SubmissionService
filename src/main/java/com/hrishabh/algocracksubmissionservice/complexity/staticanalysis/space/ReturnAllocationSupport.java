package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space;

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;

/**
 * Narrow ownership hints for excluding required output from auxiliary space (Batch 4).
 */
public final class ReturnAllocationSupport {

    private ReturnAllocationSupport() {
    }

    /** Direct return of a fresh allocation is required output, not auxiliary. */
    public static boolean isDefiniteRequiredOutputExpression(Expression expression) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()) {
            return true;
        }
        if (expression instanceof MethodCallExpr call) {
            String name = call.getNameAsString();
            return "copyOf".equals(name) || "toCharArray".equals(name);
        }
        if (expression instanceof NameExpr) {
            return true;
        }
        return false;
    }

    public static boolean isPassThroughHelperReturn(Expression expression) {
        return expression instanceof MethodCallExpr call && call.getScope().isEmpty();
    }

    public static boolean isLocalResultReference(Expression expression, String resultLocalName) {
        return expression instanceof NameExpr name && resultLocalName.equals(name.getNameAsString());
    }

    public static boolean isVariableDeclarationOfResult(VariableDeclarationExpr varDecl, String localName) {
        return varDecl.getVariables().stream()
                .anyMatch(v -> localName.equals(v.getNameAsString()) && v.getInitializer().isPresent());
    }
}
