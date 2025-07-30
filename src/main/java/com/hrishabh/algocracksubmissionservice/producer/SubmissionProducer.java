package com.hrishabh.algocracksubmissionservice.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmissionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendToSubmissionQueue(String submissionJson) {
        kafkaTemplate.send("submission-queue", submissionJson);
    }
}

