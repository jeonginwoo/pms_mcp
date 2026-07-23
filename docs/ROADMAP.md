# 구현 로드맵 — MCP 트랙 (M-1 → M3) + PMS 본체 트랙 (P0 → P3)

> 원천: `PMS_MCP_구현_가이드.md` 12장 체크리스트 + `PMS_AI기능_PRD.md`.
> 규칙: 마일스톤은 **검증 게이트를 통과해야** 다음으로 넘어간다. 진행 상태 체크는 `PROGRESS.md`에 기록.
> 분담(2026-07-23): **MCP 트랙 / PMS 본체 트랙**을 한 사람씩 맡아 병행한다 (모듈 단위 분업 — git-workflow.md §3).
> 두 트랙의 접점: **M0은 P0의 JWT 발급이 선행 조건**이고, **M1의 조회 도구 실데이터 전환은 P2 완료가 전제**다.

## M-1 — 사전 검증 (코드 작성 전, 지금 바로 가능)

- [x] `pms-mcp-mock` 목업 MCP 서버 구성 (구현 가이드 부록 B) — 2026-07-22, B-0 무인증 단계
- [ ] Inspector / Claude 데스크탑으로 도구 카탈로그·설명·프롬프트 검증
- [ ] 미해결 질문 결정: `search_projects` 상세 조회를 `get_project`로 분리할지 (PRD 11장)

**게이트:** 목업으로 대표 시나리오 S-1~S-3이 자연스럽게 흘러가는지 확인. 도구 시그니처 확정.

## M0 — 뼈대: `/mcp` + 토큰 패스스루 (구현 가이드 4장)

- [ ] PMS: `spring-ai-starter-mcp-server-webmvc` + `oauth2-resource-server` 의존성 추가
- [ ] `/mcp` 보안 체인 구성 (REST와 같은 검증 지점 재사용, audience 검증)
- [ ] `whoami` 도구 1개 등록

**게이트:** Inspector로 인증 3케이스(유효 토큰 / 무효 토큰 / audience 불일치) 검증 통과.

## M1 — 조회 전용 (킬러 기능, 구현 가이드 5~7장)

- [ ] 조회 5도구: `search_projects` / `get_utilization` / `list_overbooked` / `find_person` / `list_maintenance_logs` (이력 최근 50건 절단)
- [ ] 리소스 `pms://projects/{id}` 등록
- [ ] 에러 매핑 공통화
- [ ] AI 호스트 신규 앱: ChatClient (PRD 시스템 프롬프트 + 10턴 메모리 + 날짜/identity 주입) + PmsMcpConnector + `/chat`
- [ ] BFF `/api/chat` (2,000자 차단 · 일 50질의 리밋 · 위임 토큰 발급) + React 챗 위젯
- [ ] Modulith/ArchUnit 경계 테스트
- [ ] 권한 4종 토큰(본부장/팀장/팀원/관리자) 통합 테스트

**게이트 (G1):** Eval 셋 30케이스 — 치명 0건 · 합격률 ≥90% · 사람 승인 (구현 가이드 10-1).

## M2 — 안전한 쓰기 (읽기 운영 2주 후)

- [ ] `update_progress` 2단계 확인 패턴 + 확인 카드 UI
- [ ] AuditLog `source=MCP` 표식 (전 도구 호출 로깅, 질문 원문 30일 보관)
- [ ] 409(낙관적 락) → 도구 에러 → 모델 재조회 흐름 검증

**게이트 (G2):** 읽기 운영 2주 + 쓰기 Eval 통과 (F3 미확인 쓰기 0건).

## M3 — 프롬프트·외부 도구

- [ ] `@McpPrompt` 3종: `monthly_utilization_report` / `project_status_brief` / `overbooking_check` + 위젯 슬래시 명령
- [ ] (필요 시) Slack/GitHub MCP 하이브리드 연결, 외부 전송 확인 카드 필수화
- [ ] (필요 시) 고정 자동화 → PMS EDA NotificationChannel

**게이트:** 프롬프트 3종 실사용 피드백 수집.

---

# PMS 본체 트랙 (P0 → P3)

> 원천: PRD + java-spring.md의 구조 원칙(모듈·레이어·가시성/404 은닉·TDD). `pms_back/`(Boot 4.0.7 · Modulith 2.0.7 · `com.proten.pms`)에서 진행.
> 프론트는 기존 프로토타입(`frontend/`)을 단계별로 실연동한다 — 화면 신규 제작이 아니라 재접속.

## P0 — 뼈대 + identity (로그인·조직·가시성 기반)

- [x] pms_back 워킹 스켈레톤 — FE-BE-DB `/api/health` + 전체 스택 docker compose (2026-07-23, PR #3)
- [ ] common: `@RestControllerAdvice` + ProblemDetail 공통 예외 매핑 — 401/403(담당자만 가능)/404(가시성 은닉 겸용)/409/422 매핑표 구현
- [ ] identity 도메인: Division·Team·Grade(직급계수)·Person(조직 역할=가시성 축) + 순수 단위 테스트
- [ ] identity JPA 매핑 + Testcontainers 저장소 테스트 + 시드 적재(`reference/seed/` → `pms_back/src/main/resources/seed/`, 44명)
- [ ] identity 인증: 로그인 API(BCrypt·JWT 발급·무상태) + 시큐리티 필터 체인(CSRF off·Bearer)
- [ ] identity 가시성 축: 본부장=본부 / 팀장=팀 / 팀원=본인 스코프 판정 — 애플리케이션 서비스 + 역할 4종 테스트
- [ ] frontend 실연동 1단계: 로그인 + `/api/me` + `/api/people`

**게이트 (GP0):** `verify.sh` ALL GREEN + 역할 4종(본부장/팀장/팀원/관리자) 로그인·가시성 통합 테스트 + 프론트 로그인 실동작. **통과 시 MCP M0 착수 가능 (JWT 재사용).**

## P1 — project 모듈 (프로젝트 관리 본체)

- [ ] project 도메인: Project 애그리거트(상태머신 계약대기→수주확정→진행→완료, 진행률 0–100 역행 금지) + ManMonth VO + ProjectAssignment(BR-09 중복 배정 금지) 단위 테스트
- [ ] project JPA(`@Version` 낙관적 락) + Testcontainers 테스트(409 충돌 경로 포함) + 시드 적재(382건)
- [ ] project 권한 축: 관리자/담당자 판정 — 가시성 축과 분리 유지, 가시성 밖은 404 은닉
- [ ] project REST: 목록(가시성 필터)·상세·CRUD·배정 CRUD·진행률 갱신(낙관적 락)
- [ ] 도메인 이벤트 `MemberAssignedToProject` 발행 (Event Publication Registry — `spring-modulith-starter-jpa` 전환)
- [ ] frontend 실연동 2단계: 프로젝트 목록/상세/편집 + 배정 편집(409 시 재조회 UX)

**게이트 (GP1):** verify ALL GREEN + 권한 4종 통합 테스트 + 프론트 프로젝트 CRUD 실동작.

## P2 — resource + maintenance (킬러 기능 데이터 기반)

- [ ] resource: 월별 가동률 계산 — rate=round(mm×100), adjustedRate=round(rate×직급계수), 과부하 ⇔ 보정>100 (2026-07-22 결정), 배정 없는 인원 0% 포함
- [ ] resource: 배정 이벤트 구독 멱등 재계산(AFTER_COMMIT) + REST 조회(월/스코프)
- [ ] maintenance: 프로젝트 완료→핸드오버(`sourceProjectId`, 단일 트랜잭션 직접 호출) + MaintenanceLog append-only(SR/장애/정기점검/패치) + 최근 50건 절단 목록 API
- [ ] frontend 실연동 3단계: 가동률 대시보드 + 유지보수 화면

**게이트 (GP2):** 목업 MockData에 심은 케이스(과부하 120%·105%, 50건 절단)가 실데이터 계산으로 재현 + verify ALL GREEN. **통과 시 MCP M1 조회 도구의 목업 port 4계약을 실구현으로 승격 가능.**

## P3 — notification + audit

- [ ] notification: 이벤트 구독→인앱 알림 저장 + SseHub `/api/notifications/stream`(SSE 푸시, Nginx 버퍼링 off — nginx.conf 반영됨)
- [ ] common: AuditLog append-only + 감사 이력 UI + 포트폴리오 대시보드

**게이트 (GP3):** SSE 실시간 수신 확인 + verify ALL GREEN.
