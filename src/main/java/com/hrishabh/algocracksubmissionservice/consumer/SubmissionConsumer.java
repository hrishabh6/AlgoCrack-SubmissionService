package com.hrishabh.algocracksubmissionservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SubmissionConsumer {

    @KafkaListener(topics = "submission-queue", groupId = "submission-service-group")
    public void listen(String submissionJson) throws JsonProcessingException {
        // Convert JSON to DTO
        SubmissionDto dto = new ObjectMapper().readValue(submissionJson, SubmissionDto.class);

    }
}
