package com.meridian.teaminvite;

import java.time.Instant;

public record TeamInviteResponse(
        Long id,
        Long teamId,
        String teamName,
        Long invitedUserId,
        String invitedUserName,
        Long invitedById,
        String invitedByName,
        TeamInviteStatus status,
        Instant createdAt,
        Instant respondedAt
) {

    public static TeamInviteResponse from(TeamInvite invite) {
        return new TeamInviteResponse(
                invite.getId(),
                invite.getTeam().getId(),
                invite.getTeam().getName(),
                invite.getInvitedUser().getId(),
                invite.getInvitedUser().getName(),
                invite.getInvitedBy().getId(),
                invite.getInvitedBy().getName(),
                invite.getStatus(),
                invite.getCreatedAt(),
                invite.getRespondedAt()
        );
    }
}
