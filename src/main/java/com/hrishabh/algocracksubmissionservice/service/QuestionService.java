package com.hrishabh.algocracksubmissionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import com.hrishabh.algocrackentityservice.models.TestCase;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.hrishabh.algocracksubmissionservice.repository.TestcaseRepository;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.ParamInfoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    @Autowired
    private QuestionMetadataRepository questionMetadataRepository;

    @Autowired
    private TestcaseRepository testCaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<CodeSubmissionDTO.QuestionMetadata> getQuestionMetadata(Long questionId) {
        QuestionMetadata metadata = questionMetadataRepository.findByQuestionIdWithAllCollections(questionId)
                .orElse(null);
        if (metadata == null) {
            System.out.println("Question metadata not found for questionId: " + questionId);
            return Optional.empty();
        }
        return Optional.of(mapEntityToDto(metadata));
    }

    private CodeSubmissionDTO.QuestionMetadata mapEntityToDto(QuestionMetadata entity) {
        List<ParamInfoDTO> parameters = entity.getParameters().stream()
                .map(paramInfo -> new ParamInfoDTO(paramInfo.getName(), paramInfo.getType()))
                .collect(Collectors.toList());

        return CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("com.algocrack.solution.q" + entity.getQuestion().getId())
                .functionName(entity.getFunctionName())
                .returnType(entity.getReturnType())
                .parameters(parameters)
                // 💡 This line was added to map the custom data structure names
                .customDataStructureNames(entity.getCustomDataStructureNames())
                .build();
    }

    public List<Map<String, Object>> getTestCases(Long questionId) {
        List<TestCase> testCaseEntities = testCaseRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);

        return testCaseEntities.stream()
                .map(this::mapTestCaseEntityToDtoMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapTestCaseEntityToDtoMap(TestCase testCaseEntity) {
        System.out.println("Processing test case ID: " + testCaseEntity.getId());
        System.out.println("Raw Input JSON: " + testCaseEntity.getInput());
        System.out.println("Raw Expected Output JSON: " + testCaseEntity.getExpectedOutput());

        try {
            Object inputObject = objectMapper.readValue(testCaseEntity.getInput(), Object.class);
            Object expectedOutputObject = objectMapper.readValue(testCaseEntity.getExpectedOutput(), Object.class);

            System.out.println("Successfully parsed test case ID: " + testCaseEntity.getId());

            return Map.of(
                    "input", inputObject,
                    "expectedOutput", expectedOutputObject
            );
        } catch (JsonProcessingException e) {
            System.err.println(
                    "Error parsing JSON for test case ID " + testCaseEntity.getId() +
                            ". Raw input: '" + testCaseEntity.getInput() + "', Raw expected output: '" + testCaseEntity.getExpectedOutput() + "'"
            );
            e.printStackTrace();

            return Map.of("input", Map.of(), "expectedOutput", "JSON_PARSE_ERROR");
        }
    }
}