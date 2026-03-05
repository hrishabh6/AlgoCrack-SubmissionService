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
}
