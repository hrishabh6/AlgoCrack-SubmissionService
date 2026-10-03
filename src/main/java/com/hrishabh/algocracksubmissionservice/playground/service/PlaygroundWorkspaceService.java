package com.hrishabh.algocracksubmissionservice.playground.service;

import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.playground.exception.PlaygroundNotFoundException;
import com.hrishabh.algocracksubmissionservice.playground.config.PlaygroundProperties;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.*;
import com.hrishabh.algocracksubmissionservice.playground.model.Playground;
import com.hrishabh.algocracksubmissionservice.playground.repository.PlaygroundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundWorkspaceService {

    private static final Set<String> ALLOWED_LANGUAGES = Set.of("JAVA", "PYTHON");

    private final PlaygroundRepository playgroundRepository;
    private final PlaygroundProperties properties;

    @Transactional
    public PlaygroundDetailResponse create(String userId, PlaygroundCreateRequest request) {
        if (playgroundRepository.countByUserId(userId) >= properties.getMaxSavedPerUser()) {
            throw new ValidationException("Maximum saved playgrounds reached");
        }
        Playground entity = Playground.builder()
                .userId(userId)
                .title(normalizeTitle(request.title()))
                .language(validateLanguage(request.language()))
                .sourceCode(validateSource(request.sourceCode()))
                .stdin(validateStdin(request.stdin()))
                .build();
        Playground saved = playgroundRepository.save(entity);
        log.info("playground_created userId={} playgroundId={} language={}", userId, saved.getId(), saved.getLanguage());
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public PlaygroundPageResponse list(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), properties.getMaxPageSize());
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Playground> result = playgroundRepository.findByUserId(userId, pageable);
        return new PlaygroundPageResponse(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PlaygroundDetailResponse getOwned(String userId, long id) {
        return playgroundRepository.findByIdAndUserId(id, userId)
                .map(this::toDetail)
                .orElseThrow(PlaygroundNotFoundException::new);
    }

    @Transactional
    public PlaygroundDetailResponse update(String userId, long id, PlaygroundUpdateRequest request) {
        Playground entity = playgroundRepository.findByIdAndUserId(id, userId)
                .orElseThrow(PlaygroundNotFoundException::new);
        entity.setTitle(normalizeTitle(request.title()));
        entity.setLanguage(validateLanguage(request.language()));
        entity.setSourceCode(validateSource(request.sourceCode()));
        entity.setStdin(validateStdin(request.stdin()));
        Playground saved = playgroundRepository.save(entity);
        log.info("playground_updated userId={} playgroundId={}", userId, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public void delete(String userId, long id) {
        Playground entity = playgroundRepository.findByIdAndUserId(id, userId)
                .orElseThrow(PlaygroundNotFoundException::new);
        playgroundRepository.delete(entity);
        log.info("playground_deleted userId={} playgroundId={}", userId, id);
    }

    private PlaygroundSummaryResponse toSummary(Playground p) {
        return new PlaygroundSummaryResponse(
                p.getId(),
                p.getTitle(),
                p.getLanguage(),
                toInstant(p.getCreatedAt()),
                toInstant(p.getUpdatedAt()));
    }

    private PlaygroundDetailResponse toDetail(Playground p) {
        return new PlaygroundDetailResponse(
                p.getId(),
                p.getTitle(),
                p.getLanguage(),
                p.getSourceCode(),
                p.getStdin(),
                toInstant(p.getCreatedAt()),
                toInstant(p.getUpdatedAt()));
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > properties.getMaxTitleLength()) {
            throw new ValidationException("Title is too long");
        }
        return trimmed;
    }

    private String validateLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new ValidationException("Language is required");
        }
        String normalized = language.trim().toUpperCase();
        if (!ALLOWED_LANGUAGES.contains(normalized)) {
            throw new ValidationException("Unsupported language");
        }
        return normalized;
    }

    private String validateSource(String source) {
        if (source == null) {
            throw new ValidationException("Source code is required");
        }
        if (source.getBytes(StandardCharsets.UTF_8).length > properties.getMaxSourceBytes()) {
            throw new ValidationException("Source code exceeds size limit");
        }
        return source;
    }

    private String validateStdin(String stdin) {
        String value = stdin == null ? "" : stdin;
        if (value.getBytes(StandardCharsets.UTF_8).length > properties.getMaxStdinBytes()) {
            throw new ValidationException("Stdin exceeds size limit");
        }
        return value;
    }

    public String normalizeRunLanguage(String language) {
        return validateLanguage(language);
    }

    public String validateRunSource(String source) {
        return validateSource(source);
    }

    public String validateRunStdin(String stdin) {
        return validateStdin(stdin);
    }
}
