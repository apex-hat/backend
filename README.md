# Meridian Backend

AI 기반 글로벌 비동기 협업 플랫폼 **Meridian**의 Backend Repository입니다.

Meridian은 글로벌 팀의 협업 과정에서 발생하는 **문화적 표현 차이**, **시차**, **의견 정리의 어려움**을 AI로 보완하여 비동기 의사결정을 지원합니다.

---

## 1. Project Overview

### Meridian

> AI가 문화적 맥락과 시차를 이해하여 글로벌 팀의 비동기 협업을 돕는 AI 협업 플랫폼

### 핵심 기능

* AI 문화 맥락 분석
* 글로벌 팀 비동기 의견 수집
* AI 합의 상태 및 핵심 쟁점 요약
* 완곡한 표현의 숨은 의도 분석
* 글로벌 팀원 시간대 및 응답 현황 확인
* 협업 진행 상황 알림

### 주요 사용자 흐름

```text
제안 작성
   ↓
AI 문화 맥락 분석
   ↓
제안 등록 (DRAFT)
   ↓
제안 게시 (OPEN)
   ↓
팀원 의견 작성
   ↓
시간대 / 응답 현황 확인
   ↓
AI 합의 요약
   ↓
최종 의사결정
```

---

# 2. Tech Stack

## Backend

| 기술                      | 용도                         |
| ----------------------- | -------------------------- |
| Spring Boot             | REST API 및 비즈니스 로직         |
| Java                    | Backend 개발 언어              |
| Firebase Functions      | REST API 진입점 / API Gateway |
| Firebase Authentication | 사용자 인증                     |
| PostgreSQL              | 주요 서비스 데이터 저장              |
| AI API                  | 문화 맥락 / 의도 / 합의 분석 (OpenAI) |

## Frontend

| 기술             | 용도                  |
| -------------- | ------------------- |
| Vite           | Frontend Build Tool |
| React          | UI                  |
| TypeScript     | 정적 타입               |
| CSS Modules    | 스타일링                |
| TanStack Query | 서버 상태 관리            |
| Axios          | HTTP Client         |

---

# 3. Architecture

Meridian은 **기존 REST API 구조를 유지하면서 Firebase를 인증 및 API 진입점으로 활용**합니다.

```text
     ┌─────────────────────┐   로그인 (SDK)   ┌─────────────────────┐
     │   Firebase Auth      │◄───────────────►│      React App       │
     │   (ID Token 발급)     │   ID Token 수신   │  Vite + TypeScript   │
     └─────────────────────┘                  └──────────┬──────────┘
                                                          │
                                              REST API + Authorization:
                                              Bearer <Firebase ID Token>
                                                          │
                                                          ▼
                                              ┌─────────────────────┐
                                              │   Firebase Functions│
                                              │    REST API Entry   │
                                              └──────────┬──────────┘
                                                          │
                                                          ▼
                                              ┌─────────────────────┐
                                              │     Spring Boot     │
                                              │ Business Logic / API│
                                              │ (Token 검증 + JIT    │
                                              │  사용자 프로필 동기화) │
                                              └───────┬───────┬─────┘
                                                       │       │
                                              ┌────────┘       └─────────┐
                                              ▼                          ▼
                                     ┌─────────────────┐        ┌─────────────────┐
                                     │   PostgreSQL    │        │     AI API      │
                                     │   Application   │        │ Context / Intent│
                                     │      Data       │        │   / Consensus  │
                                     └─────────────────┘        └─────────────────┘
```

> Firebase Auth는 요청 경로의 중간 서버가 아니라 **클라이언트가 로그인 시점에 ID Token을 발급받는 대상**입니다. 이후의 모든 API 요청은 React App이 Firebase Functions로 직접 보내며, 발급받은 ID Token을 `Authorization` 헤더에 담아 전달합니다.

### Firebase를 사용하는 이유

해커톤 환경에서 인증 및 API 인프라 구축 시간을 줄이고 핵심 기능 개발에 집중하기 위해 Firebase를 사용합니다.

* Firebase Authentication으로 인증 구현
* Firebase Functions를 REST API 진입점으로 사용
* 기존 `/api/...` endpoint 구조 유지
* PostgreSQL을 서비스 핵심 데이터베이스로 사용
* Spring Boot에서 비즈니스 로직 관리

따라서 Frontend는 Firebase의 구현 방식에 직접적으로 의존하지 않고 기존 REST API를 통해 Backend와 통신합니다.

---

# 4. REST API Design

모든 API는 `/api`를 Base Path로 사용합니다.

```text
/api/{resource}
```

RESTful Resource를 기준으로 endpoint를 구성합니다.

### 기본 규칙

* `GET` : 조회
* `POST` : 생성
* `PUT` : 전체 수정
* `PATCH` : 부분 수정
* `DELETE` : 삭제

예:

```http
GET /api/proposals
POST /api/proposals
GET /api/proposals/{proposalId}
PUT /api/proposals/{proposalId}
DELETE /api/proposals/{proposalId}
```

---

# 5. Authentication

인증은 **Firebase Authentication**을 사용합니다.

사용자는 Firebase Auth를 통해 로그인하고 발급받은 ID Token을 API 요청에 포함합니다.

```http
Authorization: Bearer <Firebase ID Token>
```

### 인증 흐름

```text
User
 ↓
Firebase Authentication
 ↓
Firebase ID Token
 ↓
Frontend
 ↓
Authorization Header
 ↓
Firebase Functions
 ↓
Spring Backend
 ↓
Token 검증
 ↓
API 처리
```

Backend에서는 인증된 사용자의 Firebase UID를 기준으로 사용자 정보를 식별합니다.

> `users.firebase_uid`는 UNIQUE로 관리하고, `users.id`는 Meridian 내부 PK로 사용합니다.

### JIT(Just-In-Time) 사용자 프로필 동기화

`/api/auth/signup` 호출 여부와 무관하게, 인증이 필요한 모든 API는 Token 검증 이후 다음 순서로 동작합니다.

```text
Firebase ID Token 검증
      ↓
firebase_uid로 users 테이블 조회
      ↓
존재하지 않으면 Firebase Token Claim(email, name 등)으로
users row를 자동 생성
      ↓
API 로직 수행
```

이 동작 덕분에 `/api/auth/signup`을 생략하더라도 인증된 사용자는 항상 Postgres에 대응하는 프로필을 가지며, `/api/users/me`, 팀/제안 관련 API가 정상 동작합니다.

---

# 6. API Specification

## 6.1 Auth

Meridian의 실제 인증(계정 생성, 로그인, 세션/토큰 발급)은 **Firebase Authentication**이 담당합니다. 클라이언트는 Firebase SDK로 인증한 뒤 발급된 ID Token을 Backend API에 전달합니다.

아래 `/api/auth/*` endpoint는 Firebase Authentication 자체를 대체하는 인증 API가 아니라, 클라이언트 편의를 위한 **선택적 연계 endpoint**입니다. 사용자 프로필의 최초 생성은 [JIT 사용자 프로필 동기화](#jitjust-in-time-사용자-프로필-동기화)를 통해 항상 보장되므로, MVP에서는 Firebase SDK를 직접 사용하고 이 endpoint를 생략할 수 있습니다.

| 기능 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 회원가입 연계 | POST | `/api/auth/signup` | Firebase 사용자 생성 후 Meridian 사용자 프로필 생성/동기화 |
| 로그인 연계 | POST | `/api/auth/login` | 인증된 Firebase 사용자의 Meridian 사용자 상태 확인 |
| 로그아웃 연계 | POST | `/api/auth/logout` | 애플리케이션 측 로그아웃 상태 처리. 실제 인증 세션 종료는 Firebase SDK가 담당 |

> Backend API의 인증 여부는 보호 endpoint에서 `Authorization: Bearer <Firebase ID Token>`을 검증하는 방식으로 판단합니다.

---

## 6.2 User

| 기능        | Method | Endpoint            | 설명                                   |
| --------- | ------ | ------------------- | ------------------------------------ |
| 사용자 정보 조회 | GET    | `/api/users/me`     | 로그인한 사용자 정보 조회                        |
| 사용자 정보 수정 | PATCH  | `/api/users/me`     | 로그인한 사용자 정보 부분 수정                      |
| 사용자 이메일 검색 | GET    | `/api/users/search` | 이메일로 사용자 검색(팀원 초대 시 userId 확인용)        |

```http
GET /api/users/me
Authorization: Bearer <Firebase ID Token>
```

### 사용자 정보 수정

```http
PATCH /api/users/me
Authorization: Bearer <Firebase ID Token>
```

JIT 사용자 프로필 동기화는 Firebase ID Token의 claim(email, name 등)만 반영하므로, `country`/`timeZone`/`location`/`cultureTag`는 로그인만으로는 채워지지 않는 경우가 많다(클라이언트 SDK로는 custom claim을 직접 설정할 수 없음). 이 endpoint로 로그인 이후 프로필 정보를 채우거나 수정할 수 있다.

전달된 필드만 반영하는 부분 수정이며, 값을 생략하거나 빈 문자열로 보내면 기존 값을 유지한다(명시적으로 지우는 기능은 없음).

요청 예시:

```json
{
  "name": "이민아",
  "country": "KR",
  "timeZone": "Asia/Seoul",
  "location": "Seoul",
  "cultureTag": "high-context"
}
```

### 사용자 이메일 검색

```http
GET /api/users/search?email={email}
Authorization: Bearer <Firebase ID Token>
```

팀원을 초대하려면 `POST /api/teams/{teamId}/members`에 대상의 `userId`가 필요한데, 초대하는 쪽은 보통 상대의 이메일만 알고 있다. 이 endpoint는 정확히 일치하는 이메일로 가입된 사용자를 찾아 `id`/`name`/`email`만 반환한다(`firebaseUid` 등 민감 정보는 제외). 인증된 사용자만 검색할 수 있으며, 일치하는 사용자가 없으면 `404 USER_NOT_FOUND`를 반환한다.

예상 응답:

```json
{
  "id": 12,
  "name": "이민아",
  "email": "teammate@example.com"
}
```

---

## 6.3 Team

| 기능       | Method | Endpoint                              | 설명                                             |
| -------- | ------ | -------------------------------------- | ------------------------------------------------ |
| 팀 생성     | POST   | `/api/teams`                           | 새 팀 생성. 생성 요청자는 자동으로 `role=PM`으로 팀에 추가됨 |
| 팀 목록 조회  | GET    | `/api/teams`                           | 사용자가 속한 팀 목록 조회                          |
| 팀 상세 조회  | GET    | `/api/teams/{teamId}`                  | 팀 정보 조회                                     |
| 팀원 목록 조회 | GET    | `/api/teams/{teamId}/members`          | 팀에 속한 팀원 목록 조회                          |
| 팀원 추가    | POST   | `/api/teams/{teamId}/members`          | 팀에 사용자 추가 (`userId`, `role`)                |
| 팀원 제거    | DELETE | `/api/teams/{teamId}/members/{userId}` | 팀에서 사용자 제거                                |

> 팀원 추가/제거는 해당 팀의 `role=PM`인 사용자만 수행할 수 있습니다.

---

# 7. Proposal API

Meridian의 핵심 도메인입니다.

## 제안 생성

```http
POST /api/proposals
```

새로운 협업 제안을 등록합니다.

- `teamId`는 사용자가 소속된 팀이어야 합니다.
- `targetCultures`는 팀의 국가 목록과 동일한 개념이 아니라 AI 문화 맥락 분석의 대상 문화권을 의미합니다. 작성자가 자유롭게 선택하며 팀원의 실제 `country`와 일치할 필요는 없습니다(예: 팀에 없는 이해관계자의 문화권을 참고용으로 추가 가능). Backend는 팀원 국가와의 일치 여부를 검증하지 않습니다.
- `deadline`은 선택값이며, 지정된 경우 마감 이후 AI 합의 요약을 실행할 수 있습니다.
- `cultureAnalysisIds`는 선택값이며, 등록 전 `proposalId` 없이 수행한 [문화 맥락 분석](#91-문화-맥락-분석) 결과 ID를 전달하면 해당 분석 이력의 `proposal_id`가 생성된 제안으로 채워집니다.
- 생성된 제안은 항상 `DRAFT` 상태로 시작하며, 팀원에게 공개되지 않고 작성자만 조회/수정할 수 있습니다. 팀원에게 공개하려면 [제안 게시](#제안-게시) API를 호출해야 합니다.

### 주요 데이터

```json
{
  "title": "디자인 시안 B 적용",
  "content": "이번 프로젝트의 메인 디자인으로 B안을 적용하는 것은 어떨까요?",
  "teamId": "team-001",
  "targetCultures": [
    "KR",
    "US",
    "IN"
  ],
  "cultureAnalysisIds": ["analysis-001"],
  "deadline": "2026-08-13T18:00:00Z"
}
```

---

## 제안 목록 조회

```http
GET /api/proposals
```

사용자가 접근 가능한 제안 목록을 조회합니다.

---

## 제안 상세 조회

```http
GET /api/proposals/{proposalId}
```

특정 제안의 상세 정보를 조회합니다.

---

## 제안 수정

```http
PUT /api/proposals/{proposalId}
```

제안 정보를 수정합니다. `DRAFT` 상태의 제안만 수정할 수 있습니다. 게시 이후에는 내용을 변경할 수 없습니다.

---

## 제안 삭제

```http
DELETE /api/proposals/{proposalId}
```

제안을 삭제합니다. `DRAFT` 상태의 제안만 삭제할 수 있습니다.

---

## 제안 게시

```http
POST /api/proposals/{proposalId}/publish
```

제안을 팀원에게 공개하고 의견 수집을 시작합니다. `DRAFT` 상태의 제안만 게시할 수 있으며, 게시 후 상태는 `OPEN`으로 변경됩니다.

> 이후 팀원이 첫 의견을 등록하면 상태는 시스템에 의해 자동으로 `IN_PROGRESS`로 전환됩니다. 별도의 API 호출은 필요하지 않습니다.

---

## 제안 완료 처리

```http
POST /api/proposals/{proposalId}/complete
```

AI 합의 요약을 참고하여 최종 의사결정을 확정하고 제안을 종료합니다. `CONSENSUS_READY` 상태의 제안만 완료 처리할 수 있으며, 처리 후 상태는 `COMPLETED`로 변경됩니다.

### 주요 데이터

```json
{
  "decision": "B안을 채택하되, 모바일 UI는 추가 논의 후 반영",
  "decidedBy": "user-001"
}
```

---

# 8. Opinion API

제안에 대한 팀원의 의견을 관리합니다.

### 의견 등록

```http
POST /api/proposals/{proposalId}/opinions
```

### 의견 조회

```http
GET /api/proposals/{proposalId}/opinions
```

### 의견 수정

```http
PUT /api/opinions/{opinionId}
```

### 의견 삭제

```http
DELETE /api/opinions/{opinionId}
```

### 의견 유형

```text
AGREE
DISAGREE
CONDITIONAL_AGREE
```

예시:

```json
{
  "stance": "CONDITIONAL_AGREE",
  "content": "B안에 동의하지만 모바일 화면에서는 추가적인 수정이 필요합니다."
}
```

동일 사용자는 동일 제안에 대해 중복 의견을 등록할 수 없습니다.

---

# 9. AI API

Meridian의 핵심 차별화 기능입니다.

## 9.1 문화 맥락 분석

```http
POST /api/ai/context-analysis
```

제안 내용을 대상 문화권의 관점에서 분석합니다.

> 이 분석은 **제안 최종 등록 전에 수행할 수 있으므로 `proposalId` 없이도 호출 가능**합니다. 제안 등록이 완료되면 해당 분석 이력을 `proposalId`와 연결할 수 있습니다.

### 분석 결과

* 문화권별 해석
* 오해 가능성이 있는 표현
* 위험도
* 수정 문장 제안

예상 응답:

```json
{
  "riskLevel": "HIGH",
  "originalText": "괜찮은 것 같아요. 그런데...",
  "interpretations": [
    {
      "culture": "KR",
      "interpretation": "완곡한 반대 또는 우려로 해석될 가능성이 있음"
    },
    {
      "culture": "US",
      "interpretation": "기본적으로 긍정적인 의견으로 해석될 가능성이 있음"
    }
  ],
  "suggestion": "전체적으로 긍정적이지만 일정 부분 수정이 필요하다고 생각합니다."
}
```

---

## 9.2 AI 합의 요약

```http
POST /api/ai/consensus-summary
```

제안 내용과 팀원들의 의견을 분석하여 합의 상태와 핵심 쟁점을 제공합니다.

"대상 팀원"은 제안의 `targetTeamId`에 소속된 전체 팀원을 의미합니다.

실행 조건은 다음과 같습니다.
- 모든 대상 팀원의 의견이 제출되었거나
- 설정된 `deadline`이 경과한 경우

응답 인원이 부족하여 요약을 실행할 수 없는 경우에는 도메인 오류로 응답합니다.

> 이 조건은 별도의 배치/스케줄러 없이 **API 호출 시점에 검사**합니다. 조건을 만족하면 Proposal 상태를 `CONSENSUS_READY`로 갱신한 뒤 합의 요약을 생성합니다. 이미 `CONSENSUS_READY` 이상인 제안에 대해 다시 호출하면 새로운 합의 요약 이력을 추가로 생성합니다(최신 이력이 현재 합의 요약으로 사용됨).

### 분석 결과

* 합의 여부
* 핵심 쟁점
* 문화적 표현 분석
* 숨겨진 반대 의견
* 권장 후속 조치

예상 응답:

```json
{
  "consensus": "PARTIAL",
  "summary": "전체적으로 B안에 긍정적이나 모바일 화면에 대한 이견이 존재합니다.",
  "keyIssues": [
    "모바일 UI 수정 필요",
    "일정에 대한 우려"
  ],
  "hiddenDisagreements": [
    "일부 팀원이 완곡한 표현으로 반대 의사를 표시함"
  ],
  "nextAction": "모바일 UI 수정안을 추가로 논의할 것을 권장"
}
```

---

## 9.3 숨은 의도 분석

```http
POST /api/ai/intent-analysis
```

완곡하거나 간접적으로 표현된 의견의 실제 의도를 분석합니다.

예:

```text
입력:
"괜찮은 것 같아요. 다만 일정이 조금 걱정되네요."

↓

분석:

표면적 의견:
긍정

잠재적 의견:
일정 측면에서 조건부 반대 또는 우려 가능성
```

AI 결과는 의사결정을 대신하는 것이 아니라 **의견 해석을 보조하는 정보**로 사용합니다.

---

# 10. Dashboard API

## 시간대 조회

```http
GET /api/dashboard/timezones?teamId={teamId}
```

`teamId`에 속한 팀원의 글로벌 시간대 정보를 조회합니다. `teamId`는 필수 쿼리 파라미터이며, 요청 사용자가 해당 팀에 속하지 않은 경우 `403`을 반환합니다.

### 제공 정보

* 국가
* Time Zone
* 현지 시간
* 근무 여부
* 마지막 접속 시간
* 위치 정보

예상 응답:

```json
{
  "members": [
    {
      "userId": "user-001",
      "country": "KR",
      "timeZone": "Asia/Seoul",
      "localTime": "21:30",
      "working": false
    },
    {
      "userId": "user-002",
      "country": "US",
      "timeZone": "America/New_York",
      "localTime": "08:30",
      "working": true
    }
  ]
}
```

---

## 응답 현황 조회

```http
GET /api/dashboard/status?proposalId={proposalId}
```

`proposalId`에 해당하는 제안의 팀원 응답 현황을 조회합니다. `proposalId`는 필수 쿼리 파라미터입니다.

예:

```json
{
  "proposalId": "proposal-001",
  "totalMembers": 8,
  "respondedMembers": 6,
  "responseRate": 75,
  "status": "IN_PROGRESS"
}
```

---

# 11. Notification API

## 알림 목록 조회

```http
GET /api/notifications
```

사용자에게 전달된 알림을 조회합니다.

## 알림 읽음 처리

```http
PATCH /api/notifications/{notificationId}
```

특정 알림을 읽음 상태로 변경합니다.

### 주요 알림 이벤트

```text
PROPOSAL_CREATED
OPINION_REQUESTED
DEADLINE_APPROACHING
CONSENSUS_SUMMARY_COMPLETED
FRIEND_REQUEST
```

> `notifications.type`도 위 값을 그대로 사용합니다.

동일 이벤트에 대한 중복 알림은 방지합니다. `PROPOSAL_CREATED`, `OPINION_REQUESTED`, `CONSENSUS_SUMMARY_COMPLETED`처럼 사용자-제안 조합당 한 번만 발생해야 하는 이벤트는 알림 생성 전 `(user_id, proposal_id, type)` 조합으로 기존 알림 존재 여부를 확인해 중복 생성을 막습니다. `DEADLINE_APPROACHING`처럼 반복 발생이 정상인 이벤트는 대상에서 제외하고 발송 이력(예: 최근 발송 시각)을 기준으로 재발송 여부를 판단합니다.

`FRIEND_REQUEST`는 아래 Friend API에서 친구 요청이 생성될 때 수신자에게 발송됩니다.

---

## Friend API

사용자마다 고유 코드(`friendCode`, 예: `MER-7F3K`)를 가지며, `GET /api/users/me` 응답의 `friendCode` 필드로 확인할 수 있습니다. 이 코드로 서로를 찾아 친구 요청을 보내고 수락하는 API입니다.

| 기능        | Method | Endpoint                          | 설명                                  |
| --------- | ------ | ---------------------------------- | ------------------------------------ |
| 친구 요청 보내기 | POST   | `/api/friends/requests`            | 상대의 `friendCode`로 친구 요청 전송 |
| 받은 요청 목록  | GET    | `/api/friends/requests`            | 나에게 온 PENDING 상태 요청 목록      |
| 요청 수락/거절  | PATCH  | `/api/friends/requests/{requestId}` | `accept: true/false`로 응답        |
| 친구 목록 조회  | GET    | `/api/friends`                     | 수락된(ACCEPTED) 친구 목록          |

```http
POST /api/friends/requests
Authorization: Bearer <Firebase ID Token>
Content-Type: application/json

{
  "friendCode": "MER-7F3K"
}
```

자기 자신에게 보내면 `400 SELF_FRIEND_REQUEST`, 존재하지 않는 코드면 `404 USER_NOT_FOUND`, 이미 친구이거나 요청이 진행 중이면 `409 FRIEND_REQUEST_EXISTS`를 반환합니다. 요청이 성공하면 상대방에게 `FRIEND_REQUEST` 타입 알림이 생성됩니다.

```http
PATCH /api/friends/requests/{requestId}
Authorization: Bearer <Firebase ID Token>
Content-Type: application/json

{
  "accept": true
}
```

수신자 본인만 응답할 수 있으며(`403 FRIEND_REQUEST_ACCESS_DENIED`), 이미 처리된 요청에 다시 응답하면 `409 FRIEND_REQUEST_ALREADY_RESOLVED`를 반환합니다.

---

# 12. 전체 API Endpoint

| Domain       | Method | Endpoint                               | Description |
| ------------ | ------ | -------------------------------------- | ----------- |
| Auth         | POST   | `/api/auth/login`                      | 로그인         |
| Auth         | POST   | `/api/auth/signup`                     | 회원가입        |
| Auth         | POST   | `/api/auth/logout`                     | 로그아웃        |
| User         | GET    | `/api/users/me`                        | 사용자 정보 조회   |
| User         | PATCH  | `/api/users/me`                        | 사용자 정보 부분 수정 |
| User         | GET    | `/api/users/search?email={email}`      | 이메일로 사용자 검색 |
| Team         | POST   | `/api/teams`                           | 팀 생성        |
| Team         | GET    | `/api/teams`                           | 팀 목록 조회     |
| Team         | GET    | `/api/teams/{teamId}`                  | 팀 상세 조회     |
| Team         | GET    | `/api/teams/{teamId}/members`          | 팀원 목록 조회    |
| Team         | POST   | `/api/teams/{teamId}/members`          | 팀원 추가       |
| Team         | DELETE | `/api/teams/{teamId}/members/{userId}` | 팀원 제거       |
| Proposal     | POST   | `/api/proposals`                       | 제안 생성       |
| Proposal     | GET    | `/api/proposals`                       | 제안 목록 조회    |
| Proposal     | GET    | `/api/proposals/{proposalId}`          | 제안 상세 조회    |
| Proposal     | PUT    | `/api/proposals/{proposalId}`          | 제안 수정       |
| Proposal     | DELETE | `/api/proposals/{proposalId}`          | 제안 삭제       |
| Proposal     | POST   | `/api/proposals/{proposalId}/publish`  | 제안 게시 (DRAFT → OPEN) |
| Proposal     | POST   | `/api/proposals/{proposalId}/complete` | 제안 완료 처리 (CONSENSUS_READY → COMPLETED) |
| AI           | POST   | `/api/ai/context-analysis`             | 문화 맥락 분석    |
| AI           | POST   | `/api/ai/consensus-summary`            | AI 합의 요약    |
| AI           | POST   | `/api/ai/intent-analysis`              | 숨은 의도 분석    |
| Opinion      | POST   | `/api/proposals/{proposalId}/opinions` | 의견 등록       |
| Opinion      | GET    | `/api/proposals/{proposalId}/opinions` | 의견 조회       |
| Opinion      | PUT    | `/api/opinions/{opinionId}`            | 의견 수정       |
| Opinion      | DELETE | `/api/opinions/{opinionId}`            | 의견 삭제       |
| Dashboard    | GET    | `/api/dashboard/timezones?teamId={teamId}` | 팀원 시간대 조회 |
| Dashboard    | GET    | `/api/dashboard/status?proposalId={proposalId}` | 응답 현황 조회 |
| Notification | GET    | `/api/notifications`                   | 알림 목록 조회    |
| Notification | PATCH  | `/api/notifications/{notificationId}`  | 알림 읽음 처리    |
| Friend       | POST   | `/api/friends/requests`                | 친구 요청 보내기   |
| Friend       | GET    | `/api/friends/requests`                | 받은 요청 목록    |
| Friend       | PATCH  | `/api/friends/requests/{requestId}`    | 요청 수락/거절    |
| Friend       | GET    | `/api/friends`                         | 친구 목록 조회    |

---

# 13. Data Model

Meridian의 핵심 데이터 구조는 다음과 같습니다.

```text
User
 ├── Team Membership ──> Team
 ├── Proposal (author)
 ├── Opinion
 └── Notification

Team
 ├── Team Members
 └── Proposal

Proposal
 ├── Author (User)
 ├── Target Team (Team)
 ├── Target Cultures
 ├── Culture Analyses
 ├── Opinions
 ├── Consensus Summaries
 └── Notifications

Opinion
 ├── User
 └── Proposal
```

### 주요 Entity

#### User

```text
id
firebaseUid      # Firebase Authentication 사용자 식별자 (UNIQUE)
name
email
country         # 선택
timeZone        # 선택, 기본값 UTC (JIT 생성 시 Firebase Token Claim에 없으면 UTC로 설정)
location        # 선택
cultureTag      # 선택
createdAt
updatedAt
```

> 비밀번호는 PostgreSQL `users` 테이블에 저장하지 않습니다. 인증 정보는 Firebase Authentication이 관리하고, Backend DB에는 Firebase UID와 서비스 프로필 정보만 저장합니다.

#### Team

```text
id
name
country         # 선택: 팀의 대표/운영 국가
cultureTag      # 선택
createdAt
updatedAt
```

#### TeamMember

```text
teamId
userId
role
joinedAt
```

`teamId + userId`를 복합 PK로 사용합니다.

#### Proposal

```text
id
title
content
authorId
targetTeamId
status
deadline        # 선택
decision        # 선택: 완료 처리 시 입력된 최종 의사결정 내용
decidedBy       # 선택: 완료 처리한 사용자 ID
completedAt     # 선택: 완료 처리 일시
createdAt
updatedAt
```

대상 문화권은 `proposal_target_cultures`로 분리하여 여러 문화권을 저장합니다.

`decision`, `decidedBy`, `completedAt`은 `POST /api/proposals/{proposalId}/complete` 호출 시 채워집니다.

#### Opinion

```text
id
proposalId
userId
stance
comment
attachmentUrl   # 선택
createdAt
updatedAt
```

`proposalId + userId` UNIQUE 제약으로 동일 사용자의 동일 제안 중복 의견을 방지합니다.

#### Notification

```text
id
userId
proposalId      # 선택
type
title
content
isRead
createdAt
```

### 13.1 ERD

아래 ERD는 현재 API/기능 명세와 일치하도록 정리한 논리 모델입니다.

#### 1) Users (사용자)

**설명**

서비스 사용자와 Firebase Authentication 연계 정보를 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 사용자 고유 ID (PK) |
| `firebase_uid` | Firebase Authentication 사용자 ID (UNIQUE, NOT NULL) |
| `name` | 사용자 이름 |
| `email` | 이메일 (UNIQUE) |
| `country` | 소속/대표 국가 (선택) |
| `timezone` | 타임존 (예: `Asia/Seoul`, 선택, 기본값 `UTC`) |
| `location` | 위치 정보 (선택) |
| `culture_tag` | 문화권/커뮤니케이션 스타일 태그 (선택) |
| `created_at` | 가입/생성 일시 |
| `updated_at` | 수정 일시 |

**관계**

- `Users` : `Teams` = N:M (`team_members` 경유)
- `Users` : `Proposals` = 1:N (작성자)
- `Users` : `Opinions` = 1:N
- `Users` : `Notifications` = 1:N

> 비밀번호는 이 테이블에 저장하지 않습니다. 실제 인증 정보는 Firebase Authentication이 관리합니다.

#### 2) Teams (팀)

**설명**

글로벌 협업의 기본 단위를 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 팀 고유 ID (PK) |
| `name` | 팀명 |
| `country` | 팀의 대표/운영 국가 (선택) |
| `culture_tag` | 팀의 대표 문화/커뮤니케이션 스타일 태그 (선택) |
| `created_at` | 생성 일시 |
| `updated_at` | 수정 일시 |

**관계**

- `Teams` : `Users` = N:M (`team_members` 경유)
- `Teams` : `Proposals` = 1:N

> 팀은 여러 국가의 사용자를 포함할 수 있으므로 팀의 `country`와 사용자 개별 `country/timezone`은 별도 정보로 취급합니다.

#### 3) Team_Members (팀 멤버)

**설명**

사용자와 팀의 소속 관계를 저장하는 조인 테이블입니다.

| 컬럼 | 설명 |
| --- | --- |
| `team_id` | 팀 ID (PK, FK) |
| `user_id` | 사용자 ID (PK, FK) |
| `role` | 팀 내 역할 (예: `PM`, `MEMBER`) |
| `joined_at` | 팀 가입 일시 |

**제약**

- PK: (`team_id`, `user_id`)
- 동일 사용자의 동일 팀 중복 소속 금지

> 팀 생성 시 생성자는 `role=PM`으로 자동 등록되며, 이후 팀원 추가/제거는 [Team API](#63-team)의 `POST/DELETE /api/teams/{teamId}/members` 로 관리합니다.

#### 4) Proposals (제안)

**설명**

팀원에게 공유되는 협업 제안/안건을 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 제안 고유 ID (PK) |
| `title` | 제안 제목 |
| `content` | 제안 내용 |
| `author_id` | 작성자 ID (FK → `users.id`) |
| `target_team_id` | 대상 팀 ID (FK → `teams.id`) |
| `status` | `DRAFT/OPEN/IN_PROGRESS/CONSENSUS_READY/COMPLETED` |
| `deadline` | 응답 마감 기한 (선택) |
| `decision` | 완료 처리 시 입력된 최종 의사결정 내용 (선택) |
| `decided_by` | 완료 처리한 사용자 ID (FK → `users.id`, 선택) |
| `completed_at` | 완료 처리 일시 (선택) |
| `created_at` | 작성/생성 일시 |
| `updated_at` | 수정 일시 |

**관계**

- 하나의 제안은 하나의 작성자(User)를 가집니다. (N:1)
- 하나의 제안은 하나의 대상 팀(Team)을 가집니다. (N:1)
- 하나의 제안은 여러 대상 문화권을 가질 수 있습니다. (1:N)
- 하나의 제안은 여러 의견을 가집니다. (1:N)
- 하나의 제안은 여러 문화 맥락 분석 결과를 가질 수 있습니다. (1:N)
- 하나의 제안은 여러 AI 합의 요약 결과를 가질 수 있습니다. (1:N)

#### 5) Proposal_Target_Cultures (제안 대상 문화권)

**설명**

제안이 전달/검토될 대상 문화권 목록을 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 고유 ID (PK) |
| `proposal_id` | 제안 ID (FK → `proposals.id`) |
| `culture_name` | 문화권 코드/명 (예: `KR`, `US`, `IN`, `BR`) |

**제약**

- (`proposal_id`, `culture_name`) UNIQUE

#### 6) Culture_Analyses (AI 문화 맥락 분석)

**설명**

제안의 문화적 오해 가능성을 분석한 결과를 저장합니다. 제안 등록 전 분석이 가능하므로 `proposal_id`는 분석 시점에 없을 수 있습니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 고유 ID (PK) |
| `proposal_id` | 제안 ID (FK, NULL 허용) |
| `original_text` | 분석 대상 원문 |
| `risk_level` | `LOW/MEDIUM/HIGH` |
| `interpretation` | 문화권별 해석 결과 (JSON/JSONB 권장) |
| `flagged_phrase` | 오해 가능 표현 (JSON/JSONB 권장) |
| `suggested_rewrite` | AI 수정 제안 |
| `applied` | 해당 수정안이 최종 제안에 적용되었는지 여부 |
| `created_at` | 분석 일시 |

**관계**

- 하나의 제안은 여러 분석 이력을 가질 수 있습니다. (1:N)
- 등록 전 분석은 `proposal_id = NULL`일 수 있으며, `POST /api/proposals` 호출 시 `cultureAnalysisIds`로 전달하면 `proposal_id`가 채워집니다.

#### 7) Opinions (의견)

**설명**

팀원이 제안에 대해 남기는 비동기 의견을 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 의견 고유 ID (PK) |
| `proposal_id` | 제안 ID (FK → `proposals.id`) |
| `user_id` | 작성자 ID (FK → `users.id`) |
| `stance` | `AGREE/DISAGREE/CONDITIONAL_AGREE` |
| `comment` | 의견 내용 |
| `attachment_url` | 첨부 파일 URL (선택) |
| `created_at` | 작성 일시 |
| `updated_at` | 수정 일시 |

**제약**

- (`proposal_id`, `user_id`) UNIQUE
- 의견 유형과 코멘트를 하나의 최종 의견 레코드로 관리합니다.

#### 8) Consensus_Summaries (AI 합의 요약)

**설명**

수집된 의견을 AI가 분석하여 합의 상태와 핵심 쟁점을 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 고유 ID (PK) |
| `proposal_id` | 제안 ID (FK → `proposals.id`) |
| `consensus_status` | `AGREED/PARTIAL/DISAGREED/PENDING` |
| `summary` | 전체 요약 |
| `key_issues` | 핵심 쟁점 (JSON/JSONB) |
| `cultural_analysis` | 문화적 표현 분석 (JSON/JSONB) |
| `hidden_opposition` | 숨은 반대/우려 분석 (JSON/JSONB) |
| `recommended_actions` | 권장 후속 조치 |
| `created_at` | 생성 일시 |

**관계**

- 하나의 제안은 여러 합의 요약 이력을 가질 수 있습니다. (1:N)
- 최신 `created_at` 결과를 현재 합의 요약으로 사용할 수 있습니다.

#### 9) Notifications (알림)

**설명**

사용자에게 전달된 협업 관련 알림을 저장합니다.

| 컬럼 | 설명 |
| --- | --- |
| `id` | 알림 고유 ID (PK) |
| `user_id` | 수신자 ID (FK → `users.id`) |
| `proposal_id` | 관련 제안 ID (FK, nullable) |
| `type` | `PROPOSAL_CREATED/OPINION_REQUESTED/DEADLINE_APPROACHING/CONSENSUS_SUMMARY_COMPLETED` |
| `title` | 알림 제목 |
| `content` | 알림 내용 |
| `is_read` | 읽음 여부 |
| `created_at` | 발송/생성 일시 |

**관계**

- 여러 알림은 하나의 사용자에게 속합니다. (N:1)
- 알림은 특정 제안과 연결될 수 있습니다. (N:1, nullable)

### ERD 관계 요약

```text
Users
  │
  ├──< Team_Members >── Teams
  │                       │
  │                       └──< Proposals
  │                              ├──< Proposal_Target_Cultures
  │                              ├──< Culture_Analyses
  │                              ├──< Opinions >── Users
  │                              └──< Consensus_Summaries
  │
  └──< Notifications >── Proposals (nullable)
```

# 14. Proposal Status

제안의 진행 상태는 아래 값만 사용합니다.

```text
DRAFT
   ↓
OPEN
   ↓
IN_PROGRESS
   ↓
CONSENSUS_READY
   ↓
COMPLETED
```

### 상태 설명

| Status | 설명 | 전이 방법 |
| --- | --- | --- |
| `DRAFT` | 작성 중이며 아직 의견 수집을 시작하지 않은 제안 | `POST /api/proposals` 호출 시 초기값 |
| `OPEN` | 의견 수집이 시작된 제안 | `POST /api/proposals/{proposalId}/publish` |
| `IN_PROGRESS` | 하나 이상의 응답을 수집하고 있는 상태 | 팀원이 첫 의견 등록 시 시스템이 자동 전이 |
| `CONSENSUS_READY` | 모든 팀원 응답이 완료되었거나 마감 기한이 지나 AI 합의 요약을 실행할 수 있는 상태 | `POST /api/ai/consensus-summary` 호출 시 조건 충족되면 시스템이 자동 전이 |
| `COMPLETED` | 최종 의사결정이 완료된 상태 | `POST /api/proposals/{proposalId}/complete` |

> `REVIEWING`, `CONSENSUS_DONE`, `CLOSED` 등은 사용하지 않습니다. 초안 검토가 필요하더라도 별도의 상태값으로 확장하지 않고 `DRAFT` 내부 단계로 관리합니다.
>
> `DRAFT`, `COMPLETED`를 제외한 모든 상태 전이는 사용자의 명시적 API 호출 없이 시스템이 자동으로 처리하며, 별도의 배치/스케줄러 없이 관련 API(의견 등록, 합의 요약) 호출 시점에 조건을 검사합니다.
>
> 의견이 하나도 제출되지 않은 채 `deadline`이 경과한 경우, `IN_PROGRESS`를 거치지 않고 `OPEN`에서 바로 `CONSENSUS_READY`로 전이될 수 있습니다.

# 15. Error Response

API 오류 응답은 가능한 한 동일한 형식을 유지합니다.

```json
{
  "success": false,
  "error": {
    "code": "PROPOSAL_NOT_FOUND",
    "message": "Proposal not found."
  }
}
```

### 주요 HTTP Status

| Status | 의미              |
| ------ | --------------- |
| `200`  | 요청 성공           |
| `201`  | 리소스 생성 성공       |
| `204`  | 성공했지만 응답 데이터 없음 |
| `400`  | 잘못된 요청          |
| `401`  | 인증 필요           |
| `403`  | 권한 없음           |
| `404`  | 리소스 없음          |
| `409`  | 리소스 충돌          |
| `500`  | 서버 오류           |

---

# 16. Environment Variables

환경 변수는 `.env` 또는 배포 환경의 Secret Manager를 통해 관리합니다.

예시:

```env
SPRING_PROFILES_ACTIVE=local

DB_URL=jdbc:postgresql://localhost:5432/meridian
DB_USERNAME=postgres
DB_PASSWORD=

FIREBASE_PROJECT_ID=
FIREBASE_CLIENT_EMAIL=
FIREBASE_PRIVATE_KEY=

OPENAI_API_KEY=
OPENAI_MODEL=
```

실제 API Key 및 Secret 값은 Git Repository에 업로드하지 않습니다.

`.gitignore`에 다음 항목을 포함합니다.

```gitignore
.env
.env.*
*.key
firebase-service-account.json
```

---

# 17. Local Development

## Requirements

* Java 17+
* Gradle
* Node.js 20+
* PostgreSQL
* Firebase CLI

---

## Backend 실행

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

기본 로컬 서버:

```text
http://localhost:8080
```

---

## Firebase Functions 실행

```bash
cd functions
npm install
npm run build
firebase emulators:start
```

Firebase Functions는 외부 요청을 받아 Spring Backend API로 전달하는 REST API 진입점으로 사용합니다.

개발 환경에서는 필요에 따라 Spring Boot를 직접 호출하여 Backend 로직을 테스트할 수도 있습니다.

---

# 18. API Development Flow

새로운 API를 추가할 때 다음 순서를 따릅니다.

```text
1. API Specification 정의
        ↓
2. Request / Response DTO 정의
        ↓
3. Controller 구현
        ↓
4. Service 구현
        ↓
5. Repository 구현
        ↓
6. PostgreSQL 연동
        ↓
7. Firebase Auth 권한 검증
        ↓
8. Firebase Functions endpoint 연결
        ↓
9. API 테스트
```

---

# 19. Recommended Development Order

해커톤 MVP에서는 핵심 사용자 흐름을 먼저 완성합니다.

### Phase 1 — Authentication

```text
Firebase Auth
    ↓
Token 검증 + JIT 사용자 프로필 동기화
    ↓
/api/users/me
    ↓
(선택) /api/auth/*
```

> `/api/auth/*`는 선택 endpoint이므로 JIT 프로필 동기화 구현 이후 필요 시 추가합니다.

### Phase 2 — Team

```text
POST   /api/teams
GET    /api/teams
GET    /api/teams/{teamId}
GET    /api/teams/{teamId}/members
POST   /api/teams/{teamId}/members
DELETE /api/teams/{teamId}/members/{userId}
```

### Phase 3 — Proposal

```text
POST   /api/proposals
GET    /api/proposals
GET    /api/proposals/{proposalId}
PUT    /api/proposals/{proposalId}
DELETE /api/proposals/{proposalId}
POST   /api/proposals/{proposalId}/publish
```

### Phase 4 — Opinion

```text
POST /api/proposals/{proposalId}/opinions
GET  /api/proposals/{proposalId}/opinions
PUT  /api/opinions/{opinionId}
DELETE /api/opinions/{opinionId}
```

### Phase 5 — AI

```text
POST /api/ai/context-analysis
POST /api/ai/intent-analysis
POST /api/ai/consensus-summary
    ↓
POST /api/proposals/{proposalId}/complete
```

### Phase 6 — Dashboard

```text
GET /api/dashboard/timezones?teamId={teamId}
GET /api/dashboard/status?proposalId={proposalId}
```

### Phase 7 — Notification

```text
GET   /api/notifications
PATCH /api/notifications/{notificationId}
```

---

# 20. MVP Priority

해커톤에서는 모든 기능을 동일한 수준으로 구현하기보다 핵심 사용자 경험을 우선합니다.

### P0 — 반드시 구현

* Firebase Authentication (+ JIT 사용자 프로필 동기화)
* 사용자 정보
* 팀 생성 / 조회 / 팀원 관리
* 제안 생성 / 조회 / 게시
* 의견 등록 / 조회
* AI 문화 맥락 분석
* AI 합의 요약
* 제안 완료 처리(최종 의사결정)

### P1 — 데모 완성도 향상

* 시간대 대시보드
* 응답 현황
* 숨은 의도 분석
* 알림

### P2 — 향후 확장

* 이메일 알림
* Slack 연동
* Microsoft Teams 연동
* 다국어 번역
* 조직별 커뮤니케이션 스타일 학습
* 프로젝트별 AI 협업 리포트
* AI 회의록 및 의사결정 이력

---

# 21. Core User Scenario

Meridian의 핵심 데모 시나리오는 다음과 같습니다.

```text
[한국 디자이너]

제안 작성
"이 디자인으로 진행하는 게 괜찮을 것 같습니다.
다만 일정이 조금 걱정되네요."

        ↓

[AI 문화 맥락 분석]

한국:
완곡한 우려 또는 조건부 반대 가능성

미국:
긍정적인 의견으로 우선 해석될 가능성

        ↓

[AI 수정 제안]

"디자인 방향에는 동의하지만,
현재 일정으로 진행하는 것에는 우려가 있습니다."

        ↓

[제안 등록 (DRAFT)]

        ↓

[제안 게시 (OPEN)]

        ↓

[미국 / 인도 / 브라질 팀원]

각자의 현지 시간에 의견 작성

        ↓

[Time Zone Dashboard]

New York   08:30  근무 중   응답 완료
Seoul      21:30  근무 종료 응답 완료
Mumbai     18:00  근무 중   미응답

        ↓

[AI Consensus]

부분 합의

핵심 쟁점:
- 디자인 방향에는 대부분 동의
- 일정에 대한 이견 존재

        ↓

[최종 의사결정]
```

---

# 22. Repository Structure

권장 Backend 구조입니다.

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── meridian/
│   │   │           ├── auth/
│   │   │           ├── user/
│   │   │           ├── team/
│   │   │           ├── proposal/
│   │   │           ├── opinion/
│   │   │           ├── ai/
│   │   │           ├── dashboard/
│   │   │           ├── notification/
│   │   │           ├── common/
│   │   │           └── config/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   │
│   └── test/
│
├── functions/
│   ├── src/
│   ├── package.json
│   └── tsconfig.json
│
├── build.gradle
├── settings.gradle
├── firebase.json
├── .gitignore
└── README.md
```

---

# 23. Package Responsibility

각 도메인은 가능한 한 독립적으로 관리합니다.

```text
auth/
 ├── controller
 ├── service
 ├── dto
 └── ...

proposal/
 ├── controller
 ├── service
 ├── repository
 ├── entity
 └── dto
```

### Common

공통적으로 사용하는 기능은 `common`에서 관리합니다.

```text
common/
├── exception/
├── response/
├── security/
├── config/
└── util/
```

---

# 24. Git Convention

### Branch

```text
main
develop
feature/*
fix/*
hotfix/*
```

예:

```text
feature/proposal-api
feature/opinion-api
feature/ai-context-analysis
fix/firebase-auth
```

### Commit

권장 형식:

```text
feat: 제안 생성 API 구현
feat: Firebase 인증 연동
feat: AI 문화 맥락 분석 구현
fix: 의견 중복 등록 오류 수정
refactor: Proposal Service 구조 개선
docs: API README 작성
```

---

# 25. Security

다음 정보는 Repository에 직접 저장하지 않습니다.

* Firebase Service Account Key
* Firebase Private Key
* Database Password
* AI API Key
* JWT / Authentication Secret
* 기타 민감한 환경 변수

모든 인증이 필요한 API는 Firebase ID Token을 검증합니다.

```http
Authorization: Bearer <token>
```

또한 사용자가 접근할 수 있는 리소스인지 Backend에서 최종적으로 권한을 검증합니다.

예:

```text
사용자 A
   ↓
GET /api/proposals/{proposalId}
   ↓
해당 Proposal이 사용자 A의 Team에 속하는지 확인
   ↓
허용 / 거부
```

Frontend에서 버튼을 숨기는 것만으로 권한을 처리하지 않습니다.

---

# 26. API Design Principles

Meridian Backend는 다음 원칙을 따릅니다.

### 1. Resource 중심 설계

```text
/api/proposals
/api/opinions
/api/teams
/api/notifications
```

### 2. HTTP Method의 의미 준수

```text
GET     → 조회
POST    → 생성
PUT     → 수정
PATCH   → 부분 수정
DELETE  → 삭제
```

### 3. 인증과 권한 분리

```text
Firebase Auth
→ 사용자가 누구인지 확인

Spring Backend
→ 해당 사용자가 무엇을 할 수 있는지 확인
```

### 4. AI는 의사결정 보조

AI 분석 결과를 최종 의사결정 자체로 취급하지 않습니다.

```text
사용자 의견
      ↓
AI 분석
      ↓
해석 / 요약 / 추천
      ↓
사용자 최종 판단
```

---

# 27. API Documentation

API 명세는 향후 Swagger / OpenAPI를 통해 관리합니다.

예정:

```text
Swagger UI
http://localhost:8080/swagger-ui/index.html
```

API 변경 시 다음 항목을 함께 수정합니다.

* Endpoint
* Request DTO
* Response DTO
* HTTP Status
* Error Code
* README API Specification

---

# 28. Future Architecture

해커톤 이후 서비스가 확장될 경우 다음과 같이 확장할 수 있습니다.

```text
                    ┌──────────────┐
                    │   Frontend   │
                    └──────┬───────┘
                           │
                           ▼
                 ┌──────────────────┐
                 │ Firebase Functions│
                 │   API Gateway     │
                 └────────┬─────────┘
                          │
             ┌────────────┴────────────┐
             ▼                         ▼
      ┌──────────────┐         ┌──────────────┐
      │ Spring API   │         │ AI Service   │
      │ Application  │         │              │
      └──────┬───────┘         └──────────────┘
             │
             ▼
      ┌──────────────┐
      │ PostgreSQL   │
      └──────────────┘
```

향후 트래픽과 기능이 증가하면 다음 영역을 독립적으로 확장할 수 있습니다.

* AI 분석 서비스
* Notification Service
* Time Zone Service
* 사용자/조직 관리
* 외부 협업 도구 Integration

---

# 29. License

This project is developed for a hackathon project.

License information will be added after the project is finalized.
