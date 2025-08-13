package com.hrishabh.algocracksubmissionservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionDto;
    import com.hrishabh.algocracksubmissionservice.service.ExternalCodeRunnerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SubmissionConsumer {

//    private final CodeRunnerService codeRunnerService;
    private final ExternalCodeRunnerService codeRunnerService;
    private final ObjectMapper objectMapper;

    public SubmissionConsumer(ExternalCodeRunnerService codeRunnerService, ObjectMapper objectMapper) {
        this.codeRunnerService = codeRunnerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "submission-queue", groupId = "submission-service-group")
    public void listen(String submissionJson)  {
        try{
            // Convert JSON to DTO
            SubmissionDto dto = objectMapper.readValue(submissionJson, SubmissionDto.class);

            // 💡 Pass the DTO to the new service for processing
            codeRunnerService.processSubmission(dto);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}