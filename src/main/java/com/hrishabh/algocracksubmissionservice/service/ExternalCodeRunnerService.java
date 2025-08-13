package com.hrishabh.algocracksubmissionservice.service;


import com.hrishabh.algocracksubmissionservice.dto.SubmissionDto;
import com.hrishabh.algocracksubmissionservice.producer.LogsProducer;
import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service// 💡 Use the same bean name to easily swap the services
public class ExternalCodeRunnerService {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private LogsProducer logsProducer;

    @Autowired
    private RestTemplate restTemplate;

    // Read the engine's URL from application.properties
    @Value("${code-execution.engine.url:http://localhost:8081/api/v1/code-execution/run}")
    private String codeExecutionEngineUrl;

    public void processSubmission(SubmissionDto submissionDto) {
        System.out.println("Processing submission ID: " + submissionDto.getSubmissionId() + " for user: " + submissionDto.getUserId());

        // 1. Fetch Question Metadata and Test Cases from the database
        System.out.println("passing to getQuestionMetadata and getTestCases");
        Optional<CodeSubmissionDTO.QuestionMetadata> metadataOpt = questionService.getQuestionMetadata(submissionDto.getQuestionId());

        if (metadataOpt.isEmpty()) {
            String errorMessage = String.format("Question metadata not found for questionId: %d", submissionDto.getQuestionId());
            logsProducer.sendToLogsTopic(errorMessage);
            throw new IllegalStateException(errorMessage);
        }


        CodeSubmissionDTO.QuestionMetadata metadata = metadataOpt.get();
        var testCases = questionService.getTestCases(submissionDto.getQuestionId());
        System.out.println("testCases: " + testCases);


        // 2. Build the DTO for the Code Execution Engine
        System.out.println("Building DTO for Code Execution Engine. User code: " + submissionDto.getCode() + ", Question metadata: " + metadata + ", Test cases: " + testCases );
        CodeSubmissionDTO codeSubmissionDTO = CodeSubmissionDTO.builder()
                .submissionId(String.valueOf(submissionDto.getSubmissionId()))
                .language(submissionDto.getLanguage())
                .userSolutionCode(submissionDto.getCode())
                .questionMetadata(metadata)
                .testCases(testCases)
                .build();

        // 3. Make a POST request to the execution engine
        try {
            System.out.println("Calling external execution engine at: " + codeExecutionEngineUrl);
            ResponseEntity<CodeExecutionResultDTO> response = restTemplate.postForEntity(
                    codeExecutionEngineUrl,
                    codeSubmissionDTO,
                    CodeExecutionResultDTO.class
            );

            // 4. Process the returned DTO
            CodeExecutionResultDTO result = response.getBody();
            if (result != null) {
                // Log the final results from the engine
                logsProducer.sendToLogsTopic("Final Status: " + result.getOverallStatus());
                logsProducer.sendToLogsTopic("Compilation Output: " + result.getCompilationOutput());
            }

        } catch (Exception e) {
            // Handle errors from the HTTP call
            System.err.println("Error connecting to execution engine: " + e.getMessage());
            logsProducer.sendToLogsTopic("Error connecting to execution engine: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
