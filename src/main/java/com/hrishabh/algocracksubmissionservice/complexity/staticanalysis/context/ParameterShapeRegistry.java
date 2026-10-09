package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context;

import com.github.javaparser.ast.body.Parameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps parameters to stable complexity symbols and multidimensional shape metadata (Batch 1).
 */
public final class ParameterShapeRegistry {

    public record ParameterShape(
            String paramName,
            String rawType,
            Kind kind,
            String primarySymbol,
            Optional<String> rowSymbol,
            Optional<String> colSymbol) {

        public enum Kind {
            SCALAR,
            ONE_DIMENSIONAL,
            TWO_DIMENSIONAL,
            COLLECTION,
            OTHER
        }
    }

    private ParameterShapeRegistry() {
    }

    public static List<ParameterShape> shapesFromMetadata(
            List<String> paramNames, List<String> paramTypes) {
        List<ParameterShape> shapes = new ArrayList<>();
        if (paramNames == null || paramTypes == null) {
            return shapes;
        }
        for (int i = 0; i < paramNames.size() && i < paramTypes.size(); i++) {
            shapes.add(shapeFor(paramNames.get(i), paramTypes.get(i), i, countPriorSizeParams(shapes)));
        }
        return shapes;
    }

    public static List<ParameterShape> shapesFromParameters(List<Parameter> parameters) {
        List<ParameterShape> shapes = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter p = parameters.get(i);
            shapes.add(shapeFor(p.getNameAsString(), p.getType().asString(), i, countPriorSizeParams(shapes)));
        }
        return shapes;
    }

    public static ParameterShape shapeForIndexedParameter(String type, String paramName, int index) {
        List<ParameterShape> prior = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            prior.add(new ParameterShape("_", "int[]", ParameterShape.Kind.ONE_DIMENSIONAL, "n", Optional.empty(), Optional.empty()));
        }
        return shapeFor(paramName, type, index, countPriorSizeParams(prior));
    }

    public static Map<String, String> documentedVariables(List<ParameterShape> shapes) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (ParameterShape shape : shapes) {
            switch (shape.kind()) {
                case TWO_DIMENSIONAL -> {
                    String row = shape.rowSymbol().orElseThrow();
                    String col = shape.colSymbol().orElseThrow();
                    variables.put(row, "number of rows in " + shape.paramName());
                    variables.put(col, "number of columns in " + shape.paramName());
                }
                case ONE_DIMENSIONAL, COLLECTION -> variables.put(
                        shape.primarySymbol(), describeOneDimensional(shape));
                case SCALAR -> variables.put(shape.primarySymbol(), "size of " + shape.paramName());
                case OTHER -> variables.put(shape.primarySymbol(), "size of " + shape.paramName());
                default -> {
                }
            }
        }
        return variables;
    }

    private static int countPriorSizeParams(List<ParameterShape> shapes) {
        int count = 0;
        for (ParameterShape s : shapes) {
            if (s.kind() == ParameterShape.Kind.ONE_DIMENSIONAL
                    || s.kind() == ParameterShape.Kind.TWO_DIMENSIONAL
                    || s.kind() == ParameterShape.Kind.COLLECTION) {
                count++;
            }
        }
        return count;
    }

    private static ParameterShape shapeFor(String paramName, String type, int index, int sizeParamOrdinal) {
        String normalized = type.toLowerCase(Locale.ROOT).replace(" ", "");
        ParameterShape.Kind kind = classifyKind(normalized);
        return switch (kind) {
            case TWO_DIMENSIONAL -> {
                String row = rowSymbolFor(sizeParamOrdinal);
                String col = colSymbolFor(sizeParamOrdinal);
                yield new ParameterShape(paramName, type, kind, row, Optional.of(row), Optional.of(col));
            }
            case ONE_DIMENSIONAL, COLLECTION -> new ParameterShape(
                    paramName, type, kind, oneDimensionalSymbol(sizeParamOrdinal, paramName, index), Optional.empty(), Optional.empty());
            case SCALAR -> new ParameterShape(
                    paramName, type, kind, scalarSymbol(paramName, index), Optional.empty(), Optional.empty());
            case OTHER -> new ParameterShape(
                    paramName, type, kind, scalarSymbol(paramName, index), Optional.empty(), Optional.empty());
        };
    }

    private static ParameterShape.Kind classifyKind(String normalized) {
        if (normalized.contains("[][][]")) {
            return ParameterShape.Kind.TWO_DIMENSIONAL;
        }
        if (normalized.contains("[][]")) {
            return ParameterShape.Kind.TWO_DIMENSIONAL;
        }
        if (normalized.contains("[]") && !normalized.contains("[][]")) {
            return ParameterShape.Kind.ONE_DIMENSIONAL;
        }
        if (normalized.contains("priorityqueue")) {
            return ParameterShape.Kind.OTHER;
        }
        if (normalized.contains("list") || normalized.contains("set") || normalized.contains("queue")
                || normalized.contains("deque") || normalized.contains("collection")) {
            return ParameterShape.Kind.COLLECTION;
        }
        return ParameterShape.Kind.SCALAR;
    }

    private static String oneDimensionalSymbol(int sizeParamOrdinal, String paramName, int index) {
        return switch (sizeParamOrdinal) {
            case 0 -> "n";
            case 1 -> "m";
            case 2 -> "p";
            default -> paramName.isBlank() ? "n" : paramName;
        };
    }

    private static String rowSymbolFor(int matrixOrdinal) {
        return switch (matrixOrdinal) {
            case 0 -> "n";
            case 1 -> "p";
            default -> "rows";
        };
    }

    private static String colSymbolFor(int matrixOrdinal) {
        return switch (matrixOrdinal) {
            case 0 -> "m";
            case 1 -> "q";
            default -> "cols";
        };
    }

    private static String scalarSymbol(String paramName, int index) {
        if (!paramName.isBlank()) {
            return paramName;
        }
        return index == 0 ? "n" : "m";
    }

    private static String describeOneDimensional(ParameterShape shape) {
        String normalized = shape.rawType().toLowerCase(Locale.ROOT);
        if (normalized.contains("list")) {
            return "size of " + shape.paramName();
        }
        if (normalized.contains("string")) {
            return "length of " + shape.paramName();
        }
        return "length of " + shape.paramName();
    }
}
