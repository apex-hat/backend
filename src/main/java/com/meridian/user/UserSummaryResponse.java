package com.meridian.user;

/**
 * 팀원 검색/초대 UI에서 이메일로 사용자를 찾을 때 쓰는 최소 응답.
 * UserResponse(본인 프로필 전체)와 달리 firebaseUid 등 민감 정보는 노출하지 않는다.
 */
public record UserSummaryResponse(Long id, String name, String email) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getName(), user.getEmail());
    }
}
