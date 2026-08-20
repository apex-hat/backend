package com.meridian.teammessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {

    @Query("select m from TeamMessage m join fetch m.sender where m.team.id = :teamId order by m.createdAt asc")
    List<TeamMessage> findAllByTeam_IdOrderByCreatedAtAsc(@Param("teamId") Long teamId);

    void deleteAllByTeam_Id(Long teamId);
}
