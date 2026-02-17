package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for User entity — used to look up users by business ID.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by business identifier (String userId), not DB primary key.
     */
    Optional<User> findByUserId(String userId);
}
