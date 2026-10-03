package com.hrishabh.algocracksubmissionservice.playground.repository;

import com.hrishabh.algocracksubmissionservice.playground.model.Playground;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaygroundRepository extends JpaRepository<Playground, Long> {

    Optional<Playground> findByIdAndUserId(Long id, String userId);

    Page<Playground> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);
}
