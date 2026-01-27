package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.QuestionStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for QuestionStatistics tracking per-question analytics.
 */
public interface QuestionStatisticsRepository extends JpaRepository<QuestionStatistics, Long> {

    /**
     * Find statistics for a specific question.
     */
    Optional<QuestionStatistics> findByQuestionId(Long questionId);
}
