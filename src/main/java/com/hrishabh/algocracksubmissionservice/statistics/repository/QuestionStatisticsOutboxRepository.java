package com.hrishabh.algocracksubmissionservice.statistics.repository;

import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionStatisticsOutboxRepository extends JpaRepository<QuestionStatisticsOutbox, Long> {

    Optional<QuestionStatisticsOutbox> findBySubmissionId(String submissionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM QuestionStatisticsOutbox o WHERE o.id = :id")
    Optional<QuestionStatisticsOutbox> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM QuestionStatisticsOutbox o
            WHERE o.questionId = :questionId AND o.status = :status
            """)
    List<QuestionStatisticsOutbox> findByQuestionIdAndStatusForUpdate(
            @Param("questionId") Long questionId,
            @Param("status") StatisticsOutboxStatus status);

    @Query("""
            SELECT o FROM QuestionStatisticsOutbox o
            WHERE o.status = :status AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt ASC
            """)
    List<QuestionStatisticsOutbox> findReady(
            @Param("status") StatisticsOutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
