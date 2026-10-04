package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.function.Consumer;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComplexityAnalysisLeaseService {

    @Getter
    private final String workerId = buildWorkerId();

    private final ComplexityAnalysisRepository analysisRepository;
    private final long leaseSeconds;

    public ComplexityAnalysisLeaseService(
            ComplexityAnalysisRepository analysisRepository,
            ComplexityProperties properties) {
        this.analysisRepository = analysisRepository;
        this.leaseSeconds = Math.max(15, properties.getWorkerLeaseSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String analysisId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = analysisRepository.claimLease(
                analysisId,
                workerId,
                now,
                now.plusSeconds(leaseSeconds));
        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(String analysisId) {
        LocalDateTime now = LocalDateTime.now();
        return analysisRepository.renewLease(
                analysisId,
                workerId,
                now,
                now.plusSeconds(leaseSeconds)) > 0;
    }

    public boolean holdsLease(ComplexityAnalysis analysis) {
        return workerId.equals(analysis.getLeaseOwner())
                && analysis.getLeaseExpiresAt() != null
                && analysis.getLeaseExpiresAt().isAfter(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ComplexityAnalysis> loadForLeaseWrite(String analysisId) {
        return analysisRepository.findWithActiveLeaseLock(analysisId, workerId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mutateIfLeaseHeld(String analysisId, Consumer<ComplexityAnalysis> mutator) {
        Optional<ComplexityAnalysis> locked = analysisRepository.findWithActiveLeaseLock(
                analysisId, workerId, LocalDateTime.now());
        if (locked.isEmpty()) {
            return false;
        }
        ComplexityAnalysis analysis = locked.get();
        mutator.accept(analysis);
        analysisRepository.save(analysis);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int claimQueuedForStaticAnalysis(String analysisId) {
        return analysisRepository.claimQueuedForStaticAnalysis(analysisId);
    }

    private static String buildWorkerId() {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // keep env/default host fragment
        }
        return host + ":" + UUID.randomUUID();
    }
}
