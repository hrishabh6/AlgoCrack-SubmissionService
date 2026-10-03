package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;

import java.math.BigDecimal;

public final class TopicLevelResolver {

    private TopicLevelResolver() {
    }

    public static String resolve(BigDecimal credit, RankProperties.BreadthThresholds thresholds) {
        if (credit == null) {
            return "UNQUALIFIED";
        }
        double c = credit.doubleValue();
        if (c >= thresholds.getSpecialistCredit()) {
            return "SPECIALIST";
        }
        if (c >= thresholds.getPractitionerCredit()) {
            return "PRACTITIONER";
        }
        if (c >= thresholds.getExplorerCredit()) {
            return "EXPLORER";
        }
        return "UNQUALIFIED";
    }
}
