package com.meridian.proposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
