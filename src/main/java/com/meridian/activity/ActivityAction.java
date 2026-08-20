package com.meridian.activity;

/**
 * 팀 내에서 다른 팀원에게 영향을 주는 관리(모더레이션) 행위. 팀원 누구나 조회 가능한 감사 기록(§ 활동 로그)에 남는다.
 */
public enum ActivityAction {
    MEMBER_REMOVED,
    MEMBER_LEFT,
    PM_TRANSFERRED,
    OPINION_UPDATED_BY_PM,
    OPINION_DELETED_BY_PM,
    PROPOSAL_UPDATED_BY_PM,
    PROPOSAL_DELETED_BY_PM
}
