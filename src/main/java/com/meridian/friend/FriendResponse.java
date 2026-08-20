package com.meridian.friend;

import com.meridian.user.User;

import java.time.Instant;

/** ACCEPTED 상태인 FriendRequest에서, 현재 사용자 기준 "상대방" 정보만 뽑아낸 응답. */
public record FriendResponse(
        Long userId,
        String name,
        String email,
        String friendCode,
        Instant since
) {

    public static FriendResponse from(FriendRequest request, Long currentUserId) {
        User other = request.getRequester().getId().equals(currentUserId)
                ? request.getAddressee()
                : request.getRequester();
        return new FriendResponse(other.getId(), other.getName(), other.getEmail(), other.getFriendCode(), request.getRespondedAt());
    }
}
