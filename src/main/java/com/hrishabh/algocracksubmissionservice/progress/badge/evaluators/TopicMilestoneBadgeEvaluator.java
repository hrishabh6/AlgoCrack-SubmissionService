package com.hrishabh.algocracksubmissionservice.progress.badge.evaluators;

import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeAwardCandidate;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeCodes;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluationContext;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.ThresholdBadgeSupport;
import com.hrishabh.algocracksubmissionservice.progress.config.BadgeProperties;
import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import com.hrishabh.algocracksubmissionservice.progress.model.UserTopicProgress;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Component
public class TopicMilestoneBadgeEvaluator implements BadgeEvaluator {

    private final RankProperties rankProperties;
    private final BadgeProperties badgeProperties;

    public TopicMilestoneBadgeEvaluator(RankProperties rankProperties, BadgeProperties badgeProperties) {
        this.rankProperties = rankProperties;
        this.badgeProperties = badgeProperties;
    }

    @Override
    public String category() {
        return "TOPIC";
    }

    @Override
    public List<BadgeAwardCandidate> evaluate(BadgeEvaluationContext context) {
        List<BadgeAwardCandidate> candidates = new ArrayList<>();
        double explorerMinimum = rankProperties.getBreadth().getExplorerCredit();
        double practitionerMinimum = rankProperties.getBreadth().getPractitionerCredit();

        long explorerTopics = 0;
        BigDecimal graphCredit = BigDecimal.ZERO;

        for (UserTopicProgress row : context.getTopicProgress()) {
            double credit = row.getNormalizedCredit().doubleValue();
            if (credit >= explorerMinimum) {
                explorerTopics++;
            }
            if (matchesGraphTag(row)) {
                graphCredit = row.getNormalizedCredit();
            }
        }

        candidates.add(ThresholdBadgeSupport.threshold(BadgeCodes.TOPIC_EXPLORER, explorerTopics, 4));
        long graphScaled = (long) Math.floor(graphCredit.doubleValue() * 10);
        long practitionerScaled = (long) Math.floor(practitionerMinimum * 10);
        candidates.add(new BadgeAwardCandidate(
                BadgeCodes.GRAPH_PRACTITIONER,
                graphCredit.doubleValue() >= practitionerMinimum,
                Math.min(graphScaled, practitionerScaled),
                practitionerScaled));
        return candidates;
    }

    private boolean matchesGraphTag(UserTopicProgress row) {
        if (badgeProperties.getGraphTagId() > 0 && row.getTagId() == badgeProperties.getGraphTagId()) {
            return true;
        }
        String name = row.getTagNameSnapshot();
        return name != null
                && name.trim().equalsIgnoreCase(badgeProperties.getGraphTagName().trim());
    }
}
