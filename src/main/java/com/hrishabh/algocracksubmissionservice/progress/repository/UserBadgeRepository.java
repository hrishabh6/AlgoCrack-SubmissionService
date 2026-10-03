package com.hrishabh.algocracksubmissionservice.progress.repository;

import com.hrishabh.algocracksubmissionservice.progress.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserIdOrderByEarnedAtDesc(String userId);

    boolean existsByUserIdAndBadgeCode(String userId, String badgeCode);

    @Query("SELECT ub.badgeCode FROM UserBadge ub WHERE ub.userId = :userId")
    Set<String> findBadgeCodesByUserId(@Param("userId") String userId);
}
