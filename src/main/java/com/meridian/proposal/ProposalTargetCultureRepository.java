package com.meridian.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalTargetCultureRepository extends JpaRepository<ProposalTargetCulture, Long> {

    List<ProposalTargetCulture> findByProposal_Id(Long proposalId);

    void deleteByProposal_Id(Long proposalId);
}
