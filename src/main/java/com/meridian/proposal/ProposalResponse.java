package com.meridian.proposal;

import java.time.Instant;
import java.util.List;

public record ProposalResponse(
        Long id,
        String title,
        String content,
        Long authorId,
        Long targetTeamId,
        ProposalStatus status,
        List<String> targetCultures,
        Instant deadline,
        String decision,
        Long decidedBy,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProposalResponse from(Proposal proposal, List<String> targetCultures) {
        return new ProposalResponse(
                proposal.getId(),
                proposal.getTitle(),
                proposal.getContent(),
                proposal.getAuthor().getId(),
                proposal.getTargetTeam().getId(),
                proposal.getStatus(),
                targetCultures,
                proposal.getDeadline(),
                proposal.getDecision(),
                proposal.getDecidedBy() != null ? proposal.getDecidedBy().getId() : null,
                proposal.getCompletedAt(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt()
        );
    }
}
