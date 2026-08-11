-- ============================================
-- Meridian DB Schema (PostgreSQL)
-- ERDCloud / ERD 작성용 DDL
-- ============================================

-- ---------- 1. USERS ----------
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    country         VARCHAR(100),
    timezone        VARCHAR(50),          -- 예: 'Asia/Seoul', UTC 오프셋 대신 IANA 타임존 문자열 권장
    culture_tag     VARCHAR(50),          -- 예: 'high-context' / 'low-context'
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- 2. TEAMS ----------
CREATE TABLE teams (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    country         VARCHAR(100),
    culture_tag     VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- 3. TEAM_MEMBERS (N:M 조인 테이블) ----------
CREATE TABLE team_members (
    team_id         BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(30) DEFAULT 'MEMBER',  -- 'PM', 'MEMBER' 등
    joined_at       TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, user_id)
);

-- ---------- 4. PROPOSALS ----------
CREATE TABLE proposals (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    author_id       BIGINT NOT NULL REFERENCES users(id),
    target_team_id  BIGINT NOT NULL REFERENCES teams(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','REVIEWING','OPEN','CONSENSUS_DONE','CLOSED')),
    deadline        TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- 5. PROPOSAL_TARGET_CULTURES (제안이 대상으로 하는 문화권, N:M) ----------
CREATE TABLE proposal_target_cultures (
    id              BIGSERIAL PRIMARY KEY,
    proposal_id     BIGINT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    culture_name    VARCHAR(50) NOT NULL   -- 예: '미국', '인도', '브라질'
);

-- ---------- 6. CULTURE_ANALYSES (AI 문화 맥락 분석 결과) ----------
CREATE TABLE culture_analyses (
    id                  BIGSERIAL PRIMARY KEY,
    proposal_id         BIGINT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    original_text       TEXT NOT NULL,
    risk_level          VARCHAR(20) NOT NULL CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    interpretation      TEXT,              -- 문화권별 해석 설명
    flagged_phrase      TEXT,              -- 오해 가능성이 있는 문장/표현
    suggested_rewrite   TEXT,              -- AI 수정 제안
    applied             BOOLEAN NOT NULL DEFAULT false,  -- 사용자가 수정안을 적용했는지
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- 7. OPINIONS (비동기 의견) ----------
CREATE TABLE opinions (
    id              BIGSERIAL PRIMARY KEY,
    proposal_id     BIGINT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    stance          VARCHAR(20) NOT NULL CHECK (stance IN ('AGREE','DISAGREE','CONDITIONAL')),
    comment         TEXT,
    attachment_url  VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (proposal_id, user_id)   -- 동일 사용자 중복 제출 방지
);

-- ---------- 8. CONSENSUS_SUMMARIES (AI 합의 요약) ----------
CREATE TABLE consensus_summaries (
    id                      BIGSERIAL PRIMARY KEY,
    proposal_id             BIGINT NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    consensus_status        VARCHAR(20) NOT NULL
                                CHECK (consensus_status IN ('AGREED','DISAGREED','PENDING')),
    key_issues              TEXT,   -- JSON 문자열로 저장 (핵심 쟁점 배열)
    cultural_analysis       TEXT,   -- JSON 문자열 (문화적 표현 분석)
    hidden_opposition       TEXT,   -- JSON 문자열 (숨겨진 반대 의견)
    recommended_actions     TEXT,   -- 다음 논의가 필요한 사항
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- 9. NOTIFICATIONS ----------
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposal_id     BIGINT REFERENCES proposals(id) ON DELETE CASCADE,
    type            VARCHAR(30) NOT NULL
                        CHECK (type IN ('NEW_PROPOSAL','OPINION_REQUEST','DEADLINE_SOON','CONSENSUS_DONE')),
    message         VARCHAR(300) NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================
-- 인덱스 (조회 성능용)
-- ============================================
CREATE INDEX idx_proposals_target_team ON proposals(target_team_id);
CREATE INDEX idx_proposals_author ON proposals(author_id);
CREATE INDEX idx_opinions_proposal ON opinions(proposal_id);
CREATE INDEX idx_opinions_user ON opinions(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
CREATE INDEX idx_culture_analyses_proposal ON culture_analyses(proposal_id);
CREATE INDEX idx_consensus_summaries_proposal ON consensus_summaries(proposal_id);
