# PMS — AI 구현용 PRD (v1.0 전사본)

> **원본 PDF 전사 (2026-08-02)** — 원본: "PMS AI용 PRD.pdf" (작성 2026-06-21, v1.0 확정 표기·푸터는 v0.1로 불일치).
> 이 파일은 **현행화 전 원본 보존용** — 내용을 수정하지 말 것. 현행화는 `docs/PRD-pms.md`로
> 이관하며 수행한다 (ROADMAP P단계, 충돌 목록은 PROGRESS 결정 기록 2026-08-02 참조).

| 항목 | 내용 |
|------|------|
| 버전 | v1.0 (확정) |
| 범위 | 솔루션사업부 1차 |
| 규모 | 40명 · 1인 개발 |
| 스택 | Spring Boot 4 · React · PG |
| 인증 | 자체 로그인 · JWT |
| 작성일 | 2026-06-21 |

AI 코딩 도구(Claude Code, Cursor 등)가 그대로 읽고 PMS를 구현하도록 작성한 구현명세. 모호함을 제거하고 유저스토리·수용기준(Given/When/Then)·데이터모델·API계약·구현순서로 명세한다. 기반: 「PMS 기획서 v2」·「PMS 아키텍처 설계서 v2」.

## 0. AI 에이전트에게 주는 지시

**고정값 — 임의 변경 금지.** 너는 이 PRD를 구현하는 시니어 풀스택 엔지니어다. 아래 스택·규칙을 고정값으로 받아들이고, 명세되지 않은 선택은 "가장 단순·표준적인 방법"을 택하되 결정을 주석으로 남긴다. 각 기능은 대응하는 수용기준(AC)을 테스트로 구현하며, AC 없는 코드는 작성하지 않는다. 각 마일스톤 끝에서 `./gradlew test` + Modulith 경계 검증이 통과해야 다음으로 넘어가고, 이전 마일스톤 테스트가 깨지면 새 기능 진행을 멈추고 회귀부터 고친다. 명세 밖 세부 결정은 단순·표준을 택해 `// ASSUMPTION:` 주석으로 남기되, 12장 항목은 임의 구현하지 말고 질문한다.

**고정 기술 스택**
- Backend: Java 25 · Spring Boot 4.0 · Spring Modulith 2.0 · JPA
- DB: PostgreSQL(운영) · H2/Testcontainers(테스트)
- Frontend: React + TypeScript + Vite (CSR SPA)
- Build: Gradle · Test: JUnit 5 · AssertJ · Modulith Test
- 배포: Nginx → Spring Boot → PostgreSQL (Docker Compose)

**아키텍처 규칙 (위반=실패)**
- 모듈러 모놀리식 · 6모듈 · Modulith 경계 강제
- `api→application→domain←infra`, domain은 Spring/JPA import 0
- 모듈 간 객체참조 금지, ID로만 연결(질의는 포트)
- 단일 DB · 모듈별 스키마 · 모듈 간 물리 FK 금지
- 이벤트는 사후 fan-out만, 즉시·원자적은 동기 호출
- 권한은 서버 최종 판정, 프론트는 UI 노출 제어만

## 1. 제품 개요

**한 줄 정의** — 여러 고객사 프로젝트를 인력(M/M)·가동률·유지보수 이력 관점에서 관리하는 사내 도구. 스프레드시트를 대체한다.

**목표 (1차)**: 한 프로젝트가 수주확정 → 진행(배정·가동률) → 완료·검수 → 유지보수 이관 → 이력관리로 끊김 없이 흐르는 한 줄기를 완성. 배정 변경 시 가동률 2초 내 갱신, 오버부킹 자동 감지.

**성공 지표**: 40명이 시트 대신 사용 · "한 줄기" 데모 성공 · 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정100) 검증.

**In Scope**: 프로젝트 CRUD · 인력 배정 · 월별합산/직급보정 가동률 · 오버부킹 감지 · 유지보수 이관·이력 · 두 축 권한 · 감사로그 · 인앱 알림 구조.

**Out of Scope (구현 금지)**: 태스크/칸반 · sales 모듈 · MS본부(2차) · 파일 업로드 · 메일/Slack 알림 · SSO · 소속 시점이력 · 프로젝트별 권한예외 · 4역할 세분화 · MSA.

## 2. 사용자 · 권한 모델

두 축을 **절대 하나로 합치지 않는다**. 행위 허용 = 가시성 통과 AND 권한 허용.

| 축 | 값 | 결정 | 소유 모듈 |
|----|----|------|-----------|
| 조직 가시성 | 본부장 / 팀장 / 팀원 | 무엇이 보이나 | identity |
| 프로젝트 권한 | 관리자 / 담당자 | 무엇을 할 수 있나 | project |

| 기능 | 관리자 | 담당자 |
|------|--------|--------|
| 범위 내 프로젝트 조회 | O | 본인/팀 |
| 프로젝트 등록/수정/삭제 | O | ✕ |
| 인력 배정 / M/M 입력 | O | ✕ |
| 진척률 수정 | O | 본인 참여만 |
| 유지보수 이관/이력 | O | ✕ |

## 3. 시스템 구성 (요약)

```
[사용자] --HTTPS--> [Nginx] --> [React SPA] --REST/JWT--> [Spring Boot (6 모듈)] --JPA--> [PostgreSQL]
```

Frontend=화면·검증·표시(권한은 UI노출만) / Backend=단일앱 6모듈 / DB=단일PG·모듈별스키마 / 인증=자체 로그인+JWT(stateless) / 스케줄러=일1회(마감알림)만 / 파일저장소 없음.

## 4. 도메인 모델

모든 수정 가능 엔티티는 `version:long`(낙관적 락). 모듈 간 참조는 `*Id`.

- **identity**: `Division`(name, inScope) · `Team`(divisionId) · `Grade`(coeff) · `Person`(teamId, gradeId, orgRole, capacity) · `User`(personId, loginId, passwordHash)
- **project**: `Project`(account·name·productType·contractMM·기간·status·progress·deleted·version) · `ProjectAssignment`(personId·role{관리자,담당자}·기간·monthlyMM·status)
- **resource**: `Capacity`(personId·month·availableMM). 가동률은 배정 합산으로 계산(저장 엔티티 아님).
- **maintenance**: `Maintenance`(sourceProjectId·maintainerId·기간·sla·status) · `MaintenanceLog`(date·type·processorId·status·note, append-only)
- **notification**: `Notification`(recipientId·type·refType·refId·message·read·createdAt)
- **common**: `AuditLog`(entityType·entityId·action·actorId·source{WEB,MCP}·before·after, append-only) · `CommonCode`

## 5. 상태 전이 (Project)

```
계약대기 → 수주확정 → 진행중 → 완료 → 유지보수중
```

- 진행률 100% → 진행중→완료 **자동 전이** + `ProjectCompleted` 발행.
- 완료 상태에서만 이관 가능 → 완료→유지보수중.
- **역방향 전이 금지**(불변식). 모든 전이 AuditLog `STATE_CHANGE`.

## 6. 기능 요구사항 — 유저스토리 + 수용기준(AC)

각 Given·When·Then = 테스트 1개. AC 없는 코드 금지.

### EPIC A · 프로젝트

**US-A1 관리자로서 새 프로젝트를 등록한다** [관리자]
- A1-1 Given 유효 입력 When `POST /api/projects` Then `201`, 상태=계약대기, AuditLog CREATE 1건
- A1-2 Given 같은 고객사·같은 이름(정규화 후, soft-deleted 제외) When 생성 Then `409 DUPLICATE_NAME`
- A1-3 Given 없는 accountId/pmId When 생성 Then `422 REF_NOT_FOUND`
- A1-4 Given 참여자 목록 When 생성 Then 각자 지정 role로 배정, 생성자=기본 관리자
- A1-5 Given 담당자 토큰 When 생성 Then `403`

**US-A2 담당자로서 본인 참여 프로젝트 진행률을 갱신한다** [담당자]
- A2-1 Given 본인 참여 When `PATCH /progress {progress:90,version}` Then `200`
- A2-2 Given progress=100 When 저장 Then 완료 자동전이 + `ProjectCompleted` + STATE_CHANGE
- A2-3 Given 본인 미참여 When 수정 Then `403`
- A2-4 Given progress<0 or >100 When 저장 Then `400`

**US-A3 가시성 범위 내에서 프로젝트를 조회한다**
- A3-1 Given 팀장 When `GET /projects` Then 자기 팀 범위만 page 봉투
- A3-2 Given 범위 밖 id When 상세조회 Then `404` (은닉)

**US-A4 관리자로서 프로젝트를 소프트 삭제한다** [관리자]
- A4-1 When `DELETE /projects/{id}` Then `204`, deleted=true, 목록·중복검사 제외, AuditLog DELETE

### EPIC B · 인력 배정

**US-B1 관리자로서 인력을 배정한다** [관리자]
- B1-1 When `POST /projects/{id}/assignments` Then `201` + `MemberAssignedToProject`
- B1-2 Given 종료 안된 동일 personId 존재 When 재배정 Then `409 DUPLICATE_ASSIGNMENT` (키=projectId+personId+status≠종료)
- B1-3 When 커밋 후 Then resource가 해당 월 가동률 재계산

**US-B2 배정 종료 시 이후 월 가동률에서 빠진다** [관리자]
- B2-1 When `DELETE /assignments/{id}` Then status=종료, `AssignmentClosed`, 종료월 이후 제외

### EPIC C · 가동률

**US-C1 특정 월의 가동률을 조회한다**
- C1-1 When `GET /utilization?month=&personId=` Then 기본=Σ배정MM÷가용×100, 보정=÷(가용×coeff)
- C1-2 Given A0.5+B0.7, 가용1.0, coeff1.2 Then 기본=120, 보정=100
- C1-3 Given `overbooked=true` Then 보정>100인 사람만
- C1-4 Given 배정 변경 Then 2초 내 가동률 반영

### EPIC D · 유지보수

**US-D1 완료 프로젝트를 유지보수로 이관한다** [관리자]
- D1-1 Given status=완료 When `POST /handover` Then Maintenance+초기Log+상태전이 **한 트랜잭션**, 커밋 후 `MaintenanceHandedOver`
- D1-2 Given status≠완료 When 이관 Then `409 NOT_COMPLETED`, 아무것도 안 바뀜(원자성)

**US-D2 유지보수 이력을 등록/조회한다** [관리자]
- D2-1 When `POST /maintenances/{id}/logs` Then `201`, append-only
- D2-2 Given 기존 로그 When 수정/삭제 Then 불가(API 없음), 보정은 새 로그로만

### EPIC E · 조직 이동

**US-E1 사람의 소속 팀을 이동한다** [관리자]
- E1-1 When `PUT /people/{id}/team` Then teamId 변경, 가시성 즉시 반영, AuditLog STATE_CHANGE
- E1-2 Given 진행 중 배정 보유 When 이동 Then 허용+경고. 과거 집계는 현재 소속 기준(시점 미보존)

### EPIC F · 알림 (구조만)

**US-F1 이벤트를 구독해 인앱 알림을 적재한다**
- F1-1 Given `OverbookingDetected` Then 팀 관리자에게 Notification 생성
- F1-2 Given 동일 이벤트 중복 Then 알림 1건만(멱등)
- F1-3 When `GET /notifications?read=false` Then 본인 미읽음만, `PATCH /{id}/read` → `204`

### EPIC G · 감사 (횡단)

**US-G1 모든 변경이 자동 기록된다**
- G1-1 Given 임의 변경 Then AuditLog 1건 자동 생성(before/after)
- G1-2 Then AuditLog 수정·삭제 API 부재(append-only)

## 7. API 계약 (공통 규약)

- **인증**: `Authorization: Bearer <JWT>` 필수, 없으면 `401`.
- **응답(비대칭)**: 단건=원본 / 목록=page 봉투(content,page,size,totalElements,totalPages) / 에러=`{error:{code,message,field,traceId}}`.
- **상태코드**: 200 조회·수정 / 201 생성 / 204 삭제·읽음 / 4xx 에러.
- **동시성**: 본문에 `version`, 불일치 시 `409 STALE_VERSION` → reload-and-retry.
- **페이징**: `?page=0&size=20&sort=field,desc` · **가시성**: 조회 선필터, 범위 밖 `404`.

| code | HTTP | 의미 |
|------|------|------|
| VALIDATION_ERROR | 400 | 입력 형식 오류 |
| UNAUTHENTICATED | 401 | 토큰 없음/만료 |
| FORBIDDEN_FIELD | 403 | 권한 없는 행위 |
| NOT_FOUND | 404 | 없음/가시성 밖 |
| DUPLICATE_* / NOT_COMPLETED | 409 | 중복·상태 위반 |
| STALE_VERSION | 409 | 동시 수정 충돌 |
| REF_NOT_FOUND | 422 | 참조 대상 없음 |

```
GET/POST    /api/projects              GET/PUT/PATCH/DELETE /api/projects/{id} (/progress)
GET/POST    /api/projects/{id}/assignments    PUT/DELETE /api/assignments/{id}
GET         /api/utilization?month=&personId=&teamId=&overbooked=
POST        /api/projects/{id}/handover
GET/POST    /api/maintenances/{id}/logs
GET /api/people    PUT /api/people/{id}/team    GET /api/teams    GET /api/grades
GET /api/notifications    PATCH /api/notifications/{id}/read    POST /api/auth/login
```

## 8. 이벤트 명세

| 이벤트 | 발행자 | 구독자 | 효과 |
|--------|--------|--------|------|
| `MemberAssignedToProject` | project | resource, notification | 가동률 재계산, 알림 |
| `AssignmentClosed` | project | resource | 종료월 이후 제외 |
| `OverbookingDetected` | resource | notification | 관리자 알림 |
| `ProjectCompleted` | project | notification | 완료/이관 안내 |
| `MaintenanceHandedOver` | maintenance | notification | 초기 이력·알림 |

발행=트랜잭션 커밋 후(`AFTER_COMMIT`) · 신뢰성=Modulith Event Publication Registry 재시도 · 재계산 멱등.

## 9. 비기능 요구사항

성능: 조회 P95 < 1초 · 가동률 반영 < 2초 · 권한 서버 최종판정 · 비밀번호 해시 · 낙관적 락(NFR-13) · 감사 append-only·source 구분 · 도메인 순수 단위테스트 · Modulith 경계 검증 · compose 로컬 기동 · 1요청=1트랜잭션 · 앱 무상태 재기동 복구

## 10. 구현 순서 (Milestones)

순서대로 진행. 각 단계 끝에서 해당 AC 테스트가 초록이어야 다음으로.

- **M0 스캐폴딩**: Gradle 6모듈 + Modulith + PG/H2 + Docker Compose + 레이어 골격. 경계 테스트 통과.
- **M1 identity + 인증**: 조직/사람/직급/User, 자체 로그인→JWT, 가시성 필터. (로그인·401·403)
- **M2 project (핵심)**: Project/Assignment CRUD, 상태전이, 2종 권한, 낙관적 락, AuditLog. (EPIC A·B·G)
- **M3 resource (가동률)**: Capacity, 합산·보정 가동률, 오버부킹, 이벤트 재계산. (EPIC C)
- **M4 maintenance (1차 목표)**: 완료→이관(동기·원자적), Maintenance/Log(append-only). (EPIC D)
- **M5 notification + 조직이동 + 스케줄러**: 이벤트→알림 적재, 마감임박 @Scheduled, 팀 이동. (EPIC E·F)
- **M6 Frontend + 통합**: React SPA — 화면·라우트는 부록 A. Nginx 프록시. 부록 B 시드로 E2E 스모크(한 줄기 시연).

## 11. 완료 정의 (Definition of Done)

- ☐ 모든 EPIC의 AC가 테스트로 구현되고 초록
- ☐ Spring Modulith 경계 위반 테스트 0건
- ☐ domain 레이어에 Spring/JPA import 0
- ☐ 40명 데모 데이터로 "수주→배정→가동률→완료→이관→이력" 한 줄기 시연 성공
- ☐ 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정100) 테스트 검증
- ☐ `docker compose up` 한 번으로 전체 기동 + 부록 B 시드 자동 적재

## 부록 A. 프론트엔드 화면 명세 (v1.0)

에이전트는 아래 라우트·요소를 그대로 구현한다. 디자인은 단순·표준(사이드바+콘텐츠)으로 하되 구성 요소는 임의로 빼지 않는다.

| 라우트 | 화면 | 필수 요소 | 접근 |
|--------|------|-----------|------|
| `/login` | 로그인 | loginId/password 폼 · 실패 메시지 · JWT 저장 | 전체 |
| `/projects` | 프로젝트 목록 | 상태·제품군 필터 · 이름 검색 · 페이지네이션 · (관리자) 등록 버튼 | 가시성 범위 |
| `/projects/new` | 프로젝트 등록 | 입력 항목 폼 + 참여자별 role(관리자/담당자) 선택 · 422/409 오류 표시 | 관리자 |
| `/projects/:id` | 프로젝트 상세 | 기본정보 · 상태 뱃지 · 진행률(권한 시 수정) · 배정 목록 · lastEditedBy/At · (완료 시) 이관 버튼 | 가시성 범위 |
| 〃 배정 패널 | 인력 배정 | 배정 추가(사람 검색→월별 M/M) · 종료 처리 · 409 표시 | 관리자 |
| `/utilization` | 가동률 대시보드 | 월 선택 · 팀 필터 · 기본/보정 표 · 과부하(보정>100%) 강조 · 과부하만 보기 | 가시성 범위 |
| `/maintenance/:id` | 유지보수 상세 | 계약 정보(원프로젝트 링크) · 이력 목록(type 필터) · (관리자) 이력 추가 | 가시성 범위 |
| 공통 헤더 | 알림 뱃지 | 미읽음 수(30초 폴링) · 클릭 시 목록·읽음 처리 | 로그인 사용자 |

**공통 UI 규칙**: 모든 목록은 로딩/빈/에러 3상태 · `409 STALE_VERSION` 수신 시 "OO님이 먼저 수정했습니다. 최신 내용을 불러올까요?" → 확인 시 재조회(reload-and-retry) · 권한 없는 버튼은 렌더링하지 않되 서버 403 처리는 항상 존재.

## 부록 B. 시드(데모) 데이터 (v1.0)

`docker compose up` 후 자동 적재(또는 seed 프로파일). DoD의 "한 줄기 시연"과 가동률 예시 검증이 이 데이터로 가능해야 한다.

- 조직: 솔루션사업본부(inScope=true) 1 · 팀 2(개발1팀·개발2팀) · 직급: 수석(1.2) / 선임(1.0) / 사원(0.8)
- 인원 10명: 본부장 1 · 팀장 2 · 팀원 7 · 전원 capacity=1.0
- 계정: `admin / admin123!` (관리자·본부장) · `member / member123!` (담당자·팀원) — 데모용, 해시 저장
- 고객사 3곳 · 프로젝트 5건: 계약대기 1 · 수주확정 1 · 진행중 2 · 완료 1(이관 시연용, 미이관 상태 유지)
- 가동률 예시(필수): 김개발(수석 1.2) = A 0.5 + B 0.7 당월 배정 → 기본 120% / 보정 100% (AC-C1-2 동일 수치)
- 오버부킹 케이스: 이과부하(선임 1.0) 당월 합계 1.3 → 보정 130% 과부하 강조 확인

## 12. 미해결 / 결정 필요

**에이전트는 임의 구현하지 말고 질문할 것**
- 가동률 캐시 테이블 도입 여부(성능 필요 시) vs 매 조회 계산.
- 마감 임박 알림 D-N 값(예: D-7).
- JWT 만료 시간·refresh 정책.
- (2차) MCP 챗봇 PAT 검증 지점 — 별도 API 명세 시안 참조.

---

본 PRD는 「PMS 기획서 v2」·「PMS 아키텍처 설계서 v2」에서 파생. 충돌 시 이 PRD의 AC를 우선한다. · v0.1 · 2026-06-21
