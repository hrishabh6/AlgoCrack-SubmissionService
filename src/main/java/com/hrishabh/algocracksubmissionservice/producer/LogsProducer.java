package com.hrishabh.algocracksubmissionservice.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendToLogsTopic(String logLine) {
        kafkaTemplate.send("submission-logs", logLine);
    }
}