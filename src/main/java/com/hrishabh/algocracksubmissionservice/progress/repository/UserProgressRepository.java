package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    Optional<UserProgress> findByUserIdAndRankAlgorithmVersion(String userId, String rankAlgorithmVersion);

    @Query("""
            SELECT up FROM UserProgress up
            WHERE up.rankAlgorithmVersion = :version AND up.totalRankScore > 0
            ORDER BY up.totalRankScore DESC, up.hardSolved DESC, up.mediumSolved DESC,
                     up.totalPotdCompleted DESC, up.scoreAchievedAt ASC, up.userId ASC
            """)
    Page<UserProgress> findLeaderboard(@Param("version") String version, Pageable pageable);

    @Query("""
            SELECT COUNT(up) + 1 FROM UserProgress up
            WHERE up.rankAlgorithmVersion = :version AND up.totalRankScore > 0
              AND (
                up.totalRankScore > :score
                OR (up.totalRankScore = :score AND up.hardSolved > :hard)
                OR (up.totalRankScore = :score AND up.hardSolved = :hard AND up.mediumSolved > :medium)
                OR (up.totalRankScore = :score AND up.hardSolved = :hard AND up.mediumSolved = :medium
                    AND up.totalPotdCompleted > :potd)
              )
            """)
    long countBetterThan(
            @Param("version") String version,
            @Param("score") long score,
            @Param("hard") long hard,
            @Param("medium") long medium,
            @Param("potd") long potd);
}
