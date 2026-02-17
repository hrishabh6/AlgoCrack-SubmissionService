package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.Submission;
import com.hrishabh.algocrackentityservice.models.SubmissionStatus;
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
    Page<Submission> findByUser_UserIdOrderByQueuedAtDesc(String userId, Pageable pageable);

    /**
     * Get user's submissions for a specific question.
     */
    List<Submission> findByUser_UserIdAndQuestionIdOrderByQueuedAtDesc(String userId, Long questionId);

    /**
     * Count pending submissions in the system.
     */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<SubmissionStatus> statuses);

    /**
     * Get all pending submissions.
     */
    List<Submission> findByStatusInOrderByQueuedAtAsc(List<SubmissionStatus> statuses);
}
