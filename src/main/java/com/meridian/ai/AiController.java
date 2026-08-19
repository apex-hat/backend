package com.meridian.ai;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * README §9 AI API
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/context-analysis")
    public ResponseEntity<ContextAnalysisResponse> contextAnalysis(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody ContextAnalysisRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiService.contextAnalysis(authorizationHeader, request));
    }

    @PostMapping("/consensus-summary")
    public ResponseEntity<Void> consensusSummary(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/intent-analysis")
    public ResponseEntity<IntentAnalysisResponse> intentAnalysis(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody IntentAnalysisRequest request
    ) {
        return ResponseEntity.ok(aiService.intentAnalysis(authorizationHeader, request));
    }
}
