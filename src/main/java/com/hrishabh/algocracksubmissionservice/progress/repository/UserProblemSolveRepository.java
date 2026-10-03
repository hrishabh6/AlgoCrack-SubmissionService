package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.UserProblemSolve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProblemSolveRepository extends JpaRepository<UserProblemSolve, Long> {

    List<UserProblemSolve> findByUserId(String userId);

    Optional<UserProblemSolve> findByUserIdAndQuestionId(String userId, long questionId);

    boolean existsByUserIdAndQuestionId(String userId, long questionId);
}
