package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.*;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.repository.TestcaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Async processing service for code execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionProcessingService {

    private final CodeExecutionClientService cxeClient;
    private final ResultValidationService validationService;
    private final SubmissionRepository submissionRepository;
    private final QuestionStatisticsRepository statsRepository;
    private final QuestionMetadataRepository metadataRepository;
    private final TestcaseRepository testcaseRepository;
    private final WebSocketService webSocketService;

    /**
     * Process submission asynchronously.
     * @param submissionId The UUID of the submission to process (NOT the entity to avoid detached entity issues)
     */
    @Async
    @Transactional
    public void processSubmission(String submissionId) {
        log.info("Starting async processing for submission: {}", submissionId);

        // Re-fetch submission within this transaction to avoid detached entity issues
        Submission submission = submissionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        try {
            // 1. Update status to COMPILING
            submission.setStatus(SubmissionStatus.COMPILING);
            submission.setStartedAt(LocalDateTime.now());
            submission = submissionRepository.save(submission);
            webSocketService.sendStatus(submission);

            // 2. Fetch question metadata and test cases (filter by language)
            Long questionId = submission.getQuestion().getId();
            Language language = Language.valueOf(submission.getLanguage().toUpperCase());
            QuestionMetadata metadata = metadataRepository.findByQuestionIdAndLanguage(questionId, language)
                    .orElseThrow(() -> new RuntimeException("Question metadata not found for questionId: " + questionId + ", language: " + language));
            
            List<TestCase> testCases = testcaseRepository.findByQuestionIdOrderByOrderIndexAsc(questionId);

            // 3. Build execution request
            ExecutionRequest request = buildExecutionRequest(submission, metadata, testCases);

            // 4. Submit to CXE
            log.info("Submitting to CXE for: {}", submissionId);
            ExecutionResponse response = cxeClient.submitCode(request);

            // 5. Update status to RUNNING
            submission.setStatus(SubmissionStatus.RUNNING);
            submission = submissionRepository.save(submission);
            webSocketService.sendStatus(submission);

            // 6. Poll for completion
            SubmissionStatusDto result = pollForCompletion(response.getSubmissionId());

            // 7. Validate results
            SubmissionVerdict verdict = validationService.validateResults(
                    result.getTestCaseResults(),
                    testCases,
                    result.getCompilationOutput()
            );

            // 8. Update submission with results
            submission.setStatus(SubmissionStatus.COMPLETED);
            submission.setVerdict(verdict);
            submission.setRuntimeMs(result.getRuntimeMs());
            submission.setMemoryKb(result.getMemoryKb());
            submission.setCompletedAt(LocalDateTime.now());
            submission.setWorkerId(result.getWorkerId());
            submission.setPassedTestCases(validationService.countPassed(result.getTestCaseResults()));
            submission.setTotalTestCases(testCases.size());
            submission.setCompilationOutput(result.getCompilationOutput());
            submission.setErrorMessage(result.getErrorMessage());
            submissionRepository.save(submission);

            log.info("Submission {} completed with verdict: {}", submissionId, verdict);

            // 9. Update question statistics
            updateQuestionStatistics(submission);

            // 10. Notify client
            webSocketService.sendResult(submission);

        } catch (Exception e) {
            log.error("Processing failed for {}: {}", submissionId, e.getMessage(), e);
            
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setErrorMessage(e.getMessage());
            submission.setCompletedAt(LocalDateTime.now());
            submissionRepository.save(submission);
            
            webSocketService.sendError(submission, e.getMessage());
        }
    }

    /**
     * Build execution request for CXE.
     */
    private ExecutionRequest buildExecutionRequest(Submission submission, QuestionMetadata metadata, List<TestCase> testCases) {
        // Convert parameters - combine paramNames and paramTypes lists
        List<String> paramNames = metadata.getParamNames();
        List<String> paramTypes = metadata.getParamTypes();
        
        List<ExecutionRequest.Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < paramNames.size() && i < paramTypes.size(); i++) {
            parameters.add(ExecutionRequest.Parameter.builder()
                    .name(paramNames.get(i))
                    .type(paramTypes.get(i))
                    .build());
        }

        // Build metadata - customDataStructureNames not in current entity, pass empty list
        ExecutionRequest.QuestionMetadata metadataDto = ExecutionRequest.QuestionMetadata.builder()
                .fullyQualifiedPackageName("com.algocrack.solution.q" + submission.getQuestion().getId())
                .functionName(metadata.getFunctionName())
                .returnType(metadata.getReturnType())
                .parameters(parameters)
                .customDataStructureNames(new ArrayList<>()) // TODO: Add when entity supports it
                .build();

        // Convert test cases to maps
        List<Map<String, Object>> testCaseMaps = testCases.stream()
                .map(this::convertTestCase)
                .collect(Collectors.toList());

        return ExecutionRequest.builder()
                .submissionId(submission.getSubmissionId())
                .userId(submission.getUser().getId())
                .questionId(submission.getQuestion().getId())
                .language(submission.getLanguage())
                .code(submission.getCode())
                .metadata(metadataDto)
                .testCases(testCaseMaps)
                .build();
    }

    /**
     * Convert test case entity to map for CXE.
     */
    private Map<String, Object> convertTestCase(TestCase testCase) {
        Map<String, Object> map = new HashMap<>();
        // Parse JSON strings to objects
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object input = mapper.readValue(testCase.getInput(), Object.class);
            Object expectedOutput = mapper.readValue(testCase.getExpectedOutput(), Object.class);
            map.put("input", input);
            map.put("expectedOutput", expectedOutput);
        } catch (Exception e) {
            log.warn("Failed to parse test case JSON: {}", e.getMessage());
            map.put("input", testCase.getInput());
            map.put("expectedOutput", testCase.getExpectedOutput());
        }
        return map;
    }

    /**
     * Poll CXE for completion.
     */
    private SubmissionStatusDto pollForCompletion(String submissionId) {
        int maxAttempts = 60; // 30 seconds max
        int pollIntervalMs = 500;

        for (int i = 0; i < maxAttempts; i++) {
            SubmissionStatusDto status = cxeClient.getStatus(submissionId);
            
            if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                return cxeClient.getResults(submissionId);
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }

        throw new RuntimeException("Execution timeout after " + (maxAttempts * pollIntervalMs / 1000) + " seconds");
    }

    /**
     * Update question statistics after submission completes.
     */
    @Transactional
    public void updateQuestionStatistics(Submission submission) {
        Long questionId = submission.getQuestion().getId();
        
        QuestionStatistics stats = statsRepository.findByQuestionId(questionId)
                .orElseGet(() -> QuestionStatistics.builder()
                        .questionId(questionId)
                        .totalSubmissions(0)
                        .acceptedSubmissions(0)
                        .build());

        stats.incrementSubmissions(
                submission.getVerdict() == SubmissionVerdict.ACCEPTED,
                submission.getRuntimeMs(),
                submission.getMemoryKb()
        );

        statsRepository.save(stats);
        log.debug("Updated statistics for question {}: total={}, accepted={}", 
                questionId, stats.getTotalSubmissions(), stats.getAcceptedSubmissions());
    }
}
