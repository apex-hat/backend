package com.meridian.realtime;

/** 클라이언트로 보내는 실시간 이벤트 페이로드. */
public record TeamEvent(
        TeamEventType type,
        Long teamId,
        Long proposalId
) {
}
