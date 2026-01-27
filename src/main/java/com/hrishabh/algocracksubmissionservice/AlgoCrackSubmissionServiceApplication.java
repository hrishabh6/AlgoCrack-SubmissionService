package com.hrishabh.algocracksubmissionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EntityScan("com.hrishabh.algocrackentityservice.models")
@EnableJpaRepositories("com.hrishabh.algocracksubmissionservice.repository")
@EnableJpaAuditing
@EnableAsync
public class AlgoCrackSubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlgoCrackSubmissionServiceApplication.class, args);
    }

}
