package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context;

import com.github.javaparser.ast.body.Parameter;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ParameterVariableMapper {

    private ParameterVariableMapper() {
    }

    public static Map<String, String> fromMetadata(QuestionMetadataApiDto metadata) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (metadata == null) {
            return variables;
        }
        List<String> names = metadata.getParamNames();
        List<String> types = metadata.getParamTypes();
        if (names == null || types == null) {
            return variables;
        }
        for (int i = 0; i < names.size() && i < types.size(); i++) {
            String paramName = names.get(i);
            String type = types.get(i);
            String meaning = describeParameter(type, paramName);
            String symbol = symbolFor(type, paramName, i);
            variables.put(symbol, meaning);
        }
        return variables;
    }

    public static Map<String, String> fromParameters(List<Parameter> parameters) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String type = parameter.getType().asString();
            String paramName = parameter.getNameAsString();
            variables.put(symbolFor(type, paramName, i), describeParameter(type, paramName));
        }
        return variables;
    }

    private static String symbolFor(String type, String paramName, int index) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("[]") || normalized.contains("list") || normalized.contains("string")) {
            if (index == 0) {
                return "n";
            }
            if (index == 1) {
                return "m";
            }
        }
        if (normalized.contains("graph") || normalized.contains("edge")) {
            return index == 0 ? "v" : "e";
        }
        return paramName.isBlank() ? "n" : paramName;
    }

    private static String describeParameter(String type, String paramName) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("int[]") || normalized.contains("integer[]")) {
            return "length of " + paramName;
        }
        if (normalized.contains("list")) {
            return "size of " + paramName;
        }
        if (normalized.contains("string")) {
            return "length of " + paramName;
        }
        if (normalized.contains("[][]")) {
            return "dimensions of " + paramName;
        }
        return "size of " + paramName;
    }
}
