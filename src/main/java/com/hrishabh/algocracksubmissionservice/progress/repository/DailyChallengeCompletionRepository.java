package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.DailyChallengeCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyChallengeCompletionRepository extends JpaRepository<DailyChallengeCompletion, Long> {

    List<DailyChallengeCompletion> findByUserId(String userId);

    Optional<DailyChallengeCompletion> findByAcceptedSubmissionId(String acceptedSubmissionId);

    boolean existsByUserIdAndDailyChallengeId(String userId, long dailyChallengeId);

    void deleteByUserId(String userId);
}
