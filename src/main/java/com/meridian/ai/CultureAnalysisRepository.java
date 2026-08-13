package com.meridian.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CultureAnalysisRepository extends JpaRepository<CultureAnalysis, Long> {

    List<CultureAnalysis> findByProposal_Id(Long proposalId);
}
