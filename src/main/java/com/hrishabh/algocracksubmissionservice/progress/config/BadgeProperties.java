package com.hrishabh.algocracksubmissionservice.progress.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "gamification.badge")
public class BadgeProperties {

    /** Canonical Graph tag name fallback when tag id is not configured. */
    private String graphTagName = "Graph";

    /** Optional canonical tag id; when zero, name matching is used. */
    private long graphTagId = 0L;
}
