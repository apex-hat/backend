package com.meridian.user;

/**
 * PATCH /api/users/me 요청 Body. 부분 수정이므로 각 필드는 전달된 것만 반영하고,
 * null은 "값 없음"이 아니라 "변경하지 않음"으로 취급한다.
 */
public record UserUpdateRequest(
        String name,
        String country,
        String timeZone,
        String location,
        String cultureTag
) {
}
