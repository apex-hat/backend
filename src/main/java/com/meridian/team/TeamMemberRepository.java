package com.meridian.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findAllByUser_Id(Long userId);

    List<TeamMember> findAllByTeam_Id(Long teamId);

    Optional<TeamMember> findByTeam_IdAndUser_Id(Long teamId, Long userId);

    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);

    boolean existsByTeam_IdAndUser_IdAndRole(Long teamId, Long userId, String role);
}
