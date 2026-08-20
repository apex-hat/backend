package com.meridian.proposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    @Query("SELECT p FROM Proposal p JOIN FETCH p.targetTeam WHERE p.author.id = :authorId")
    List<Proposal> findByAuthor_Id(@Param("authorId") Long authorId);

    @Query("""
            SELECT p FROM Proposal p JOIN FETCH p.targetTeam
            WHERE p.author.id = :userId
               OR (p.status <> com.meridian.proposal.ProposalStatus.DRAFT AND p.targetTeam.id IN :teamIds)
            """)
    List<Proposal> findVisibleToUser(@Param("userId") Long userId, @Param("teamIds") List<Long> teamIds);

    /** 특정 팀 하나로 스코핑된 목록 — 다른 팀에서 내가 작성한 제안까지 새지 않도록 findVisibleToUser와 별도로 둔다. */
    @Query("""
            SELECT p FROM Proposal p JOIN FETCH p.targetTeam
            WHERE p.targetTeam.id = :teamId
              AND (p.author.id = :userId OR p.status <> com.meridian.proposal.ProposalStatus.DRAFT)
            """)
    List<Proposal> findVisibleToUserInTeam(@Param("userId") Long userId, @Param("teamId") Long teamId);

    List<Proposal> findAllByTargetTeam_Id(Long teamId);

    /** 아직 알림을 보내지 않았고, 마감이 (now, threshold] 사이에 있으며, 팀원 응답을 계속 받고 있는 제안만 대상으로 한다. */
    @Query("""
            SELECT p FROM Proposal p JOIN FETCH p.targetTeam
            WHERE p.deadline IS NOT NULL
              AND p.deadline > :now
              AND p.deadline <= :threshold
              AND p.status IN (com.meridian.proposal.ProposalStatus.OPEN, com.meridian.proposal.ProposalStatus.IN_PROGRESS)
              AND (p.deadlineReminderSent IS NULL OR p.deadlineReminderSent = false)
            """)
    List<Proposal> findDueForDeadlineReminder(@Param("now") Instant now, @Param("threshold") Instant threshold);
}
