package com.meridian.dashboard;

import java.util.List;

/**
 * README §10 시간대 조회 응답 — {"members": [...]} 래핑을 그대로 따른다.
 */
public record DashboardTimezonesResponse(
        List<DashboardTimezoneMemberResponse> members
) {
}
