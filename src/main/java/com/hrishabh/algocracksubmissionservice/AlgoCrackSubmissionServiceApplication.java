package com.hrishabh.algocracksubmissionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EntityScan({
        "com.hrishabh.algocracksubmissionservice.models",
        "com.hrishabh.algocracksubmissionservice.progress.model"
})
@EnableJpaRepositories({
        "com.hrishabh.algocracksubmissionservice.repository",
        "com.hrishabh.algocracksubmissionservice.progress.repository"
})
@EnableJpaAuditing
@EnableAsync
public class AlgoCrackSubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlgoCrackSubmissionServiceApplication.class, args);
    }

}
