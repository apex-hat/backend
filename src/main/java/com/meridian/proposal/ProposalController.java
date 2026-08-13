package com.meridian.proposal;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * README §7 Proposal API
 */
@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    @PostMapping
    public ResponseEntity<ProposalResponse> createProposal(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody ProposalCreateRequest request
    ) {
        ProposalResponse response = proposalService.createProposal(authorizationHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProposalResponse>> listProposals(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(proposalService.listProposals(authorizationHeader));
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<ProposalResponse> getProposal(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long proposalId
    ) {
        return ResponseEntity.ok(proposalService.getProposal(authorizationHeader, proposalId));
    }

    @PutMapping("/{proposalId}")
    public ResponseEntity<ProposalResponse> updateProposal(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long proposalId,
            @RequestBody ProposalUpdateRequest request
    ) {
        return ResponseEntity.ok(proposalService.updateProposal(authorizationHeader, proposalId, request));
    }

    @DeleteMapping("/{proposalId}")
    public ResponseEntity<Void> deleteProposal(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long proposalId
    ) {
        proposalService.deleteProposal(authorizationHeader, proposalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{proposalId}/publish")
    public ResponseEntity<Void> publishProposal(@PathVariable Long proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/{proposalId}/complete")
    public ResponseEntity<Void> completeProposal(@PathVariable Long proposalId,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
