package com.meridian.dashboard;

/**
 * README §10 시간대 조회 members[] 원소.
 * "근무 여부"/"마지막 접속 시간"은 현재 데이터 모델(User entity)에 근거가 없어 포함하지 않는다
 * — 임의의 근무시간 규칙을 만들거나 새 컬럼을 추가하지 않기로 한 결정에 따른 것.
 */
public record DashboardTimezoneMemberResponse(
        Long userId,
        String country,
        String timeZone,
        String localTime,
        String location
) {
}
