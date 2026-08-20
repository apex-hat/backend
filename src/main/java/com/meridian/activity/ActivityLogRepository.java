package com.meridian.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findAllByTeam_IdOrderByCreatedAtDesc(Long teamId);

    void deleteAllByTeam_Id(Long teamId);
}
