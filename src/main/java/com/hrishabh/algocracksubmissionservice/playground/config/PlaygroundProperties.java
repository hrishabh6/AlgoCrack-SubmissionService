package com.hrishabh.algocracksubmissionservice.playground.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "playground")
public class PlaygroundProperties {

    /** When false, playground REST controllers are not registered. */
    private boolean apiEnabled = false;

    /** When false, POST /run stays unavailable even if apiEnabled is true. */
    private boolean runEnabled = false;

    private int maxSavedPerUser = 100;
    private int defaultPageSize = 20;
    private int maxPageSize = 50;
    private int maxTitleLength = 120;
    private int maxSourceBytes = 32 * 1024;
    private int maxStdinBytes = 8 * 1024;
    private int maxRunsPerMinute = 10;
    private int cxePollTimeoutSeconds = 45;
}
