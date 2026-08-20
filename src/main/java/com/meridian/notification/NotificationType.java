package com.meridian.notification;

/**
 * README §11 주요 알림 이벤트
 */
public enum NotificationType {
    PROPOSAL_CREATED,
    PROPOSAL_UPDATED,
    OPINION_REQUESTED,
    DEADLINE_APPROACHING,
    CONSENSUS_SUMMARY_COMPLETED,
    FRIEND_REQUEST,
    TEAM_INVITE,
    /** PM이 본인이 작성하지 않은 의견을 모더레이션 목적으로 수정/삭제했을 때 원 작성자에게 발송 */
    OPINION_UPDATED_BY_PM,
    OPINION_DELETED_BY_PM,
    /** PM이 본인이 작성하지 않은 제안을 모더레이션 목적으로 수정/삭제했을 때 원 작성자에게 발송 */
    PROPOSAL_UPDATED_BY_PM,
    PROPOSAL_DELETED_BY_PM
}
