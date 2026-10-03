package com.hrishabh.algocracksubmissionservice.progress.badge;

import java.util.Set;

public final class BadgeCodes {

    public static final String FIRST_SOLVE = "FIRST_SOLVE";
    public static final String SOLVED_10 = "SOLVED_10";
    public static final String SOLVED_50 = "SOLVED_50";
    public static final String SOLVED_100 = "SOLVED_100";
    public static final String MEDIUM_10 = "MEDIUM_10";
    public static final String MEDIUM_25 = "MEDIUM_25";
    public static final String HARD_5 = "HARD_5";
    public static final String HARD_10 = "HARD_10";
    public static final String HARD_25 = "HARD_25";
    public static final String POTD_FIRST = "POTD_FIRST";
    public static final String POTD_7 = "POTD_7";
    public static final String POTD_30 = "POTD_30";
    public static final String POTD_100 = "POTD_100";
    public static final String POTD_STREAK_7 = "POTD_STREAK_7";
    public static final String POTD_STREAK_30 = "POTD_STREAK_30";
    public static final String TOPIC_EXPLORER = "TOPIC_EXPLORER";
    public static final String GRAPH_PRACTITIONER = "GRAPH_PRACTITIONER";

    public static final Set<String> ALL = Set.of(
            FIRST_SOLVE,
            SOLVED_10,
            SOLVED_50,
            SOLVED_100,
            MEDIUM_10,
            MEDIUM_25,
            HARD_5,
            HARD_10,
            HARD_25,
            POTD_FIRST,
            POTD_7,
            POTD_30,
            POTD_100,
            POTD_STREAK_7,
            POTD_STREAK_30,
            TOPIC_EXPLORER,
            GRAPH_PRACTITIONER);

    private BadgeCodes() {
    }
}
