package com.hrishabh.algocracksubmissionservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class LogsConsumer {

    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = "submission-logs", groupId = "log-reader-group")
    public void listen(String logLine) {
        // Store in memory, or DB, or cache
        logLines.add(logLine);
    }

    public List<String> getLogs() {
        return new ArrayList<>(logLines);
    }
}
