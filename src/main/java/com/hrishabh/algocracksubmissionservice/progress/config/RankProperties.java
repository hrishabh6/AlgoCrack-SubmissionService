package com.hrishabh.algocracksubmissionservice.progress.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "gamification.rank")
public class RankProperties {

    @Min(0)
    private long masteryEasy = 10;

    @Min(0)
    private long masteryMedium = 25;

    @Min(0)
    private long masteryHard = 45;

    @Min(0)
    private long potdEasy = 6;

    @Min(0)
    private long potdMedium = 8;

    @Min(0)
    private long potdHard = 10;

    @Min(0)
    private long breadthCap = 300;

    @Valid
    @NotNull
    private BreadthThresholds breadth = new BreadthThresholds();

    @Valid
    @NotEmpty
    private List<TierThreshold> tiers = defaultTiers();

    @Getter
    @Setter
    public static class BreadthThresholds {
        @Min(0)
        private double explorerCredit = 1.0;
        @Min(0)
        private int explorerPoints = 4;
        @Min(0)
        private double practitionerCredit = 3.0;
        @Min(0)
        private int practitionerPoints = 10;
        @Min(0)
        private double specialistCredit = 6.0;
        @Min(0)
        private int specialistPoints = 18;
    }

    @Getter
    @Setter
    public static class TierThreshold {
        @NotNull
        private String code;
        @NotNull
        private String name;
        @Min(0)
        private long minimumScore;
    }

    private static List<TierThreshold> defaultTiers() {
        return List.of(
                tier("NOVICE", "Novice", 0),
                tier("BRONZE", "Bronze", 100),
                tier("SILVER", "Silver", 300),
                tier("GOLD", "Gold", 700),
                tier("PLATINUM", "Platinum", 1500),
                tier("DIAMOND", "Diamond", 3000),
                tier("MASTER", "Master", 6000));
    }

    private static TierThreshold tier(String code, String name, long min) {
        TierThreshold t = new TierThreshold();
        t.setCode(code);
        t.setName(name);
        t.setMinimumScore(min);
        return t;
    }
}
