package com.meridian.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByUser_Id(Long userId);

    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);
}
