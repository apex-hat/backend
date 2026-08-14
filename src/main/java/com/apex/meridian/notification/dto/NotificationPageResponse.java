package com.apex.meridian.notification.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static NotificationPageResponse from(
            Page<NotificationResponse> page
    ) {
        return new NotificationPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}