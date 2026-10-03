package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.UserTopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, Long> {

    List<UserTopicProgress> findByUserIdAndRankAlgorithmVersion(String userId, String rankAlgorithmVersion);

    void deleteByUserIdAndRankAlgorithmVersion(String userId, String rankAlgorithmVersion);
}
