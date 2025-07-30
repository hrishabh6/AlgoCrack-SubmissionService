package com.hrishabh.algocracksubmissionservice.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionDto;
import com.hrishabh.algocracksubmissionservice.producer.SubmissionProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionProducer producer;

    @PostMapping("/submit")
    public ResponseEntity<String> submit(@RequestBody SubmissionDto dto) throws JsonProcessingException {
        String submissionJson = new ObjectMapper().writeValueAsString(dto); // or use custom mapper
        producer.sendToSubmissionQueue(submissionJson);
        return ResponseEntity.ok("Submitted!");
    }
}

