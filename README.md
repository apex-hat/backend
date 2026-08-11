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
제안 등록
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
| AI API                  | 문화 맥락 / 의도 / 합의 분석         |

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
                    ┌─────────────────────┐
                    │      React App       │
                    │ Vite + TypeScript   │
                    └──────────┬──────────┘
                               │
                               │ REST API
                               ▼
                    ┌─────────────────────┐
                    │   Firebase Auth     │
                    │   Authentication    │
                    └──────────┬──────────┘
                               │
                         ID Token
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

---

# 6. API Specification

## 6.1 Auth

| 기능   | Method | Endpoint           | 설명      |
| ---- | ------ | ------------------ | ------- |
| 회원가입 | POST   | `/api/auth/signup` | 회원가입    |
| 로그인  | POST   | `/api/auth/login`  | 사용자 로그인 |
| 로그아웃 | POST   | `/api/auth/logout` | 로그아웃    |

> Firebase Authentication을 사용하므로 실제 인증 처리는 Firebase Auth가 담당하고, REST API는 서비스 사용자 정보 및 인증 상태와 연계됩니다.

---

## 6.2 User

| 기능        | Method | Endpoint        | 설명             |
| --------- | ------ | --------------- | -------------- |
| 사용자 정보 조회 | GET    | `/api/users/me` | 로그인한 사용자 정보 조회 |

```http
GET /api/users/me
Authorization: Bearer <Firebase ID Token>
```

---

## 6.3 Team

| 기능      | Method | Endpoint     | 설명              |
| ------- | ------ | ------------ | --------------- |
| 팀 목록 조회 | GET    | `/api/teams` | 사용자가 속한 팀 목록 조회 |

---

# 7. Proposal API

Meridian의 핵심 도메인입니다.

## 제안 생성

```http
POST /api/proposals
```

새로운 협업 제안을 등록합니다.

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

제안 정보를 수정합니다.

---

## 제안 삭제

```http
DELETE /api/proposals/{proposalId}
```

제안을 삭제합니다.

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
  "type": "CONDITIONAL_AGREE",
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
GET /api/dashboard/timezones
```

팀원의 글로벌 시간대 정보를 조회합니다.

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
GET /api/dashboard/status
```

제안별 팀원 응답 현황을 조회합니다.

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
```

동일 이벤트에 대한 중복 알림은 방지합니다.

---

# 12. 전체 API Endpoint

| Domain       | Method | Endpoint                               | Description |
| ------------ | ------ | -------------------------------------- | ----------- |
| Auth         | POST   | `/api/auth/login`                      | 로그인         |
| Auth         | POST   | `/api/auth/signup`                     | 회원가입        |
| Auth         | POST   | `/api/auth/logout`                     | 로그아웃        |
| User         | GET    | `/api/users/me`                        | 사용자 정보 조회   |
| Team         | GET    | `/api/teams`                           | 팀 목록 조회     |
| Proposal     | POST   | `/api/proposals`                       | 제안 생성       |
| Proposal     | GET    | `/api/proposals`                       | 제안 목록 조회    |
| Proposal     | GET    | `/api/proposals/{proposalId}`          | 제안 상세 조회    |
| Proposal     | PUT    | `/api/proposals/{proposalId}`          | 제안 수정       |
| Proposal     | DELETE | `/api/proposals/{proposalId}`          | 제안 삭제       |
| AI           | POST   | `/api/ai/context-analysis`             | 문화 맥락 분석    |
| AI           | POST   | `/api/ai/consensus-summary`            | AI 합의 요약    |
| AI           | POST   | `/api/ai/intent-analysis`              | 숨은 의도 분석    |
| Opinion      | POST   | `/api/proposals/{proposalId}/opinions` | 의견 등록       |
| Opinion      | GET    | `/api/proposals/{proposalId}/opinions` | 의견 조회       |
| Opinion      | PUT    | `/api/opinions/{opinionId}`            | 의견 수정       |
| Opinion      | DELETE | `/api/opinions/{opinionId}`            | 의견 삭제       |
| Dashboard    | GET    | `/api/dashboard/timezones`             | 팀원 시간대 조회   |
| Dashboard    | GET    | `/api/dashboard/status`                | 응답 현황 조회    |
| Notification | GET    | `/api/notifications`                   | 알림 목록 조회    |
| Notification | PATCH  | `/api/notifications/{notificationId}`  | 알림 읽음 처리    |

---

# 13. Data Model

핵심 데이터 구조는 다음과 같습니다.

```text
User
 ├── Team Membership
 └── Opinion

Team
 ├── Members
 └── Proposals

Proposal
 ├── Team
 ├── Author
 ├── Opinions
 ├── AI Context Analysis
 └── AI Consensus Summary

Opinion
 ├── User
 └── Proposal

Notification
 └── User
```

### 주요 Entity

#### User

```text
id
firebaseUid
name
email
country
timeZone
location
createdAt
updatedAt
```

#### Team

```text
id
name
createdAt
updatedAt
```

#### Proposal

```text
id
teamId
authorId
title
content
targetCultures
deadline
status
createdAt
updatedAt
```

#### Opinion

```text
id
proposalId
userId
type
content
createdAt
updatedAt
```

#### Notification

```text
id
userId
type
title
content
isRead
createdAt
```

---

# 14. Proposal Status

제안의 진행 상태는 다음과 같이 관리합니다.

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

| Status            | 설명          |
| ----------------- | ----------- |
| `DRAFT`           | 작성 중인 제안    |
| `OPEN`            | 의견 수집 시작    |
| `IN_PROGRESS`     | 팀원의 의견 수집 중 |
| `CONSENSUS_READY` | AI 합의 요약 가능 |
| `COMPLETED`       | 의사결정 완료     |

---

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

AI_API_KEY=
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
/api/auth/*
    ↓
/api/users/me
```

### Phase 2 — Team

```text
/api/teams
```

### Phase 3 — Proposal

```text
POST   /api/proposals
GET    /api/proposals
GET    /api/proposals/{proposalId}
PUT    /api/proposals/{proposalId}
DELETE /api/proposals/{proposalId}
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
```

### Phase 6 — Dashboard

```text
GET /api/dashboard/timezones
GET /api/dashboard/status
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

* Firebase Authentication
* 사용자 정보
* 팀 조회
* 제안 생성 / 조회
* 의견 등록 / 조회
* AI 문화 맥락 분석
* AI 합의 요약

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

[제안 등록]

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
