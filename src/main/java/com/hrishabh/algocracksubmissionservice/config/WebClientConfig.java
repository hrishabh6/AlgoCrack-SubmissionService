package com.hrishabh.algocracksubmissionservice.config;

import com.hrishabh.algocracksubmissionservice.logging.RequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
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
                .filter((request, next) -> {
                    String requestId = RequestContext.getRequestId();
                    if (requestId == null) {
                        return next.exchange(request);
                    }
                    ClientRequest correlatedRequest = ClientRequest.from(request)
                            .header(RequestContext.REQUEST_ID_HEADER, requestId)
                            .build();
                    return next.exchange(correlatedRequest);
                })
                .build();
    }
}
