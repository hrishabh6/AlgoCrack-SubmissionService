package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.BadgeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, Long> {

    List<BadgeDefinition> findByActiveTrueOrderBySortOrderAsc();

    Optional<BadgeDefinition> findByCode(String code);
}
