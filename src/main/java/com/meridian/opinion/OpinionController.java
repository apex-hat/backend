package com.meridian.opinion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * README §8 Opinion API — 경로가 /api/proposals/{proposalId}/opinions 와
 * /api/opinions/{opinionId} 두 리소스 루트에 걸쳐 있어 클래스 레벨 매핑을 두지 않는다.
 */
@RestController
public class OpinionController {

    @PostMapping("/api/proposals/{proposalId}/opinions")
    public ResponseEntity<Void> createOpinion(@PathVariable Long proposalId,
                                               @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/api/proposals/{proposalId}/opinions")
    public ResponseEntity<Void> listOpinions(@PathVariable Long proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PutMapping("/api/opinions/{opinionId}")
    public ResponseEntity<Void> updateOpinion(@PathVariable Long opinionId,
                                               @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @DeleteMapping("/api/opinions/{opinionId}")
    public ResponseEntity<Void> deleteOpinion(@PathVariable Long opinionId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
