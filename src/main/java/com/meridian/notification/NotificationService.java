package com.meridian.notification;

import com.meridian.common.exception.DomainException;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * README §11 Notification API. 조회/읽음 처리만 담당한다 — 알림 생성(발송) 트리거는
 * 다른 도메인 서비스(Proposal/Opinion/AI)에서 필요한 시점에 별도로 구현한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserService userService;
    private final NotificationRepository notificationRepository;

    public List<NotificationResponse> listNotifications(String authorizationHeader) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        return notificationRepository.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(String authorizationHeader, Long notificationId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> DomainException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found."));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw DomainException.forbidden("NOTIFICATION_ACCESS_DENIED", "본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.setIsRead(true);
        return NotificationResponse.from(notification);
    }
}
