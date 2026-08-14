package com.meridian.opinion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * README §8 Opinion API — 경로가 /api/proposals/{proposalId}/opinions 와
 * /api/opinions/{opinionId} 두 리소스 루트에 걸쳐 있어 클래스 레벨 매핑을 두지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class OpinionController {

    private final OpinionService opinionService;

    @PostMapping("/api/proposals/{proposalId}/opinions")
    public ResponseEntity<OpinionResponse> createOpinion(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long proposalId,
            @Valid @RequestBody OpinionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(opinionService.createOpinion(authorizationHeader, proposalId, request));
    }

    @GetMapping("/api/proposals/{proposalId}/opinions")
    public ResponseEntity<List<OpinionResponse>> listOpinions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long proposalId
    ) {
        return ResponseEntity.ok(opinionService.listOpinions(authorizationHeader, proposalId));
    }

    @PutMapping("/api/opinions/{opinionId}")
    public ResponseEntity<OpinionResponse> updateOpinion(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long opinionId,
            @Valid @RequestBody OpinionRequest request
    ) {
        return ResponseEntity.ok(opinionService.updateOpinion(authorizationHeader, opinionId, request));
    }

    @DeleteMapping("/api/opinions/{opinionId}")
    public ResponseEntity<Void> deleteOpinion(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long opinionId
    ) {
        opinionService.deleteOpinion(authorizationHeader, opinionId);
        return ResponseEntity.noContent().build();
    }
}
