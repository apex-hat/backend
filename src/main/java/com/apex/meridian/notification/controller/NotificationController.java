package com.apex.meridian.notification.controller;

import com.apex.meridian.notification.dto.MarkAllReadResponse;
import com.apex.meridian.notification.dto.NotificationPageResponse;
import com.apex.meridian.notification.dto.NotificationResponse;
import com.apex.meridian.notification.dto.UnreadCountResponse;
import com.apex.meridian.notification.service.NotificationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponse getNotifications(
            Authentication authentication,

            @RequestParam(defaultValue = "false")
            boolean unreadOnly,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {

        Long currentUserId =
                resolveCurrentUserId(authentication);

        return notificationService.getNotifications(
                currentUserId,
                unreadOnly,
                page,
                size
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(
            Authentication authentication
    ) {

        Long currentUserId =
                resolveCurrentUserId(authentication);

        return new UnreadCountResponse(
                notificationService.getUnreadCount(
                        currentUserId
                )
        );
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            Authentication authentication,
            @PathVariable Long notificationId
    ) {

        Long currentUserId =
                resolveCurrentUserId(authentication);

        return notificationService.markAsRead(
                currentUserId,
                notificationId
        );
    }

    @PatchMapping("/read-all")
    public MarkAllReadResponse markAllAsRead(
            Authentication authentication
    ) {

        Long currentUserId =
                resolveCurrentUserId(authentication);

        return new MarkAllReadResponse(
                notificationService.markAllAsRead(
                        currentUserId
                )
        );
    }

    /*
     * TODO:
     * Firebase Auth가 구현되면
     * Firebase UID → users.id(Long)
     * 변환 로직으로 교체한다.
     */
    private Long resolveCurrentUserId(
            Authentication authentication
    ) {

        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Firebase 사용자와 DB userId 연결이 아직 구현되지 않았습니다."
        );
    }
}