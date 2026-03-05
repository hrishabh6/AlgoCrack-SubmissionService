package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Submission entity with async tracking support.
 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

        /**
         * Find submission by external UUID.
         */
        Optional<Submission> findBySubmissionId(String submissionId);

        /**
         * Get user's submissions ordered by most recent.
         */
        Page<Submission> findByUserIdOrderByQueuedAtDesc(String userId, Pageable pageable);

        /**
         * Get user's submissions for a specific question.
         */
        /**
         * Get user's submissions for a specific question with pagination.
         */
        Page<Submission> findByUserIdAndQuestionIdOrderByQueuedAtDesc(String userId, Long questionId,
                        Pageable pageable);

        /**
         * Get all pending submissions.
         */
        List<Submission> findByStatusInOrderByQueuedAtAsc(List<SubmissionStatus> statuses);

        // ── Inter-Service Query Methods (Phase 7) ──────────────────────────

        /**
         * Count distinct solved questions by user and difficulty.
         */
        @Query("SELECT COUNT(DISTINCT s.questionId) FROM Submission s WHERE s.userId = :userId AND s.difficultyLevel = :difficulty AND s.status = 'COMPLETED' AND s.verdict = 'ACCEPTED'")
        long countDistinctSolvedByUserIdAndDifficulty(@Param("userId") String userId,
                        @Param("difficulty") String difficulty);

        /**
         * Count distinct solved questions grouped by language.
         */
        @Query("SELECT s.language, COUNT(DISTINCT s.questionId) FROM Submission s WHERE s.userId = :userId AND s.status = 'COMPLETED' AND s.verdict = 'ACCEPTED' GROUP BY s.language")
        List<Object[]> countDistinctSolvedByUserIdGroupByLanguage(@Param("userId") String userId);

        /**
         * Count submissions grouped by date for heatmap.
         */
        @Query("SELECT FUNCTION('DATE', s.queuedAt), COUNT(s) FROM Submission s WHERE s.userId = :userId AND s.queuedAt >= :from AND s.queuedAt < :to GROUP BY FUNCTION('DATE', s.queuedAt) ORDER BY FUNCTION('DATE', s.queuedAt) ASC")
        List<Object[]> countSubmissionsGroupedByDateBetween(
                        @Param("userId") String userId,
                        @Param("from") java.time.LocalDateTime from,
                        @Param("to") java.time.LocalDateTime to);
}
