package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocracksubmissionservice.models.QuestionStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for QuestionStatistics tracking per-question analytics.
 */
public interface QuestionStatisticsRepository extends JpaRepository<QuestionStatistics, Long> {

    /**
     * Find statistics for a specific question.
     */
    Optional<QuestionStatistics> findByQuestionId(Long questionId);

    /**
     * Batch lookup used by ProblemService to show acceptance rates for a page of questions.
     */
    List<QuestionStatistics> findByQuestionIdIn(Collection<Long> questionIds);
}
