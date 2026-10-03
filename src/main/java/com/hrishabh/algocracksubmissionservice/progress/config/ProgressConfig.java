package com.hrishabh.algocracksubmissionservice.progress.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RankProperties.class)
public class ProgressConfig {

    @Bean
    public Clock progressClock() {
        return Clock.systemUTC();
    }
}
