package com.apex.meridian.notification.repository;

import com.apex.meridian.notification.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // 사용자의 전체 알림 조회
    Page<Notification> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    // 사용자의 읽지 않은 알림만 조회
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    // 읽지 않은 알림 개수
    long countByUserIdAndReadFalse(
            Long userId
    );

    // 특정 사용자의 특정 알림 조회
    Optional<Notification> findByIdAndUserId(
            Long id,
            Long userId
    );

    // 해당 사용자의 모든 읽지 않은 알림을 읽음 처리
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.read = true
            WHERE n.userId = :userId
            AND n.read = false
            """)
    int markAllAsRead(
            @Param("userId") Long userId
    );
}