package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context;

import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves Java expressions to symbolic size magnitudes (not evaluation cost).
 */
public final class SymbolSizeResolver {

    private final List<ParameterShapeRegistry.ParameterShape> parameterShapes;
    private final Map<String, ParameterShapeRegistry.ParameterShape> shapeByParamName;
    private final Map<String, ComplexityExpr> localSizeAliases = new HashMap<>();

    public SymbolSizeResolver(List<ParameterShapeRegistry.ParameterShape> parameterShapes) {
        this.parameterShapes = List.copyOf(parameterShapes);
        this.shapeByParamName = new HashMap<>();
        for (ParameterShapeRegistry.ParameterShape shape : parameterShapes) {
            shapeByParamName.put(shape.paramName(), shape);
        }
    }

    public void bindLocalAlias(String localName, ComplexityExpr sizeMagnitude) {
        if (localName == null || localName.isBlank() || sizeMagnitude == null) {
            return;
        }
        if (sizeMagnitude instanceof ComplexityExpr.Unknown) {
            localSizeAliases.remove(localName);
            return;
        }
        localSizeAliases.put(localName, sizeMagnitude);
    }

    public void invalidateLocal(String localName) {
        localSizeAliases.remove(localName);
    }

    public Optional<ComplexityExpr> resolveSize(Expression expression) {
        if (expression == null) {
            return Optional.empty();
        }
        if (expression instanceof NameExpr name) {
            return resolveNameSize(name.getNameAsString());
        }
        if (expression instanceof FieldAccessExpr access && "length".equals(access.getNameAsString())) {
            return resolveLengthAccess(access.getScope());
        }
        if (expression instanceof MethodCallExpr call && "size".equals(call.getNameAsString()) && call.getScope().isPresent()) {
            return resolveCollectionSize(call.getScope().get());
        }
        if (expression instanceof IntegerLiteralExpr literal) {
            try {
                return Optional.of(new ComplexityExpr.Constant(Integer.parseInt(literal.getValue())));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<ComplexityExpr> resolveNameSize(String name) {
        if (localSizeAliases.containsKey(name)) {
            return Optional.of(localSizeAliases.get(name));
        }
        ParameterShapeRegistry.ParameterShape shape = shapeByParamName.get(name);
        if (shape != null) {
            return switch (shape.kind()) {
                case ONE_DIMENSIONAL, COLLECTION -> Optional.of(ComplexityExpr.var(shape.primarySymbol()));
                case TWO_DIMENSIONAL -> Optional.of(ComplexityExpr.var(shape.rowSymbol().orElse(shape.primarySymbol())));
                case SCALAR, OTHER -> Optional.of(ComplexityExpr.var(shape.primarySymbol()));
            };
        }
        if (localSizeAliases.containsKey(name)) {
            return Optional.of(localSizeAliases.get(name));
        }
        return Optional.empty();
    }

    private Optional<ComplexityExpr> resolveLengthAccess(Expression scope) {
        if (scope instanceof NameExpr name) {
            ParameterShapeRegistry.ParameterShape shape = shapeByParamName.get(name.getNameAsString());
            if (shape == null) {
                return localSizeAlias(name.getNameAsString());
            }
            return switch (shape.kind()) {
                case ONE_DIMENSIONAL -> Optional.of(ComplexityExpr.var(shape.primarySymbol()));
                case TWO_DIMENSIONAL -> Optional.of(ComplexityExpr.var(shape.rowSymbol().orElse(shape.primarySymbol())));
                default -> Optional.empty();
            };
        }
        if (scope instanceof ArrayAccessExpr access) {
            if (access.getIndex() instanceof IntegerLiteralExpr lit && "0".equals(lit.getValue())) {
                if (access.getName() instanceof NameExpr matrixName) {
                    ParameterShapeRegistry.ParameterShape shape = shapeByParamName.get(matrixName.getNameAsString());
                    if (shape != null && shape.kind() == ParameterShapeRegistry.ParameterShape.Kind.TWO_DIMENSIONAL) {
                        return shape.colSymbol().map(ComplexityExpr::var);
                    }
                }
            }
            return Optional.empty();
        }
        if (scope instanceof FieldAccessExpr nested && "length".equals(nested.getNameAsString())) {
            return resolveLengthAccess(nested.getScope());
        }
        return Optional.empty();
    }

    private Optional<ComplexityExpr> resolveCollectionSize(Expression receiver) {
        if (receiver instanceof NameExpr name) {
            ParameterShapeRegistry.ParameterShape shape = shapeByParamName.get(name.getNameAsString());
            if (shape != null && shape.kind() == ParameterShapeRegistry.ParameterShape.Kind.COLLECTION) {
                return Optional.of(ComplexityExpr.var(shape.primarySymbol()));
            }
            return localSizeAlias(name.getNameAsString());
        }
        return Optional.empty();
    }

    private Optional<ComplexityExpr> localSizeAlias(String name) {
        return Optional.ofNullable(localSizeAliases.get(name));
    }
}
