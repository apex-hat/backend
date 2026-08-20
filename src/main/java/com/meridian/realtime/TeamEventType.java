package com.meridian.realtime;

/**
 * WebSocket으로 팀에 실시간 브로드캐스트하는 이벤트 종류.
 * 프론트는 종류를 구분하지 않고 수신 시 목록을 다시 조회(refetch)하는 용도로만 쓴다.
 */
public enum TeamEventType {
    PROPOSAL_CREATED,
    PROPOSAL_UPDATED,
    PROPOSAL_DELETED,
    OPINION_CREATED,
    OPINION_UPDATED,
    OPINION_DELETED,
    MESSAGE_CREATED
}
