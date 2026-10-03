package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.LeaderboardResponse;
import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.LeaderboardRowDto;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardReadService {

    public static final int MAX_PAGE_SIZE = 50;

    private final UserProgressRepository userProgressRepository;

    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(int page, int size, String currentUserId) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<UserProgress> result = userProgressRepository.findLeaderboard(
                RankConstants.ALGORITHM_VERSION,
                PageRequest.of(safePage, safeSize));

        List<LeaderboardRowDto> rows = new ArrayList<>();
        long basePosition = (long) safePage * safeSize;
        for (int i = 0; i < result.getContent().size(); i++) {
            UserProgress row = result.getContent().get(i);
            rows.add(LeaderboardRowDto.builder()
                    .position(basePosition + i + 1)
                    .userId(row.getUserId())
                    .totalScore(row.getTotalRankScore())
                    .tierCode(row.getRankTierCode())
                    .uniqueSolved(row.getUniqueSolved())
                    .hardSolved(row.getHardSolved())
                    .totalPotdCompleted(row.getTotalPotdCompleted())
                    .currentUser(currentUserId != null && currentUserId.equals(row.getUserId()))
                    .build());
        }

        return LeaderboardResponse.builder()
                .content(rows)
                .page(safePage)
                .size(safeSize)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }
}
