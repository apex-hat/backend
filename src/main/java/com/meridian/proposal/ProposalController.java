package com.meridian.proposal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * README §7 Proposal API
 */
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    @PostMapping
    public ResponseEntity<Void> createProposal(@RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping
    public ResponseEntity<Void> listProposals() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{proposalId}")
    public ResponseEntity<Void> getProposal(@PathVariable Long proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PutMapping("/{proposalId}")
    public ResponseEntity<Void> updateProposal(@PathVariable Long proposalId,
                                                @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @DeleteMapping("/{proposalId}")
    public ResponseEntity<Void> deleteProposal(@PathVariable Long proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
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
