package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.Submission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for pushing real-time updates to clients via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

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

        Map<String, Object> message = new HashMap<>();
        message.put("submissionId", submission.getSubmissionId());
        message.put("status", "COMPLETED");
        message.put("verdict", submission.getVerdict() != null ? submission.getVerdict().name() : null);
        message.put("runtimeMs", submission.getRuntimeMs());
        message.put("memoryKb", submission.getMemoryKb());
        message.put("passedTestCases", submission.getPassedTestCases());
        message.put("totalTestCases", submission.getTotalTestCases());

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
}
