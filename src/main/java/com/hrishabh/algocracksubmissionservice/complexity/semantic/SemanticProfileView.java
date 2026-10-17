package com.hrishabh.algocracksubmissionservice.complexity.semantic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Problem Service Semantic Metadata V1 payload fields used by static analysis.
 * This is problem meaning only. It does not carry an expected Big-O.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticProfileView(
        String schemaVersion,
        List<String> families,
        Entry entry,
        List<SizeVariable> sizeVariables,
        List<Structure> structures,
        List<MutatedInput> mutatedInputs) {

    public static final String SCHEMA_V1 = "semantic-v1";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String kind, String className, String inputEncoding, List<Operation> operations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(String name, String role, List<OperationParameter> parameters, String returns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationParameter(String kind, String encoding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SizeVariable(String name, String kind, Binding binding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Binding(
            Integer parameterIndex,
            String operationName,
            Integer operationParameterIndex,
            String dimension) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Structure(
            Binding binding,
            String kind,
            String elementKind,
            String encoding,
            Constraints constraints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Constraints(
            Boolean rectangular,
            Boolean singlyLinked,
            Boolean undirected,
            Boolean zeroBasedVertexLabels,
            Boolean oneIndexedEntryNode,
            Integer fixedRows,
            Integer fixedColumns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MutatedInput(Integer parameterIndex, String structureKind) {
    }
}
