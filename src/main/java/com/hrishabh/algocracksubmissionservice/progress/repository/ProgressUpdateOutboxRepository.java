package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.ProgressOutboxStatus;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressUpdateOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProgressUpdateOutboxRepository extends JpaRepository<ProgressUpdateOutbox, Long> {

    @Query("""
            SELECT o FROM ProgressUpdateOutbox o
            WHERE o.status = :status AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt ASC
            """)
    List<ProgressUpdateOutbox> findReady(
            @Param("status") ProgressOutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
