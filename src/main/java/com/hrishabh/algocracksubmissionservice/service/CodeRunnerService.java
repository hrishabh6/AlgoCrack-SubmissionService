package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.SubmissionDto;

import com.hrishabh.algocracksubmissionservice.producer.LogsProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.CodeExecutionManager;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.util.Optional;
import java.util.function.Consumer;

@Service
public class CodeRunnerService {

    @Autowired
    private CodeExecutionManager codeExecutionManager;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private LogsProducer logsProducer;

    public void processSubmission(SubmissionDto submissionDto) {
        System.out.println("Processing submission ID: " + submissionDto.getSubmissionId() + " for user: " + submissionDto.getUserId());

        // 1. Fetch Question Metadata and Test Cases from the database
        System.out.println("passing to getQuestionMetadata and getTestCases");
        Optional<CodeSubmissionDTO.QuestionMetadata> metadata = questionService.getQuestionMetadata(submissionDto.getQuestionId());
        System.out.println("metadata: " + metadata);
        var testCases = questionService.getTestCases(submissionDto.getQuestionId());
        System.out.println("testCases: " + testCases);

        // 2. Build the DTO for the Code Execution Engine
        System.out.println("Building DTO for Code Execution Engine. User code: " + submissionDto.getCode() + ", Question metadata: " + metadata + ", Test cases: " + testCases );
        CodeSubmissionDTO codeSubmissionDTO = CodeSubmissionDTO.builder()
                .submissionId(String.valueOf(submissionDto.getSubmissionId()))
                .language(submissionDto.getLanguage())
                .userSolutionCode(submissionDto.getCode())
                .questionMetadata(metadata.orElse(null))  // Unwrap the Optional
                .testCases(testCases)
                .build();

        // 3. Create a log consumer that sends each log line to the Kafka logs topic
        Consumer<String> kafkaLogConsumer = logLine -> {
            System.out.println("LOG: " + logLine); // Print to console for debugging
            logsProducer.sendToLogsTopic(logLine);
        };

        // 4. Run the code execution pipeline
        CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(
                codeSubmissionDTO,
                kafkaLogConsumer
        );

        // 5. Process and log the final result
        // You would typically store this final result in a database associated with the submissionId
        System.out.println("Final execution status: " + result.getOverallStatus());
        System.out.println("Final compilation output: " + result.getCompilationOutput());
        logsProducer.sendToLogsTopic("Final Status: " + result.getOverallStatus());
    }
}