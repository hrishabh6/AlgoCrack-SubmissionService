package com.hrishabh.algocracksubmissionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient to communicate with CodeExecutionService.
 */
@Configuration
public class WebClientConfig {

    @Value("${cxe.service.url:http://localhost:8081}")
    private String cxeServiceUrl;

    @Bean
    public WebClient cxeWebClient() {
        return WebClient.builder()
                .baseUrl(cxeServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
