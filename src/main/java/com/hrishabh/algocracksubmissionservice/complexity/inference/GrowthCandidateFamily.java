package com.hrishabh.algocracksubmissionservice.complexity.inference;

import java.util.Optional;

public enum GrowthCandidateFamily {
    O1("O(1)"),
    O_LOG_N("O(log n)"),
    O_N("O(n)"),
    O_N_LOG_N("O(n log n)"),
    O_N2("O(n^2)"),
    O_N3("O(n^3)"),
    INCONCLUSIVE(null);

    private final String bigOLabel;

    GrowthCandidateFamily(String bigOLabel) {
        this.bigOLabel = bigOLabel;
    }

    public Optional<String> bigOLabel() {
        return Optional.ofNullable(bigOLabel);
    }

    public static Optional<GrowthCandidateFamily> fromStaticBigO(String staticBigO) {
        if (staticBigO == null || staticBigO.isBlank()) {
            return Optional.empty();
        }
        String normalized = staticBigO.replace(" ", "").toLowerCase();
        return switch (normalized) {
            case "o(1)" -> Optional.of(O1);
            case "o(logn)" -> Optional.of(O_LOG_N);
            case "o(n)" -> Optional.of(O_N);
            case "o(nlogn)" -> Optional.of(O_N_LOG_N);
            case "o(n^2)", "o(n²)" -> Optional.of(O_N2);
            case "o(n^3)", "o(n³)" -> Optional.of(O_N3);
            default -> Optional.empty();
        };
    }

    /** Lower rank = simpler family (Occam preference). */
    public int simplicityRank() {
        return switch (this) {
            case O1 -> 0;
            case O_LOG_N -> 1;
            case O_N -> 2;
            case O_N_LOG_N -> 3;
            case O_N2 -> 4;
            case O_N3 -> 5;
            case INCONCLUSIVE -> 99;
        };
    }

    double scaleAt(long n) {
        if (n <= 0) {
            return Double.NaN;
        }
        double nd = Math.max(n, 2L);
        return switch (this) {
            case O1 -> 1.0;
            case O_LOG_N -> Math.log(nd) / Math.log(2.0);
            case O_N -> nd;
            case O_N_LOG_N -> nd * (Math.log(nd) / Math.log(2.0));
            case O_N2 -> nd * nd;
            case O_N3 -> nd * nd * nd;
            case INCONCLUSIVE -> Double.NaN;
        };
    }
}
