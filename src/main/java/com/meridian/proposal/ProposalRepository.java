package com.meridian.proposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    List<Proposal> findByAuthor_Id(Long authorId);

    @Query("""
            SELECT p FROM Proposal p
            WHERE p.author.id = :userId
               OR (p.status <> com.meridian.proposal.ProposalStatus.DRAFT AND p.targetTeam.id IN :teamIds)
            """)
    List<Proposal> findVisibleToUser(@Param("userId") Long userId, @Param("teamIds") List<Long> teamIds);
}
