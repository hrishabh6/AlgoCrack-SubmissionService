package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterShapeRegistry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Maps helper formal symbols to call-site symbols and validates substitution (Batch 2).
 */
public final class HelperCallSiteSupport {

    public record SubstitutionResult(boolean success, ComplexityExpr expr, Map<String, String> mapping) {
        public static SubstitutionResult failed() {
            return new SubstitutionResult(false, new ComplexityExpr.Unknown("substitution failed"), Map.of());
        }

        public static SubstitutionResult ok(ComplexityExpr expr, Map<String, String> mapping) {
            return new SubstitutionResult(true, expr, mapping);
        }
    }

    private HelperCallSiteSupport() {
    }

    public static SubstitutionResult substituteAtCallSite(
            ComplexityExpr helperSummary,
            MethodDeclaration helperDecl,
            MethodCallExpr call,
            Function<Expression, Optional<ComplexityExpr>> callSiteSizeForArgument,
            Set<String> callerDocumentedSymbols) {
        List<ParameterShapeRegistry.ParameterShape> shapes =
                ParameterShapeRegistry.shapesFromParameters(helperDecl.getParameters());
        if (call.getArguments().size() != shapes.size()) {
            return SubstitutionResult.failed();
        }
        Map<String, String> symbolMap = new HashMap<>();
        for (int i = 0; i < shapes.size(); i++) {
            ParameterShapeRegistry.ParameterShape shape = shapes.get(i);
            Expression arg = call.getArgument(i);
            Optional<ComplexityExpr> argSize = callSiteSizeForArgument.apply(arg);
            if (argSize.isEmpty() || argSize.get() instanceof ComplexityExpr.Unknown) {
                return SubstitutionResult.failed();
            }
            ComplexityExpr size = ComplexityExprSimplifier.simplify(argSize.get());
            if (!mapShapeSymbols(shape, size, symbolMap, callerDocumentedSymbols)) {
                return SubstitutionResult.failed();
            }
        }
        ComplexityExpr renamed = ComplexityExprRenamer.rename(helperSummary, symbolMap);
        ComplexityExpr simplified = ComplexityExprSimplifier.simplify(renamed);
        if (!ComplexityExprSimplifier.allVariablesDocumented(simplified, callerDocumentedSymbols)) {
            return SubstitutionResult.failed();
        }
        return SubstitutionResult.ok(simplified, symbolMap);
    }

    private static boolean mapShapeSymbols(
            ParameterShapeRegistry.ParameterShape shape,
            ComplexityExpr argumentSize,
            Map<String, String> symbolMap,
            Set<String> callerDocumentedSymbols) {
        if (shape.kind() == ParameterShapeRegistry.ParameterShape.Kind.TWO_DIMENSIONAL) {
            return mapTwoDimensional(shape, argumentSize, symbolMap, callerDocumentedSymbols);
        }
        return mapSingleSymbol(shape.primarySymbol(), argumentSize, symbolMap, callerDocumentedSymbols);
    }

    private static boolean mapTwoDimensional(
            ParameterShapeRegistry.ParameterShape shape,
            ComplexityExpr argumentSize,
            Map<String, String> symbolMap,
            Set<String> callerDocumentedSymbols) {
        if (argumentSize instanceof ComplexityExpr.Product product && product.factors().size() == 2) {
            String row = shape.rowSymbol().orElseThrow();
            String col = shape.colSymbol().orElseThrow();
            ComplexityExpr f0 = product.factors().get(0);
            ComplexityExpr f1 = product.factors().get(1);
            if (f0 instanceof ComplexityExpr.Variable v0 && f1 instanceof ComplexityExpr.Variable v1
                    && callerDocumentedSymbols.contains(v0.name()) && callerDocumentedSymbols.contains(v1.name())) {
                symbolMap.put(row, v0.name());
                symbolMap.put(col, v1.name());
                return true;
            }
        }
        if (argumentSize instanceof ComplexityExpr.Variable v && callerDocumentedSymbols.contains(v.name())) {
            symbolMap.put(shape.rowSymbol().orElseThrow(), v.name());
            return false;
        }
        return false;
    }

    private static boolean mapSingleSymbol(
            String formalSymbol,
            ComplexityExpr argumentSize,
            Map<String, String> symbolMap,
            Set<String> callerDocumentedSymbols) {
        if (argumentSize instanceof ComplexityExpr.Variable v) {
            if (!callerDocumentedSymbols.contains(v.name())) {
                return false;
            }
            symbolMap.put(formalSymbol, v.name());
            return true;
        }
        if (argumentSize instanceof ComplexityExpr.Constant) {
            return false;
        }
        return false;
    }
}
