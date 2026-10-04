package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityAnalysisLeaseServiceTest {

    @Mock
    private ComplexityAnalysisRepository repository;

    private ComplexityAnalysisLeaseService workerA;
    private ComplexityAnalysisLeaseService workerB;

    @BeforeEach
    void setUp() {
        ComplexityProperties properties = new ComplexityProperties();
        workerA = new ComplexityAnalysisLeaseService(repository, properties);
        workerB = new ComplexityAnalysisLeaseService(repository, properties);
    }

    @Test
    void onlyOneWorkerClaimsAnalysis() {
        when(repository.claimLease(eq("a-1"), eq(workerA.getWorkerId()), any(), any())).thenReturn(1);
        when(repository.claimLease(eq("a-1"), eq(workerB.getWorkerId()), any(), any())).thenReturn(0);

        assertTrue(workerA.tryClaim("a-1"));
        assertFalse(workerB.tryClaim("a-1"));
    }

    @Test
    void workerIdsAreUniquePerServiceInstance() {
        assertFalse(workerA.getWorkerId().equals(workerB.getWorkerId()));
    }

    @Test
    void holdsLeaseWhenOwnerAndNotExpired() {
        ComplexityAnalysis analysis = ComplexityAnalysis.builder()
                .analysisId("a-1")
                .leaseOwner(workerA.getWorkerId())
                .leaseExpiresAt(LocalDateTime.now().plusSeconds(30))
                .build();
        assertTrue(workerA.holdsLease(analysis));
        assertFalse(workerB.holdsLease(analysis));
    }
}
