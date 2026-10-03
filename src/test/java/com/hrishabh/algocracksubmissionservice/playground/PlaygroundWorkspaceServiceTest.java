package com.hrishabh.algocracksubmissionservice.playground;

import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.playground.config.PlaygroundProperties;
import com.hrishabh.algocracksubmissionservice.playground.repository.PlaygroundRepository;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaygroundWorkspaceServiceTest {

    @Mock
    private PlaygroundRepository playgroundRepository;

    private PlaygroundWorkspaceService service;

    @BeforeEach
    void setUp() {
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setMaxSourceBytes(32);
        properties.setMaxStdinBytes(8);
        service = new PlaygroundWorkspaceService(playgroundRepository, properties);
    }

    @Test
    void rejectsOversizedSource() {
        when(playgroundRepository.countByUserId("u1")).thenReturn(0L);
        assertThrows(ValidationException.class, () -> service.create(
                "u1",
                new com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundCreateRequest(
                        "t", "JAVA", "x".repeat(40), "")));
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThrows(ValidationException.class, () -> service.normalizeRunLanguage("RUST"));
    }
}
