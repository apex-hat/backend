package com.apex.meridian.notification.service;

import com.apex.meridian.notification.domain.Notification;
import com.apex.meridian.notification.domain.NotificationType;
import com.apex.meridian.notification.dto.NotificationPageResponse;
import com.apex.meridian.notification.dto.NotificationResponse;
import com.apex.meridian.notification.repository.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    // 알림 목록 조회
    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(
            Long currentUserId,
            boolean unreadOnly,
            int page,
            int size
    ) {

        PageRequest pageable = PageRequest.of(page, size);

        Page<Notification> notifications;

        if (unreadOnly) {
            notifications =
                    notificationRepository
                            .findByUserIdAndReadFalseOrderByCreatedAtDesc(
                                    currentUserId,
                                    pageable
                            );
        } else {
            notifications =
                    notificationRepository
                            .findByUserIdOrderByCreatedAtDesc(
                                    currentUserId,
                                    pageable
                            );
        }

        return NotificationPageResponse.from(
                notifications.map(NotificationResponse::from)
        );
    }

    // 읽지 않은 알림 개수
    @Transactional(readOnly = true)
    public long getUnreadCount(
            Long currentUserId
    ) {

        return notificationRepository
                .countByUserIdAndReadFalse(currentUserId);
    }

    // 알림 하나 읽음 처리
    @Transactional
    public NotificationResponse markAsRead(
            Long currentUserId,
            Long notificationId
    ) {

        Notification notification =
                notificationRepository
                        .findByIdAndUserId(
                                notificationId,
                                currentUserId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "알림을 찾을 수 없습니다."
                                )
                        );

        notification.markAsRead();

        return NotificationResponse.from(notification);
    }

    // 모든 알림 읽음 처리
    @Transactional
    public int markAllAsRead(
            Long currentUserId
    ) {

        return notificationRepository
                .markAllAsRead(currentUserId);
    }

    // 제안 게시 후 의견 요청 알림
    @Transactional
    public void notifyOpinionRequested(
            Long proposalId,
            Collection<Long> recipientUserIds
    ) {

        for (Long userId : recipientUserIds) {

            Notification notification =
                    new Notification(
                            userId,
                            proposalId,
                            NotificationType.OPINION_REQUEST,
                            "새 제안에 대한 의견을 남겨주세요."
                    );

            notificationRepository.save(notification);
        }
    }

    // 전원 응답 완료 또는 48시간 경과 후
    @Transactional
    public void notifySummaryReady(
            Long proposalId,
            Collection<Long> recipientUserIds
    ) {

        for (Long userId : recipientUserIds) {

            Notification notification =
                    new Notification(
                            userId,
                            proposalId,
                            NotificationType.CONSENSUS_DONE,
                            "의견 수집이 완료되어 AI 합의 요약을 확인할 수 있습니다."
                    );

            notificationRepository.save(notification);
        }
    }
}
