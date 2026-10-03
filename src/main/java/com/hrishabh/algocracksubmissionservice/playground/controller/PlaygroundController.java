package com.hrishabh.algocracksubmissionservice.playground.controller;

import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.*;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.LanguageDescriptorResponse;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunRequest;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunResponse;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundLanguageCatalogService;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundRunService;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/playgrounds")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "playground", name = "api-enabled", havingValue = "true")
public class PlaygroundController {

    private final PlaygroundWorkspaceService workspaceService;
    private final PlaygroundRunService runService;
    private final PlaygroundLanguageCatalogService languageCatalogService;

    @PostMapping
    public ResponseEntity<PlaygroundDetailResponse> create(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestBody PlaygroundCreateRequest request) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<PlaygroundPageResponse> list(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = CurrentUser.require(userIdHeader);
        if (page < 0 || size < 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(workspaceService.list(userId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaygroundDetailResponse> get(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @PathVariable long id) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(workspaceService.getOwned(userId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaygroundDetailResponse> update(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @PathVariable long id,
            @RequestBody PlaygroundUpdateRequest request) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(workspaceService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @PathVariable long id) {
        String userId = CurrentUser.require(userIdHeader);
        workspaceService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run")
    public ResponseEntity<PlaygroundRunResponse> run(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestBody PlaygroundRunRequest request) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(runService.run(userId, request));
    }

    @GetMapping("/languages")
    public ResponseEntity<List<LanguageDescriptorResponse>> languages(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader) {
        CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(languageCatalogService.listLanguages());
    }
}
