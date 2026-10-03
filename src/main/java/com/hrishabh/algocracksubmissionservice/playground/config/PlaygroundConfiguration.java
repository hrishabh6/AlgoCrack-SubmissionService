package com.hrishabh.algocracksubmissionservice.playground.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlaygroundProperties.class)
public class PlaygroundConfiguration {
}
