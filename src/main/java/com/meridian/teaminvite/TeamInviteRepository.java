package com.meridian.teaminvite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    Optional<TeamInvite> findByTeam_IdAndInvitedUser_Id(Long teamId, Long invitedUserId);

    List<TeamInvite> findAllByInvitedUser_IdAndStatusOrderByCreatedAtDesc(Long invitedUserId, TeamInviteStatus status);

    List<TeamInvite> findAllByTeam_IdOrderByCreatedAtDesc(Long teamId);

    void deleteAllByTeam_Id(Long teamId);
}
