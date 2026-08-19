package com.meridian.dashboard;

import com.meridian.proposal.ProposalStatus;

/**
 * README §10 응답 현황 조회 응답.
 */
public record DashboardStatusResponse(
        Long proposalId,
        int totalMembers,
        int respondedMembers,
        int responseRate,
        ProposalStatus status
) {
}
