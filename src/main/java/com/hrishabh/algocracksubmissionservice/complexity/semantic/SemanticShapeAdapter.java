package com.hrishabh.algocracksubmissionservice.complexity.semantic;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Binding;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Constraints;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.SizeVariable;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Structure;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterShapeRegistry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterShapeRegistry.ParameterShape;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Projects a compatible Semantic Metadata V1 profile onto the analyzer's existing parameter shapes.
 */
public final class SemanticShapeAdapter {

    private SemanticShapeAdapter() {
    }

    public record Adapted(
            List<ParameterShape> shapes,
            Map<String, String> variables) {
    }

    public static Adapted adaptFunction(QuestionMetadataApiDto metadata, MethodDeclaration method, SemanticProfileView profile) {
        List<ParameterShape> base = baseShapes(metadata, method);
        return overlay(base, bindingsFor(profile, null), profile);
    }

    public static Adapted adaptOperation(MethodDeclaration method, SemanticProfileView profile, String operationName) {
        List<ParameterShape> base = ParameterShapeRegistry.shapesFromParameters(method.getParameters());
        return overlay(base, bindingsFor(profile, operationName), profile);
    }

    public static boolean isDesignClass(SemanticProfileView profile) {
        return profile != null
                && profile.entry() != null
                && "DESIGN_CLASS".equals(profile.entry().kind())
                && profile.entry().operations() != null;
    }

    private static List<ParameterShape> baseShapes(QuestionMetadataApiDto metadata, MethodDeclaration method) {
        if (metadata != null && metadata.getParamNames() != null && metadata.getParamTypes() != null
                && !metadata.getParamNames().isEmpty()) {
            return ParameterShapeRegistry.shapesFromMetadata(metadata.getParamNames(), metadata.getParamTypes());
        }
        if (method != null) {
            return ParameterShapeRegistry.shapesFromParameters(method.getParameters());
        }
        return List.of();
    }

    private static List<SizeVariable> bindingsFor(SemanticProfileView profile, String operationName) {
        if (profile == null || profile.sizeVariables() == null) {
            return List.of();
        }
        List<SizeVariable> matched = new ArrayList<>();
        for (SizeVariable variable : profile.sizeVariables()) {
            Binding binding = variable.binding();
            if (binding == null) {
                continue;
            }
            if (operationName == null && binding.parameterIndex() != null && binding.operationName() == null) {
                matched.add(variable);
            }
            if (operationName != null && operationName.equals(binding.operationName())) {
                matched.add(variable);
            }
        }
        return matched;
    }

    private static Adapted overlay(List<ParameterShape> base, List<SizeVariable> variables, SemanticProfileView profile) {
        List<ParameterShape> shapes = new ArrayList<>();
        Map<String, String> extra = new LinkedHashMap<>();
        for (int index = 0; index < base.size(); index++) {
            ParameterShape shape = base.get(index);
            List<SizeVariable> bound = variablesForIndex(variables, index);
            shapes.add(rewrite(shape, bound, structureAt(profile, index), extra));
        }
        Map<String, String> documented = new LinkedHashMap<>(ParameterShapeRegistry.documentedVariables(shapes));
        applyFixedDimensionNotes(documented, shapes, profile);
        documented.putAll(extra);
        return new Adapted(List.copyOf(shapes), documented);
    }

    private static List<SizeVariable> variablesForIndex(List<SizeVariable> variables, int index) {
        List<SizeVariable> bound = new ArrayList<>();
        for (SizeVariable variable : variables) {
            Binding binding = variable.binding();
            Integer parameterIndex = binding.parameterIndex() != null
                    ? binding.parameterIndex()
                    : binding.operationParameterIndex();
            if (parameterIndex != null && parameterIndex == index) {
                bound.add(variable);
            }
        }
        return bound;
    }

    private static ParameterShape rewrite(
            ParameterShape shape,
            List<SizeVariable> bound,
            Structure structure,
            Map<String, String> extra) {
        String row = null;
        String col = null;
        String primary = null;
        for (SizeVariable variable : bound) {
            String dimension = variable.binding().dimension();
            if ("ROWS".equals(dimension)) {
                row = variable.name();
            } else if ("COLUMNS".equals(dimension)) {
                col = variable.name();
            } else if ("AGGREGATE_LENGTH".equals(variable.kind()) || (primary != null && "EDGE_COUNT".equals(dimension))) {
                extra.put(variable.name(), describe(variable, shape, structure));
            } else if (primary == null) {
                primary = variable.name();
            } else {
                extra.put(variable.name(), describe(variable, shape, structure));
            }
        }
        if (row != null && col != null) {
            return new ParameterShape(
                    shape.paramName(), shape.rawType(), ParameterShape.Kind.TWO_DIMENSIONAL,
                    row, Optional.of(row), Optional.of(col));
        }
        if (primary == null) {
            return shape;
        }
        ParameterShape.Kind kind = shape.kind();
        if (isString(shape, structure)) {
            kind = ParameterShape.Kind.ONE_DIMENSIONAL;
        }
        return new ParameterShape(shape.paramName(), shape.rawType(), kind, primary, Optional.empty(), Optional.empty());
    }

    private static void applyFixedDimensionNotes(
            Map<String, String> documented, List<ParameterShape> shapes, SemanticProfileView profile) {
        if (profile == null || profile.structures() == null) {
            return;
        }
        for (Structure structure : profile.structures()) {
            if (structure.constraints() == null || structure.binding() == null || structure.binding().parameterIndex() == null) {
                continue;
            }
            int index = structure.binding().parameterIndex();
            if (index < 0 || index >= shapes.size()) {
                continue;
            }
            ParameterShape shape = shapes.get(index);
            Constraints constraints = structure.constraints();
            if (constraints.fixedRows() != null && shape.rowSymbol().isPresent()) {
                documented.put(shape.rowSymbol().get(),
                        "rows of " + shape.paramName() + " (fixed " + constraints.fixedRows() + ")");
            }
            if (constraints.fixedColumns() != null && shape.colSymbol().isPresent()) {
                documented.put(shape.colSymbol().get(),
                        "columns of " + shape.paramName() + " (fixed " + constraints.fixedColumns() + ")");
            }
        }
    }

    private static Structure structureAt(SemanticProfileView profile, int index) {
        if (profile == null || profile.structures() == null) {
            return null;
        }
        for (Structure structure : profile.structures()) {
            if (structure.binding() != null && structure.binding().parameterIndex() != null
                    && structure.binding().parameterIndex() == index) {
                return structure;
            }
        }
        return null;
    }

    private static boolean isString(ParameterShape shape, Structure structure) {
        if (structure != null && "STRING".equals(structure.kind())) {
            return true;
        }
        return shape.rawType() != null && shape.rawType().replace(" ", "").equals("String");
    }

    private static String describe(SizeVariable variable, ParameterShape shape, Structure structure) {
        String dimension = variable.binding().dimension();
        String target = shape.paramName();
        if ("EDGE_COUNT".equals(dimension)) {
            return "edge count of " + target;
        }
        if ("AGGREGATE_LENGTH".equals(variable.kind())) {
            return "aggregate length of " + target;
        }
        if ("NODE_COUNT".equals(dimension)) {
            return "node count of " + target;
        }
        return "size of " + target;
    }
}
