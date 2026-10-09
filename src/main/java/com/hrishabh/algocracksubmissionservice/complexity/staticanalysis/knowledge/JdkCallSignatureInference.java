package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conservative erased signature inference for JDK overload matching.
 */
public final class JdkCallSignatureInference {

    private JdkCallSignatureInference() {
    }

    public static List<String> inferParameterTypes(
            MethodCallExpr call,
            Map<String, String> localNameToRawType,
            Map<String, String> parameterNameToType,
            Map<String, String> localJdkReceiverTypes) {
        List<String> types = new ArrayList<>();
        for (Expression argument : call.getArguments()) {
            types.add(inferExpressionType(argument, localNameToRawType, parameterNameToType, localJdkReceiverTypes)
                    .orElse("unknown"));
        }
        return types;
    }

    public static Optional<String> inferExpressionType(
            Expression expression,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType,
            Map<String, String> localJdkReceiverTypes) {
        if (expression instanceof IntegerLiteralExpr) {
            return Optional.of("int");
        }
        if (expression instanceof StringLiteralExpr) {
            return Optional.of("String");
        }
        if (expression instanceof NameExpr name) {
            String n = name.getNameAsString();
            if (localJdkReceiverTypes.containsKey(n)) {
                return Optional.of(localJdkReceiverTypes.get(n));
            }
            if (localNameToType.containsKey(n)) {
                return Optional.of(JdkOperationIdentity.eraseType(localNameToType.get(n)));
            }
            if (parameterNameToType.containsKey(n)) {
                return Optional.of(JdkOperationIdentity.eraseType(parameterNameToType.get(n)));
            }
            return Optional.empty();
        }
        if (expression instanceof FieldAccessExpr access && "length".equals(access.getNameAsString())) {
            return Optional.of("int");
        }
        if (expression instanceof BinaryExpr binary) {
            return switch (binary.getOperator()) {
                case MINUS, PLUS -> inferExpressionType(binary.getLeft(), localNameToType, parameterNameToType, localJdkReceiverTypes);
                default -> Optional.empty();
            };
        }
        if (expression instanceof com.github.javaparser.ast.expr.ArrayCreationExpr array) {
            return Optional.of(JdkOperationIdentity.eraseType(array.getElementType().asString()) + "[]");
        }
        return Optional.empty();
    }
}
