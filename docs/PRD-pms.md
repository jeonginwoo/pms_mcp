# PMS — 구현용 PRD (PRD-pms)

| 항목 | 내용 |
|------|------|
| 문서 | PMS 본체 구현명세 (코딩 에이전트용) · **소유: pms 트랙** |
| 버전 / 상태 | v2.1 · **초안** (게이트 P에서 사람 승인 시 확정) |
| 작성일 | 2026-08-02 — 구 "PMS — AI 구현용 PRD" v1.0(2026-06-21, 전사본 `reference/PMS_구현용_PRD_v1.0.md`) 현행화 이관 |
| 범위 | 프로텐 전사 1차 |
| 규모 | 약 40명(시드 기준 44명) · 2인 개발(MCP 담당 + PMS 담당) |
| 스택 | Java 25 · Spring Boot 4.1 · Spring Modulith 2.1 · React · PostgreSQL |
| 인증 | 자체 로그인 · JWT (MCP 경유는 토큰 패스스루 — 구조 원칙 4) |
| 상위 문서 | **`docs/PRD.md`** — 공유 정의(용어·권한 모델·가동률 산식·대상 사용자·시드 기준)의 유일 원본. 본 문서는 참조만 하고 재정의하지 않는다 |
| 관련 문서 | `docs/PRD-host.md`(AI 어시스턴트 — 도구 카탈로그↔서비스 대응) · `docs/ROADMAP.md` · `reference/seed/` |

## v1.0 → v2.0 변경 요약 (충돌 7건 수정 — PROGRESS 결정 기록 2026-08-02)

| # | 변경 | 반영 위치 |
|---|------|-----------|
| ① | 스택 Boot 4.0·Modulith 2.0 → **4.1·2.1** | §0 |
| ② | 전제 "솔루션사업부 40명·1인" → **프로텐 전사 44명·2인**. Out of Scope의 "MS본부(2차)" 삭제, `Division.inScope` 플래그 제거 | §1 · §4 |
| ③ | 부록 B 가상 시드(10명·5건) → **`reference/seed/` 44명·382프로젝트** 기준 재작성 + 시드 공백(계정·M/M·Capacity·유지보수)의 적재 정책 명시 | 부록 B |
| ④ | MCP "2차" 취급 → **v3 M0에 `/mcp` 임베디드 어댑터** (구조 원칙 2). 6모듈 고정 해제 — 모듈 목록은 PMS-M0 스캐폴드에서 확정 | §3 · §10 · §12 |
| ⑤ | 마일스톤 라벨 M0~M6 → **PMS-M0~M6** (루트 ROADMAP M-1~M3와 충돌 방지) | §10 |
| ⑥ | 알림 30초 폴링 → **SSE 즉시 푸시** (구 2026-07-13 결정, frontend 프로토타입에 구독 로직 기존재) | §3 · §9 · 부록 A |
| ⑦ | 조직 가시성에 **ADMIN(대표)=전사** 추가, 명칭을 시드 orgRole·부문 체계로 정합 | §2 |
| + | 가동률 예시 직급을 시드 정합으로 보정(coeff 1.2 = 책임) · 단건 응답 version 포함 명시(PRD-host FR-AI-10 대응) | §6 C1-2 · §7 |

### v2.0 보완 (2026-08-02 — `frontend/` 프로토타입 대조 검토, PROGRESS 결정 기록)

프로토타입(구 백엔드 실연동본)의 검증된 동작을 명세로 승격: 웹 진행률 2단계(US-A2) · 상태 전이 서버 강제(US-A5) · 사용자 CRUD(US-E2) · 마감 임박 알림 AC(US-F2) · 감사 조회(G1-3) · 내 계정(EPIC H) · chat BFF·SSE 쿼리 토큰(§7) · email 로그인·화면 3종(부록 A) · 계정 적재 규칙 확정(부록 B). **배정은 기간 모델 유지** — 프로토타입의 월별 upsert API는 재연동 시 본 계약으로 조정. **권한 모델은 2026-08-03 확정**(합집합 판정 + 프로젝트 역할 PM/PL/참여자 3단 — 상위 `PRD.md` §4). HQ 가동률 집계 제외만 잔여(12장).

### v2.1 정합성 리뷰 반영 (2026-08-03 — 권한 모델 재작성 직후 잔여 검토)

- ADMIN 태그 표기 통일 — §6 도입부 공통 규칙 1문장, 개별 태그에서 `· ADMIN` 제거(상위 PRD §4-1 "표에 ADMIN 열을 두지 않는다" 정합)
- US-A5에 [PM·PL] 태그 + 정보 수정 권한 AC(A5-3) 신설 — 문서 전체에서 빠져 있던 `PUT /projects/{id}` 권한 검증
- US-A6 구멍 보강: A6-3 해제 의미 정의 · A6-7 신설(`/roles`의 PM 지정 차단·2번째 PL 차단) · 자동 생성 배정 기본값(기간·monthlyMM=0)
- 배정 수정 AC(B1-4) + `AssignmentUpdated` 이벤트 신설 — §7의 `PUT /assignments/{id}` 대응
- AuditLog action 정리 — STATE_CHANGE는 §5 상태 전이 전용, 역할 변경·팀 이동은 UPDATE(A6-1·E1-1)
- 에러 표 422 행에 PM_REQUIRED·MULTIPLE_PM·MULTIPLE_PL·INVALID_ROLE 추가
- EPIC H 보강: `GET /api/me/account` AC 대응(H1-1) · email 중복 `409 DUPLICATE_EMAIL`(H1-2)

---

## 0. AI 에이전트에게 주는 지시

**고정값 — 임의 변경 금지.** 너는 이 PRD를 구현하는 시니어 풀스택 엔지니어다. 아래 스택·규칙을 고정값으로 받아들이고, 명세되지 않은 선택은 "가장 단순·표준적인 방법"을 택하되 결정을 주석으로 남긴다. 각 기능은 대응하는 수용기준(AC)을 테스트로 구현하며, AC 없는 코드는 작성하지 않는다. 각 마일스톤 끝에서 `bash scripts/verify.sh pms`(내부: gradle test + Modulith 경계 검증)가 통과해야 다음으로 넘어가고, 이전 마일스톤 테스트가 깨지면 새 기능 진행을 멈추고 회귀부터 고친다. 명세 밖 세부 결정은 단순·표준을 택해 `// ASSUMPTION:` 주석(한국어)으로 남기되, 12장 항목은 임의 구현하지 말고 질문한다.

**고정 기술 스택**
- Backend: Java 25 · Spring Boot 4.1 · Spring Modulith 2.1 · JPA
- DB: PostgreSQL(운영) · H2/Testcontainers(테스트)
- Frontend: React + TypeScript + Vite (CSR SPA) — 프로토타입(`frontend/`, JSX)은 M1 재연동, 신규 파일부터 TS (`docs/conventions/react-ts.md`)
- Build: Gradle · Test: JUnit 5 · AssertJ · Modulith Test
- 배포: Nginx → Spring Boot → PostgreSQL (Docker Compose)

**아키텍처 규칙 (위반=실패)**
- 모듈러 모놀리식 · Modulith 경계 강제 (모듈 목록은 §3 — PMS-M0 스캐폴드에서 확정)
- `api→application→domain←infra`, domain은 Spring/JPA import 0
- 모듈 간 객체참조 금지, ID로만 연결(질의는 포트)
- 단일 DB · 모듈별 스키마 · 모듈 간 물리 FK 금지
- 이벤트는 사후 fan-out만, 즉시·원자적은 동기 호출
- 권한은 서버 최종 판정, 프론트는 UI 노출 제어만
- `/mcp` 어댑터는 애플리케이션 서비스만 호출 — 리포지토리 직접 접근 금지 (구조 원칙 3)

## 1. 제품 개요

**한 줄 정의** — 여러 고객사 프로젝트를 인력(M/M)·가동률·유지보수 이력 관점에서 관리하는 사내 도구. 스프레드시트를 대체한다.

**목표 (1차)**: 한 프로젝트가 수주확정 → 진행(배정·가동률) → 완료·검수 → 유지보수 이관 → 이력관리로 끊김 없이 흐르는 한 줄기를 완성. 배정 변경 시 가동률 2초 내 갱신, 오버부킹 자동 감지.

**성공 지표**: 프로텐 전 직원(시드 44명)이 시트 대신 사용 · "한 줄기" 데모 성공 · 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정100) 검증.

**In Scope**: 프로젝트 CRUD · 인력 배정 · 월별합산/직급보정 가동률 · 오버부킹 감지 · 유지보수 이관·이력 · 두 축 권한 · 감사로그 · 인앱 알림 · `/mcp` 어댑터 접점(어댑터 자체는 MCP 담당 소유).

**Out of Scope (구현 금지)**: 태스크/칸반 · sales 모듈 · 파일 업로드 · 메일/Slack 알림 · SSO · 소속 시점이력 · orgRole 커스텀 추가/편집 · MSA. (구 "프로젝트별 권한예외 · 4역할 세분화" 금지는 2026-08-03 권한 모델 확정으로 해제 — 프로젝트 역할 PM/PL/참여자는 In Scope) (v1.0의 "MS본부(2차)"는 삭제 — 전사 범위 전환으로 시드에 MS사업부 포함)

## 2. 사용자 · 권한 모델

권한 모델(두 축·조직 가시성·기능별 권한 표·404 은닉 의미론)은 **상위 `docs/PRD.md` §4가 유일 원본**이다. 구현 관점 보충만 남긴다:

- 소유 모듈: 조직 가시성 = identity, 프로젝트 역할 = project
- 판정식은 상위 `PRD.md` §4-1이 원본(`canDo = orgPerm OR projectPerm`, 가시성은 §4-4). 서버가 최종 판정(§0 아키텍처 규칙), 프론트는 UI 노출 제어만
- orgRole 값(ADMIN/DIVISION_HEAD/TEAM_LEAD/MEMBER)은 시드 `people.json`과 정합 유지
- **확정(2026-08-03)**: 판정은 **합집합** — `canDo = orgPerm(orgRole) OR projectPerm(프로젝트 역할)`. orgRole을 선행 게이트로 쓰지 않는다. 프로젝트 역할은 **PM / PL / 참여자** 3단이며 프로젝트마다 개별 판정한다(구 "관리자/담당자" 대체). orgRole은 가시성 + 프로젝트 밖 행위(생성·조직 관리)만 담당. 프로토타입 기능 플래그 5종 미채택, 부문장 `editProgress:false` 폐기. 상세 표는 상위 `PRD.md` §4가 유일 원본 — 본 문서는 참조만 한다 (PROGRESS 결정 기록 2026-08-03)
- 가동률 집계의 대표 직속(HQ_TEAM '프로텐') 제외 규칙은 권한이 아닌 집계 규칙으로 분리 — 미결(PROGRESS 미해결 이슈)

## 3. 시스템 구성 (요약)

```
[사용자] --HTTPS--> [Nginx] --> [React SPA] --REST/JWT·SSE--> [PMS Boot 앱 (Modulith 모듈 + /mcp 어댑터)] --JPA--> [PostgreSQL]
                                          [AI 호스트 Boot 앱] --MCP(Streamable HTTP)--> 위 /mcp
```

- Frontend=화면·검증·표시(권한은 UI노출만) / Backend=단일앱 모듈러 모놀리식 / DB=단일PG·모듈별스키마 / 인증=자체 로그인+JWT(stateless) / 알림=SSE 즉시 푸시(⑥) / 스케줄러=일1회(마감알림)만 / 파일저장소 없음.
- **모듈 목록(PMS-M0 스캐폴드에서 확정)**: 바운디드 컨텍스트 6(identity·project·resource·maintenance·notification·common) + 지원 모듈 후보(chat BFF·mcpconfig — 구 설계 승계 표기) + `/mcp` 어댑터(MCP 담당 소유). v1.0의 "6모듈 고정"은 해제.
- `/mcp` 어댑터가 호출하는 애플리케이션 서비스는 EPIC A(조회·진척률)·C(가동률)·D(이력)·H(`/api/me` = `whoami`)와 동일 — 도구 카탈로그 대응은 PRD-host §4-2. 서비스 API 변경은 공용 결정 기록 경유(2인 협업 경계).

## 4. 도메인 모델

모든 수정 가능 엔티티는 `version:long`(낙관적 락). 모듈 간 참조는 `*Id`.

- **identity**: `Division`(name) · `Team`(divisionId) · `Grade`(name, coeff — 시드 값은 부록 B) · `Person`(teamId, gradeId, orgRole{ADMIN,DIVISION_HEAD,TEAM_LEAD,MEMBER}, capacity) · `User`(personId, email(로그인 ID), passwordHash, phone, notifPrefs — 내 계정 EPIC H 대응)
  - v1.0의 `Division.inScope`는 제거 — 전사 범위 전환으로 불필요(②)
- **project**: `Project`(client·name·solution(제품군)·engagement{REMOTE,PARTIAL_ONSITE,OFFSITE,ONSITE}·**managerId(PM)**·contractMM·기간·status·progress·deleted·version) · `ProjectAssignment`(personId·**role{PM,PL,PARTICIPANT}**·기간·monthlyMM·status)
  - 필드명·값은 시드 `projects.json` 정합(client·solution·engagement 4종·managerId — v1.0의 account·productType 대체. AC A1-3의 pmId = managerId)
  - **`ProjectAssignment.role`이 프로젝트 역할의 정본**(2026-08-03). `Project.managerId`는 대표 PM 파생 읽기 필드로 유지 — 시드 정합·조회 편의. 불변식: 프로젝트당 `role=PM` 정확히 1행, `managerId`와 일치. 값은 `PARTICIPANT`를 쓴다 — orgRole의 `MEMBER`와 이름이 겹치면 안 된다. `role=PL`은 복수 행 물리적으로 허용하되 API에서 당분간 1명으로 제약(제약 해제 시 스키마·접점 변경 없음)
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
- 시드 status 분포는 4단계(완료 319·진행중 34·수주확정 19·계약대기 10) — "유지보수중"은 운영 중 이관으로만 생성.

## 6. 기능 요구사항 — 유저스토리 + 수용기준(AC)

각 Given·When·Then = 테스트 1개. AC 없는 코드 금지. **역할 태그 규칙**: `[PM·PL·참여자]`는 프로젝트 역할(상위 PRD §4-2), `[orgRole: …]`은 조직 권한(§4-3). **ADMIN은 §4-1 치환(모든 프로젝트에서 PM 간주)으로 모든 PM 태그에 자동 포함되므로 태그에 별도 표기하지 않는다.**

### EPIC A · 프로젝트

**US-A1 프로젝트 생성 권한자로서 새 프로젝트를 등록한다** [orgRole: TEAM_LEAD·DIVISION_HEAD·ADMIN]
- A1-1 Given 유효 입력 When `POST /api/projects` Then `201`, 상태=계약대기, AuditLog CREATE 1건
- A1-2 Given 같은 고객사·같은 이름(정규화 후, soft-deleted 제외) When 생성 Then `409 DUPLICATE_NAME` — 정규화 = trim·연속 공백 1개로 축약·영문 대소문자 무시
- A1-3 Given 없는 clientId/pmId When 생성 Then `422 REF_NOT_FOUND`
- A1-4 Given 참여자 목록 When 생성 Then 각자 지정 role(PM/PL/PARTICIPANT)로 배정. **PM 1명 지정 필수** — 생성자 본인이 아니어도 된다. 미지정 시 `422 PM_REQUIRED`
- A1-5 Given orgRole=MEMBER 토큰 When 생성 Then `403` — 생성은 orgRole이 판정한다(TEAM_LEAD·DIVISION_HEAD·ADMIN만, 상위 PRD §4-3). 프로젝트 역할은 판정 축이 아니다
- A1-6 Given `role=PM`이 2행 이상인 입력 When 생성 Then `422 MULTIPLE_PM` — 프로젝트당 `role=PM` 정확히 1행 불변식
- A1-7 Given `role=PL`이 2행 이상인 입력 When 생성 Then `422 MULTIPLE_PL` — 스키마는 복수 PL을 수용하나 API가 당분간 1명으로 제약(12장 · 제약 해제 시 이 AC만 삭제)

**US-A2 참여자로서 본인 배정 프로젝트 진행률을 갱신한다** [PM·PL·참여자] — 웹·MCP(`update_progress`) **동일 서비스·동일 2단계 프로토콜** (2026-08-02 프로토타입 동작 승격)
- A2-1 Given 본인 배정(role 무관 — PM·PL·PARTICIPANT 동일) When `PUT /progress {progress:90, version, confirmed:false}` Then `200` + 변경 요약 반환, DB 미변경. `Project.progress`는 단일 값이므로 부분("본인 몫") 수정 개념은 없다
- A2-2 Given 요약 확인 후 When `confirmed:true` 재호출 Then `200` 커밋 + AuditLog UPDATE
- A2-3 Given progress=100·confirmed=true When 저장 Then 완료 자동전이 + `ProjectCompleted` + STATE_CHANGE
- A2-4 Given 본인 미배정 When 수정 Then 가시성 밖이면 `404`(은닉), 가시성 안이면 `403`
- A2-7 Given orgRole=ADMIN·미배정 When 수정 Then `200` — ADMIN은 모든 프로젝트에서 PM으로 간주(상위 PRD §4-1)
- A2-5 Given progress<0 or >100 When 저장 Then `400`
- A2-6 Given version 불일치 When `confirmed:true` Then `409 STALE_VERSION` + 최신 progress·version 반환

**US-A3 가시성 범위 내에서 프로젝트를 조회한다**
- A3-1 Given 팀장 When `GET /projects` Then **자기 팀 범위 ∪ 본인이 배정된 프로젝트**(타 팀 포함) page 봉투 — 가시성은 프로젝트 역할이 확장한다(상위 PRD §4-4). 시드 기준 참여자가 2개 팀 이상인 프로젝트가 46건이라 상시 발생하는 경로다
- A3-2 Given 범위 밖 id When 상세조회 Then `404` (은닉)
- A3-3 Given 타 팀 프로젝트에 배정된 사용자 When 그 프로젝트 상세조회 Then `200` + 해당 프로젝트의 배정 레코드(타 팀 인원 포함) 노출. 단 그 인원의 **다른 프로젝트·개인 전체 가동률은 조직 가시성 규칙을 그대로 따른다**(프로젝트 컨텍스트 한정)

**US-A4 PM으로서 프로젝트를 소프트 삭제한다** [PM]
- A4-1 When `DELETE /projects/{id}` Then `204`, deleted=true, 목록·중복검사 제외, AuditLog DELETE
- A4-2 Given PL 또는 참여자 토큰 When 삭제 Then `403`

**US-A5 프로젝트 정보 수정은 PM·PL만, 상태는 정의된 전이만 허용된다** [PM·PL] — 프로토타입 수정 폼의 status 자유 편집 대비 서버 강제 (2026-08-02)
- A5-1 Given 순방향 전이(§5) When `PUT /projects/{id}` (status 포함) Then `200` + AuditLog STATE_CHANGE
- A5-2 Given 역방향·건너뛰기 전이 When 저장 Then `409 INVALID_TRANSITION`, 아무것도 안 바뀜
- A5-3 Given 참여자 토큰 When `PUT /projects/{id}` (정보 수정) Then `403` — 정보 수정 권한(상위 PRD §4-2 PM·PL)은 이 AC가 검증한다. 진행률은 별도 경로(US-A2)라 참여자도 가능

**US-A6 프로젝트 역할을 지정·교체한다** [PM] (2026-08-03 권한 모델 확정에 따른 신설)
- A6-1 Given 현 PM When `PUT /projects/{id}/pm {personId, version}` Then `200`, `ProjectAssignment.role` PM 이동 + `Project.managerId` 동기화, AuditLog UPDATE — STATE_CHANGE는 §5 상태 전이 전용
- A6-2 Given PL·참여자 토큰 When PM 교체 Then `403`
- A6-3 Given PM When `PUT /projects/{id}/roles {personId, role}` (role ∈ {PL, PARTICIPANT}) Then `200`, AuditLog UPDATE. PL·참여자 토큰은 `403`. **해제 = `role=PARTICIPANT`로 변경**(배정은 유지 — 배정 자체의 종료는 US-B2)
- A6-6 Given 대상이 해당 프로젝트에 미배정 When `role=PL` 지정 Then 배정을 함께 생성 — PM·PL은 항상 배정 인원(상위 PRD §4-2). **자동 생성 배정의 기본값: 기간 = 지정일~프로젝트 종료일 · monthlyMM = 0** (M/M 입력은 PM이 배정 패널에서 별도 수행)
- A6-4 Given 교체 대상이 해당 프로젝트에 미배정 When PM 교체 Then 배정을 함께 생성(기본값은 A6-6과 동일). 직전 PM은 `role=PARTICIPANT`로 강등(배정은 유지)
- A6-5 Given 임의 시점 When 조회 Then 프로젝트당 `role=PM` 정확히 1행 · `Project.managerId`와 일치 (불변식 — 경계 테스트 대상)
- A6-7 Given `role=PM` When `/roles` 경유 지정 Then `422 INVALID_ROLE` — PM 변경은 `/pm` 전용(A6-5 불변식 우회 차단). Given 이미 PL 존재 When 두 번째 PL 지정 Then `422 MULTIPLE_PL` — A1-7과 동일 제약, 해제 시 함께 삭제

### EPIC B · 인력 배정

**US-B1 PM으로서 인력을 배정한다** [PM]
- B1-1 When `POST /projects/{id}/assignments` Then `201` + `MemberAssignedToProject`
- B1-2 Given 종료 안된 동일 personId 존재 When 재배정 Then `409 DUPLICATE_ASSIGNMENT` (키=projectId+personId+status≠종료)
- B1-3 When 커밋 후 Then resource가 해당 월 가동률 재계산
- B1-4 When `PUT /assignments/{id}` (기간·monthlyMM 수정, `version` 포함) Then `200` + `AssignmentUpdated` → 영향 월 가동률 재계산. PL·참여자 토큰은 `403`(M/M 입력은 PM — 상위 PRD §4-2) — §7의 `PUT /assignments/{id}` 대응 AC

**US-B2 배정 종료 시 이후 월 가동률에서 빠진다** [PM]
- B2-1 When `DELETE /assignments/{id}` Then status=종료, `AssignmentClosed`, 종료월 이후 제외

### EPIC C · 가동률

**US-C1 특정 월의 가동률을 조회한다**
- C1-1 When `GET /utilization?month=&personId=` Then 기본=Σ배정MM÷가용×100, 보정=÷(가용×coeff)
- C1-2 Given A0.5+B0.7, 가용1.0, coeff1.2(책임 — 시드 직급계수 기준) Then 기본=120, 보정=100
- C1-3 Given `overbooked=true` Then 보정>100인 사람만
- C1-4 Given 배정 변경 Then 커밋 후 2초 내 가동률 조회 API에 반영 — 이벤트 재계산 완료 기준, 통합 테스트로 검증

### EPIC D · 유지보수

**US-D1 완료 프로젝트를 유지보수로 이관한다** [PM]
- D1-1 Given status=완료 When `POST /handover` Then Maintenance+초기Log+상태전이 **한 트랜잭션**, 커밋 후 `MaintenanceHandedOver`
- D1-2 Given status≠완료 When 이관 Then `409 NOT_COMPLETED`, 아무것도 안 바뀜(원자성)

**US-D2 유지보수 이력을 등록/조회한다** [등록: PM / 조회: 가시성 범위]
- D2-1 When `POST /maintenances/{id}/logs` Then `201`, append-only
- D2-2 Given 기존 로그 When 수정/삭제 Then 불가(API 없음), 보정은 새 로그로만

### EPIC E · 조직 · 사용자 관리

**US-E1 사람의 소속 팀을 이동한다** [orgRole: ADMIN]
- E1-1 When `PUT /people/{id}/team` Then teamId 변경, 가시성 즉시 반영, AuditLog UPDATE — STATE_CHANGE는 §5 상태 전이 전용(v2.1 정리)
- E1-2 Given 진행 중 배정 보유 When 이동 Then 허용+경고. 과거 집계는 현재 소속 기준(시점 미보존)

**US-E2 ADMIN으로서 사용자를 등록·수정·삭제한다** [ADMIN] (2026-08-02 채택 — 프로토타입 기구현)
- E2-1 When `POST /api/people {name, team, grade, orgRole}` Then `201` + AuditLog CREATE — User 계정은 부록 B 규칙(email·초기 비밀번호)으로 생성
- E2-2 When `PUT /api/people/{id}` Then `200` — 이름·팀·직급(coeff)·orgRole 변경, AuditLog UPDATE
- E2-3 When `DELETE /api/people/{id}` Then `204` — soft 비활성(로그인 차단·목록 제외), 과거 배정·감사·집계는 보존
- E2-4 Given 비ADMIN 토큰 When 위 요청 Then `403`

### EPIC F · 알림

**US-F1 이벤트를 구독해 인앱 알림을 적재하고 SSE로 푸시한다**
- F1-1 Given `OverbookingDetected` Then 해당 인원의 팀장(orgRole=TEAM_LEAD)에게 Notification 생성
- F1-2 Given 동일 이벤트 중복 Then 알림 1건만(멱등)
- F1-3 When `GET /notifications?read=false` Then 본인 미읽음만, `PATCH /{id}/read` → `204`
- F1-4 Given 알림 생성 When 수신자가 SSE 연결 중 Then 즉시 푸시. 미연결이면 재연결·재조회 시 반영 (⑥ — 구 2026-07-13 SSE 채택 결정)
- F1-5 Given 수신자의 알림 설정(notifPrefs — H1-4)이 해당 유형 꺼짐 Then 적재·푸시하지 않음

**US-F2 마감 임박 프로젝트를 매일 점검해 알린다** (2026-08-02 — PMS-M5 @Scheduled의 AC 보강)
- F2-1 Given 종료일이 D-N 이내인 진행중 프로젝트 When 일일 스케줄러 실행 Then PM(managerId)에게 Notification 1건 (D-N 값은 12장 미해결)
- F2-2 Given 같은 프로젝트·같은 날 재실행 Then 중복 알림 없음(멱등)

### EPIC G · 감사 (횡단)

**US-G1 모든 변경이 자동 기록된다**
- G1-1 Given 임의 변경 Then AuditLog 1건 자동 생성(before/after)
- G1-2 Then AuditLog 수정·삭제 API 부재(append-only)
- G1-3 When `GET /api/audit` (ADMIN) Then page 봉투 목록 · 비ADMIN `403` (2026-08-02 채택 — 프로토타입 설정 화면의 감사 탭 대응)

### EPIC H · 내 계정 (2026-08-02 채택 — 프로토타입 기구현)

**US-H1 로그인 사용자로서 내 계정을 관리한다**
- H1-1 When `GET /api/me` Then 본인 personId·이름·팀·부문·orgRole — MCP `whoami` 도구(PRD-host FR-AI-16)와 동일 서비스. `GET /api/me/account`는 계정 상세(email·phone·notifPrefs — 프로토타입 내 계정 모달 대응)
- H1-2 When `PUT /api/me/profile {name, email, phone}` Then `200` + AuditLog UPDATE. email은 로그인 ID — 타 사용자와 중복 시 `409 DUPLICATE_EMAIL`
- H1-3 Given 현재 비밀번호 일치·새 비밀번호 8자 이상 When `PUT /api/me/password {current, newPassword}` Then `200`(해시 저장) / 불일치·형식 오류 Then `400`
- H1-4 When `PUT /api/me/notif-prefs {progress, project, org, weekly}` Then `200` — 알림 적재·푸시 시 수신자 설정 필터로 적용(F1-5)

## 7. API 계약 (공통 규약)

- **인증**: `Authorization: Bearer <JWT>` 필수, 없으면 `401`. (`/mcp` 경유 호출도 사용자 토큰 패스스루 — 별도 서비스 계정 없음, 구조 원칙 4)
- **응답(비대칭)**: 단건=원본 / 목록=page 봉투(content,page,size,totalElements,totalPages) / 에러=`{error:{code,message,field,traceId}}`.
- **단건 응답은 `version`을 포함한다** — 동시성 제어 및 MCP 도구 계약(PRD-host FR-AI-10: 프로젝트 상세의 version 반환)의 전제.
- **상태코드**: 200 조회·수정 / 201 생성 / 204 삭제·읽음 / 4xx 에러.
- **동시성**: 본문에 `version`, 불일치 시 `409 STALE_VERSION` → reload-and-retry.
- **페이징**: `?page=0&size=20&sort=field,desc` · **가시성**: 조회 선필터, 범위 밖 `404`(은닉 — 권한/부재 비구분).

| code | HTTP | 의미 |
|------|------|------|
| VALIDATION_ERROR | 400 | 입력 형식 오류 |
| UNAUTHENTICATED | 401 | 토큰 없음/만료 |
| FORBIDDEN_FIELD | 403 | 권한 없는 행위 ※명칭 재검토 후보 — 프로토타입 호환 확인 후 결정(12장) |
| NOT_FOUND | 404 | 없음/가시성 밖 |
| DUPLICATE_* / NOT_COMPLETED / INVALID_TRANSITION | 409 | 중복·상태 위반·전이 위반 |
| STALE_VERSION | 409 | 동시 수정 충돌 |
| REF_NOT_FOUND / PM_REQUIRED / MULTIPLE_PM / MULTIPLE_PL / INVALID_ROLE | 422 | 참조 대상 없음 · 역할 구성 위반 (A1-4·A1-6·A1-7·A6-7) |

```
GET/POST    /api/projects              GET/PUT/DELETE /api/projects/{id}
PUT         /api/projects/{id}/progress        # 2단계: confirmed=false 요약 → true 커밋 (US-A2)
PUT         /api/projects/{id}/pm                  # PM 교체 (US-A6 A6-1)
PUT         /api/projects/{id}/roles               # 프로젝트 역할 지정·해제 {personId, role} (US-A6 A6-3)
GET/POST    /api/projects/{id}/assignments     PUT/DELETE /api/assignments/{id}
GET         /api/utilization?month=&personId=&teamId=&overbooked=
POST        /api/projects/{id}/handover
GET/POST    /api/maintenances/{id}/logs        # 프로토타입의 /api/maintenance/{id}는 재연동 시 이 경로로 통일 (2026-08-02)
GET/POST    /api/people    PUT/DELETE /api/people/{id}    PUT /api/people/{id}/team
GET /api/teams    GET /api/grades    GET /api/audit (ADMIN)
GET /api/me    GET /api/me/account    PUT /api/me/profile    PUT /api/me/password    PUT /api/me/notif-prefs
POST /api/auth/login (email + password)
GET /api/notifications    PATCH /api/notifications/{id}/read
GET /api/notifications/stream (SSE)
POST /api/chat    POST /api/chat/feedback        # chat BFF — AI 호스트 프록시·피드백 저장(PRD-host FR-AI-05), 상세 계약은 M1 확정
```

- **SSE 인증**: EventSource는 헤더를 싣지 못하므로 `?access_token=` 쿼리 파라미터로 인증 — 액세스 로그에 토큰이 남지 않도록 마스킹 필수.

## 8. 이벤트 명세

| 이벤트 | 발행자 | 구독자 | 효과 |
|--------|--------|--------|------|
| `MemberAssignedToProject` | project | resource, notification | 가동률 재계산, 알림 |
| `AssignmentUpdated` | project | resource | 기간·M/M 변경 영향 월 재계산 (B1-4) |
| `AssignmentClosed` | project | resource | 종료월 이후 제외 |
| `OverbookingDetected` | resource | notification | 팀장 알림 |
| `ProjectCompleted` | project | notification | 완료/이관 안내 |
| `MaintenanceHandedOver` | maintenance | notification | 초기 이력·알림 |

발행=트랜잭션 커밋 후(`AFTER_COMMIT`) · 신뢰성=Modulith Event Publication Registry 재시도 · 재계산 멱등.

## 9. 비기능 요구사항

성능: 조회 P95 < 1초 · 가동률 반영 < 2초 · 알림 SSE 즉시 푸시(재연결 복구 포함) · 권한 서버 최종판정 · 비밀번호 해시 · 낙관적 락 · 감사 append-only·source 구분 · 도메인 순수 단위테스트 · Modulith 경계 검증 · compose 로컬 기동 · 1요청=1트랜잭션 · 앱 무상태 재기동 복구

## 10. 구현 순서 (Milestones — PMS-M 라벨)

순서대로 진행. 각 단계 끝에서 해당 AC 테스트가 초록이어야 다음으로. 라벨은 루트 `docs/ROADMAP.md`의 M-1~M3(제품 단계)와 구분되는 **pms 트랙 내부 순서**다.

- **PMS-M0 스캐폴딩**: Gradle 모듈(§3 목록 확정) + Modulith + PG/H2 + Docker Compose + 레이어 골격. 경계 테스트 통과. — 루트 ROADMAP M0의 "PMS 백엔드 스캐폴드" 항목에 대응
- **PMS-M1 identity + 인증**: 조직/사람/직급/User, 자체 로그인(email)→JWT, 가시성 필터, 내 계정·사용자 관리. (로그인·401·403, EPIC H·US-E2) — 루트 M0의 `/mcp` 인증 체인(MCP 담당)이 이 위에 얹힌다
- **PMS-M2 project (핵심)**: Project/Assignment CRUD, 상태전이, 2종 권한, 낙관적 락, AuditLog. (EPIC A·B·G)
- **PMS-M3 resource (가동률)**: Capacity, 합산·보정 가동률, 오버부킹, 이벤트 재계산. (EPIC C)
- **PMS-M4 maintenance**: 완료→이관(동기·원자적), Maintenance/Log(append-only). (EPIC D)
- **PMS-M5 notification + 조직이동 + 스케줄러**: 이벤트→알림 적재·SSE 푸시, 마감임박 @Scheduled, 팀 이동. (EPIC E·F)
- **PMS-M6 Frontend + 통합**: `frontend/` 프로토타입 재연동(라우트·요소는 부록 A), Nginx 프록시, 부록 B 시드로 E2E 스모크(한 줄기 시연). — 루트 ROADMAP M1(조회 도구 공개)과 정렬

## 11. 완료 정의 (Definition of Done)

- ☐ 모든 EPIC의 AC가 테스트로 구현되고 초록 (`bash scripts/verify.sh pms` 통과)
- ☐ Spring Modulith 경계 위반 테스트 0건
- ☐ domain 레이어에 Spring/JPA import 0
- ☐ 시드 데이터(44명·382프로젝트)로 "수주→배정→가동률→완료→이관→이력" 한 줄기 시연 성공
- ☐ 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정100) 테스트 검증
- ☐ `docker compose up` 한 번으로 전체 기동 + 부록 B 시드 자동 적재

## 부록 A. 프론트엔드 화면 명세

에이전트는 아래 라우트·요소를 그대로 구현한다(`frontend/` 프로토타입 재연동 기반). 디자인은 단순·표준(사이드바+콘텐츠)으로 하되 구성 요소는 임의로 빼지 않는다.

| 라우트 | 화면 | 필수 요소 | 접근 |
|--------|------|-----------|------|
| `/login` | 로그인 | **email**/password 폼(계정 ID = 이메일 — 2026-08-02 확정) · 실패 메시지 · JWT 저장 | 전체 |
| `/` | 홈 대시보드 | 내 프로젝트·가동률 요약·최근 알림 (프로토타입 구성 승격) | 로그인 사용자 |
| `/people` | 인력 | 인원 목록(팀 필터·검색) · 상세(참여 프로젝트·가동률) | 가시성 범위 |
| `/settings` | 설정 | (ADMIN) 감사로그 조회(G1-3)·사용자 관리(US-E2). 조직 트리·직급·권한 편집 탭은 백엔드 엔티티 없음 — 프로토타입처럼 로컬 데모 표기 유지(채택 여부 2차 검토) | ADMIN |
| `/projects` | 프로젝트 목록 | 상태·제품군(solution) 필터 · 이름 검색 · 페이지네이션 · (생성 권한자) 등록 버튼 | 가시성 범위 |
| `/projects/new` | 프로젝트 등록 | 입력 항목 폼 + 참여자별 role(**PM/PL/참여자**) 선택, PM 1명 필수 · 422/409 오류 표시 | orgRole: TEAM_LEAD·DIVISION_HEAD·ADMIN |
| `/projects/:id` | 프로젝트 상세 | 기본정보 · 상태 뱃지 · 진행률(권한 시 수정) · 배정 목록(**역할 뱃지 PM/PL/참여자**) · lastEditedBy/At · (PM) PM 교체·PL 지정 UI · (완료 시) 이관 버튼 | 가시성 범위 |
| 〃 배정 패널 | 인력 배정 | 배정 추가(사람 검색→기간·월별 M/M) · 종료 처리 · 409 표시 — 프로토타입의 월별 upsert UI는 기간 모델 API(§7)로 재연동 시 조정(2026-08-02 기간 모델 확정) | PM(§6 태그 규칙 — ADMIN 치환 포함) |
| `/utilization` | 가동률 대시보드 | 월 선택 · 팀 필터 · 기본/보정 표 · 과부하(보정>100%) 강조 · 과부하만 보기 | 가시성 범위 |
| `/maintenance/:id` | 유지보수 상세 | 계약 정보(원프로젝트 링크) · 이력 목록(type 필터) · (PM) 이력 추가 | 가시성 범위 |
| 공통 헤더 | 알림 뱃지 | 미읽음 수 — **SSE 즉시 갱신**(⑥, 프로토타입의 구독 로직 재사용), 재연결 시 미읽음 재조회 · 클릭 시 목록·읽음 처리 | 로그인 사용자 |

**공통 UI 규칙**: 모든 목록은 로딩/빈/에러 3상태 · `409 STALE_VERSION` 수신 시 "OO님이 먼저 수정했습니다. 최신 내용을 불러올까요?" → 확인 시 재조회(reload-and-retry) · 권한 없는 버튼은 렌더링하지 않되 서버 403 처리는 항상 존재.

## 부록 B. 시드(데모) 데이터 — `reference/seed/` 기준

`docker compose up` 후 자동 적재(또는 seed 프로파일). DoD의 "한 줄기 시연"과 가동률 예시 검증이 이 데이터로 가능해야 한다. v1.0의 가상 시드(10명·5건)는 폐기.

**시드에 있는 것** (`people.json` 44건 · `projects.json` 382건):
- 인원 44명 · 7부문(AI기술연구소·AX기술연구소·AX솔루션사업부·AX사업기획부·MS사업부·관리•마케팅부·대표) · orgRole: ADMIN 1·DIVISION_HEAD·TEAM_LEAD·MEMBER
- 직급계수(gradeCoeff): 대표이사 2.0 · 부사장 1.8 · 상무 1.7 · 이사 1.6 · 수석 1.5 · 책임 1.2 · 선임 1.0 · 주임 0.8 · 수습 0.5
- 프로젝트 382건: 완료 319 · 진행중 34 · 수주확정 19 · 계약대기 10. 필드: client·solution·engagement·contractMm·기간·progress·managerId·assigneeIds(305건 보유)

**시드에 없는 것 → 적재 정책**:
- `User` 계정 — **확정(2026-08-02)**: 로그인 ID = 시드 email 전체, 초기 비밀번호 `proten1!` 해시 저장(프로토타입 데모 계정 4종과 정합), 최초 로그인 후 변경 안내
- 배정의 **월별 M/M** — assigneeIds만 있음. 가동률 시연을 위해 적재 시 부여 규칙 필요 (12장, PMS-M1 전 결정)
- 배정의 **role** — **확정(2026-08-03)**: `managerId` → `role=PM`(382건 전부, 누락 0건), 나머지 `assigneeIds` → `role=PARTICIPANT`. **`role=PL`은 아무도 지정하지 않는다** — 시드에 근거 데이터가 없어 임의 지정 금지. 필요 시 진행중 34건 중 참여자 2명 이상인 9건에 한해 수동 지정
- `Capacity`(월별 가용 MM) — 기본 1.0 적재로 시작
- 유지보수(`Maintenance`/`MaintenanceLog`) 데이터 — 이관 시연용 완료 프로젝트 1건 지정 + 데모 이력 필요 (12장, PMS-M1 전 결정)

**검증 케이스**:
- 가동률 단위테스트는 고정값: 배정 0.5+0.7, 가용 1.0, coeff 1.2(책임) → 기본 120% / 보정 100% (AC C1-2)
- 시드 스모크용 오버부킹 사례(보정>100% 최소 1명)는 M/M 부여 규칙 확정 시 실제 인물로 지정

## 12. 미해결 / 결정 필요

**에이전트는 임의 구현하지 말고 질문할 것**
- ~~**권한 모델 재기술 (pms 담당 결정)**~~ — 2026-08-03 결정 완료(합집합 판정 + PM/PL/참여자 3단, 상위 `PRD.md` §4 재작성 · PROGRESS 결정 기록). **MCP 담당 확인 대기** — 공용 문서 변경이라 합의 필요
- **가동률 집계에서 대표 직속(HQ_TEAM '프로텐') 제외 여부** — 위 항목에서 분리된 잔여 건(권한이 아닌 집계 규칙). 상위 PRD §3 반영 여부 미결
- **PL 복수 허용 여부** — 스키마는 수용, API 제약은 유예 중. 실무 확인 필요: 참여자 4명 규모 프로젝트(한국관광공사 AI챗봇·대화형 데이터 플랫폼 — 4개 팀 걸침)에서 실제로 파트별 리드가 따로 있었는지
- 가동률 캐시 테이블 도입 여부(성능 필요 시) vs 매 조회 계산
- 마감 임박 알림 D-N 값(예: D-7) — US-F2의 N
- JWT 만료 시간·refresh 정책
- 시드 적재 정책 잔여 — 배정 월별 M/M 부여 규칙 · 유지보수 데모 데이터 (부록 B. 계정 규칙은 2026-08-02 확정)
- 403 에러코드 `FORBIDDEN_FIELD` 명칭 재검토 (프로토타입 호환 확인 후)
- 설정 화면의 조직 트리·직급·권한 편집을 백엔드 엔티티로 승격할지 (현재 로컬 데모 — 부록 A)
- ~~(2차) MCP 챗봇 PAT 검증 지점~~ → v3에서 M0로 승격: `/mcp` 인증 체인(토큰 패스스루·audience)은 루트 ROADMAP M0 + 구현 노트 소유. 이 문서는 접점(애플리케이션 서비스 계약)만 가진다

---

본 PRD는 구 "PMS — AI 구현용 PRD" v1.0(「PMS 기획서 v2」·「PMS 아키텍처 설계서 v2」 파생)의 v3 현행화판. 충돌 시 이 PRD의 AC를 우선한다. AI 어시스턴트 기능 요구는 `docs/PRD-host.md` 소유.
