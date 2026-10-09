package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context;

import com.github.javaparser.ast.body.Parameter;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParameterVariableMapper {

    private ParameterVariableMapper() {
    }

    public static Map<String, String> fromMetadata(QuestionMetadataApiDto metadata) {
        if (metadata == null || metadata.getParamNames() == null || metadata.getParamTypes() == null) {
            return new LinkedHashMap<>();
        }
        return ParameterShapeRegistry.documentedVariables(
                ParameterShapeRegistry.shapesFromMetadata(metadata.getParamNames(), metadata.getParamTypes()));
    }

    public static Map<String, String> fromParameters(List<Parameter> parameters) {
        return ParameterShapeRegistry.documentedVariables(ParameterShapeRegistry.shapesFromParameters(parameters));
    }

    public static String symbolForParameter(String type, String paramName, int index) {
        return ParameterShapeRegistry.shapeForIndexedParameter(type, paramName, index).primarySymbol();
    }
}
