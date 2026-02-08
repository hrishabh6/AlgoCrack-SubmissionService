package com.hrishabh.algocracksubmissionservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for pushing real-time updates to clients via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Send status update to client.
     */
    public void sendStatus(Submission submission) {
        String destination = "/topic/submission/" + submission.getSubmissionId();

        Map<String, Object> message = new HashMap<>();
        message.put("submissionId", submission.getSubmissionId());
        message.put("status", submission.getStatus().name());

        log.debug("Sending status update to {}: {}", destination, message);
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * Send final result to client.
     */
    public void sendResult(Submission submission) {
        String destination = "/topic/submission/" + submission.getSubmissionId();

        // Extract test case counts from testResults JSON
        int[] counts = extractTestCaseCounts(submission.getTestResults());

        Map<String, Object> message = new HashMap<>();
        message.put("submissionId", submission.getSubmissionId());
        message.put("status", "COMPLETED");
        message.put("verdict", submission.getVerdict() != null ? submission.getVerdict().name() : null);
        message.put("runtimeMs", submission.getRuntimeMs());
        message.put("memoryKb", submission.getMemoryKb());
        message.put("passedTestCases", counts[0]);
        message.put("totalTestCases", counts[1]);

        log.info("Sending result to {}: verdict={}", destination, submission.getVerdict());
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * Send error to client.
     */
    public void sendError(Submission submission, String error) {
        String destination = "/topic/submission/" + submission.getSubmissionId();

        Map<String, Object> message = new HashMap<>();
        message.put("submissionId", submission.getSubmissionId());
        message.put("status", "FAILED");
        message.put("error", error);

        log.error("Sending error to {}: {}", destination, error);
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * Extract [passedCount, totalCount] from testResults JSON.
     */
    private int[] extractTestCaseCounts(String testResultsJson) {
        if (testResultsJson == null || testResultsJson.isEmpty()) {
            return new int[] { 0, 0 };
        }
        try {
            List<Map<String, Object>> results = mapper.readValue(
                    testResultsJson, new TypeReference<List<Map<String, Object>>>() {
                    });
            int total = results.size();
            int passed = (int) results.stream()
                    .filter(r -> Boolean.TRUE.equals(r.get("passed")))
                    .count();
            return new int[] { passed, total };
        } catch (Exception e) {
            log.warn("Failed to parse testResults JSON: {}", e.getMessage());
            return new int[] { 0, 0 };
        }
    }
}
