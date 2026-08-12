package com.meridian.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * README §9 AI API
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @PostMapping("/context-analysis")
    public ResponseEntity<Void> contextAnalysis(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/consensus-summary")
    public ResponseEntity<Void> consensusSummary(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/intent-analysis")
    public ResponseEntity<Void> intentAnalysis(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
