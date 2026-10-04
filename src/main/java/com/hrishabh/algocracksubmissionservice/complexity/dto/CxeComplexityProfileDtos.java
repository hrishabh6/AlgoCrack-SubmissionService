package com.hrishabh.algocracksubmissionservice.complexity.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** Mirrors CXE internal COMPLEXITY_PROFILE API DTOs. */
public final class CxeComplexityProfileDtos {

    private CxeComplexityProfileDtos() {
    }

    public record SubmitRequest(
            String executionId,
            String submissionId,
            Long questionId,
            String language,
            String sourceCode,
            QuestionMetadataDto questionMetadata,
            String profileCode,
            String profileVersion,
            String profileHash,
            String harnessVersion,
            List<ProfileCaseRequest> cases) {
    }

    public record QuestionMetadataDto(
            String fullyQualifiedPackageName,
            String functionName,
            String returnType,
            List<ParameterDto> parameters,
            Map<String, String> customDataStructures,
            String questionType,
            Boolean isOutputOrderMatters) {
    }

    public record ParameterDto(String name, String type) {
    }

    public record ProfileCaseRequest(
            String caseId,
            String caseIdentity,
            String profileCode,
            String profileVersion,
            String profileHash,
            String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String seed,
            JsonNode input,
            String inputHash,
            JsonNode expectedOutput,
            int warmups,
            int measuredRepeats) {
    }

    public record SubmitResponse(String executionId, String status) {
    }

    public record PollResponse(
            String executionId,
            String status,
            String compileStatus,
            String jdkVersion,
            String harnessVersion,
            String measurementPolicyVersion,
            String profilerRuntimeVersion,
            String environmentFingerprint,
            List<ProfileCaseResult> cases,
            String errorCode,
            String errorMessage) {
    }

    public record ProfileCaseResult(
            String caseId,
            String caseIdentity,
            String profileCode,
            String profileVersion,
            String profileHash,
            String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String outcome,
            boolean outputValidated,
            int warmupCount,
            int sampleCount,
            Long medianElapsedNs,
            Long madElapsedNs,
            Long minElapsedNs,
            Long maxElapsedNs,
            String errorCode) {
    }
}
