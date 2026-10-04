package com.hrishabh.algocracksubmissionservice.complexity;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.support.SourceSha256Hasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplexityAnalysisPersistenceTest {

    @Test
    void activeSlotUsesSentinelOneForInFlightRows() {
        ComplexityAnalysis row = ComplexityAnalysis.builder()
                .analysisId("a-1")
                .submissionId("sub-1")
                .ownerUserId("u-1")
                .questionId(1L)
                .language("JAVA")
                .status(ComplexityProcessingStatus.QUEUED)
                .sourceSha256(SourceSha256Hasher.hash("class Main {}"))
                .activeSlot(ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                .build();

        assertTrue(row.isActive());
        assertEquals(1, row.getActiveSlot());
        assertNull(row.getAnalysisFingerprint());
    }

    @Test
    void sourceSha256IsDeterministic() {
        String first = SourceSha256Hasher.hash("public class Main {}");
        String second = SourceSha256Hasher.hash("public class Main {}");
        assertEquals(first, second);
        assertEquals(64, first.length());
    }
}
