package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.DailyChallengeCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyChallengeCompletionRepository extends JpaRepository<DailyChallengeCompletion, Long> {

    List<DailyChallengeCompletion> findByUserId(String userId);

    boolean existsByUserIdAndDailyChallengeId(String userId, long dailyChallengeId);
}
