package com.meridian.opinion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpinionRepository extends JpaRepository<Opinion, Long> {

    boolean existsByProposal_IdAndUser_Id(Long proposalId, Long userId);

    List<Opinion> findAllByProposal_Id(Long proposalId);

    void deleteAllByProposal_Id(Long proposalId);
}
