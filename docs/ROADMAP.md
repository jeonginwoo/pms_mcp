# 구현 로드맵 (M-1 → M3)

> 원천: `PMS_MCP_구현_가이드.md` 12장 체크리스트 + `PMS_AI기능_PRD.md`.
> 규칙: 마일스톤은 **검증 게이트를 통과해야** 다음으로 넘어간다. 진행 상태 체크는 `PROGRESS.md`에 기록.

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
