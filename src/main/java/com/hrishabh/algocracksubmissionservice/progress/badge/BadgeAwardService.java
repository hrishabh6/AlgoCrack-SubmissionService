package com.hrishabh.algocracksubmissionservice.progress.badge;

import com.hrishabh.algocracksubmissionservice.progress.model.UserBadge;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.repository.BadgeDefinitionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserBadgeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class BadgeAwardService {

    private final UserBadgeRepository userBadgeRepository;
    private final BadgeDefinitionRepository badgeDefinitionRepository;
    private final Clock clock;

    public BadgeAwardService(
            UserBadgeRepository userBadgeRepository,
            BadgeDefinitionRepository badgeDefinitionRepository,
            Clock clock) {
        this.userBadgeRepository = userBadgeRepository;
        this.badgeDefinitionRepository = badgeDefinitionRepository;
        this.clock = clock;
    }

    @Transactional
    public List<String> awardNewBadges(
            String userId,
            Collection<String> badgeCodes,
            String sourceType,
            String sourceReference) {
        if (badgeCodes == null || badgeCodes.isEmpty()) {
            return List.of();
        }
        LocalDateTime earnedAt = LocalDateTime.now(clock);
        List<String> awarded = new ArrayList<>();
        for (String code : badgeCodes) {
            if (userBadgeRepository.existsByUserIdAndBadgeCode(userId, code)) {
                continue;
            }
            if (badgeDefinitionRepository.findByCode(code).filter(d -> d.isActive()).isEmpty()) {
                log.warn("Skipping unknown or inactive badge code {}", code);
                continue;
            }
            userBadgeRepository.save(UserBadge.builder()
                    .userId(userId)
                    .badgeCode(code)
                    .earnedAt(earnedAt)
                    .sourceType(sourceType)
                    .sourceReference(sourceReference)
                    .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                    .build());
            awarded.add(code);
        }
        return awarded;
    }
}
