# 진행 상태 원장 (PROGRESS)

> **모든 세션은 이 파일을 읽는 것으로 시작하고, 이 파일을 갱신하는 것으로 끝난다.**
> 최신 상태가 항상 맨 위. 오래된 세션 로그는 아래로.

## 현재 상태 (2026-07-22)

- **마일스톤:** M-1 (사전 검증) — 1/3 완료 (목업 서버 구성 ✅, 브랜치 `feat/m-1-mock-mcp-server` PR 대기)
- **협업:** 2인 개발 — GitHub Flow (브랜치 규칙: `docs/conventions/git-workflow.md`)
- **코드:** `pms-mcp-mock/` 목업 MCP 서버 (Boot 4.0.7 · Spring AI 2.0.0 · Streamable HTTP · 포트 8090, B-0 무인증). 조회 도구 5종 등록·테스트 27개 green. 재사용 자산: `reference/seed/`, `frontend/`.
- **다음 작업:** M-1 Inspector/Claude 데스크탑 검증 (도구 카탈로그·자연어 실험, 부록 B-1) → `get_project` 분리 결정
- **차단 요소:** 없음

## 결정 기록 (Decision Log)

| 날짜 | 결정 | 근거 |
|------|------|------|
| 2026-07-13 | 하네스 구축 후 재시작. 기존 코드는 참고만 | 무계획 생성 코드의 구조 부채 회피 |
| 2026-07-13 | 개발 도구는 Claude Code | — |
| 2026-07-13 | ~~저장소 위치 `C:\Projects\pms_mcp_v2`~~ (2026-07-16 개정 아래) | OneDrive 동기화가 git·Gradle과 충돌 |
| 2026-07-16 | **정본 경로 `C:\Projects\playground\pms_mcp` 확정** + git init + 리모트 `https://github.com/jeonginwoo/pms_mcp.git` 연결 | 폴더 이동 중 `.git`·`.github` 유실 발견 — 현 위치를 정본으로 재확정하고 Persist 축 복구. 기존 코드 원본(`C:\Projects\pms_mcp`)은 로컬에 없음 확인, 재사용 자산은 이미 확보됨 |
| 2026-07-13 | 기존 코드 중 시드 데이터·프론트만 재사용, 백엔드는 재작성 | 데이터·UI는 코드 품질과 무관한 자산. 기존 코드는 `C:\Projects\pms_mcp`에서 읽기 전용 참고 |
| 2026-07-13 | 하네스 보강 4종: eval 30케이스 초안 · `/gate` 커맨드 · react-ts 컨벤션 · CI(yml) | G1 게이트 실체화, 게이트 통과 절차 강제, 프론트 컨벤션 공백 해소, 하네스 밖 이중 검증 |
| 2026-07-13 | 알림 전달은 **SSE 푸시** (당초 폴링 → 개정) | 프로토타입에서 40명 규모 검증 완료 + 챗 스트리밍과 기반 공유. 기술_선택_근거 9장·가이드 1-A 개정 |
| 2026-07-13 | LLM 모델: 개발·테스트 단계는 무관, 출시 전 확정 | 테스트 가능하면 충분. 단가·학습 미사용 조항 확인은 출시 게이트 전제로 유지 (PRD 11장 5번) |
| 2026-07-13 | PRD v1.2 **확정판 승격** | 하네스·eval의 원천으로 실사용 중 — 초안 상태 해소. 구현 가이드 장 번호 참조도 v1.2 기준으로 현행화 |
| 2026-07-18 | **2인 개발 전환 + GitHub Flow 브랜치 전략** (main 보호·1작업=1브랜치=1PR·squash 머지·모듈 단위 분담) | GitHub 협업 시작. 상세: `docs/conventions/git-workflow.md` (CLAUDE.md import) |
| 2026-07-18 | **하네스 v2 — 참고 레포(Eunji1217/pms) 융합**: skills 2종(mcp-tool·module-scaffold)·agents 3종(architect·boundary-reviewer·verifier)·Stop 훅·`scripts/verify.sh`(로그 오프로딩)·`scripts/ralph.sh`(작업 큐 자율 루프)·`/sync-docs`·CLAUDE.md 교훈 섹션 | 하네스 엔지니어링 개념노트 비교 검토. 전부 이 레포의 패키지 구조(`internal/`)·구조적 원칙에 맞게 번안. `/plan`은 `/next`의 계획 단계와 중복이라 미채택 |
| 2026-07-18 | **하네스 지시 파일 영어 메인 전환** — CLAUDE.md·컨벤션 3종·commands 5종·agents 3종·skills 2종·scripts 3종. 한국어 리터럴(에러 메시지 문구·확인 카드 라벨·주석은 한국어 규칙·문서 파일명)은 보존 | LLM 지시문은 영어가 더 안정적. 도메인 원천 문서(PRD·가이드·근거)와 이력 원장(PROGRESS·ROADMAP)은 한국어 유지 — 기록물이지 프롬프트가 아님 |
| 2026-07-22 | **보정 가동률 공식 정본화**: rate=round(mm×100), adjustedRate=round(rate×직급계수), 과부하 ⇔ 보정>100 — `UtilizationQueryPort` Javadoc에 계약으로 명문화 | PRD/가이드에 대수식이 없고 유일한 정의가 프로토타입(`frontend/src/store.jsx:168-173`). PRD S-1 예시 수치(0.8+0.5MM→120%)는 자체 모순이라 결과 수치(120%·105%)를 정본으로 삼음 |
| 2026-07-22 | 가동률 report는 배정 없는 인원도 **0%로 포함** (빈 목록 아님) | 빈 목록이면 가장 여유 있는 사람이 S-2 "여유 인력 탐색"에서 안 보임 |
| 2026-07-22 | 목업 B-0: MY_TEAM/DIVISION 기준자를 `DEFAULT_REQUESTER_ID=16`(남도린)으로 고정 | B-0엔 인증이 없음. B-2에서 SecurityContext 클레임으로 교체 |
| 2026-07-22 | 목업 스택: Boot **4.0.7** + Spring AI 2.0.0 (Initializr 생성, toolchain 25 + foojay) | Initializr 기본값(4.1.0)이 아닌 프로젝트 표준 4.0.x 유지. `@McpTool`은 `org.springframework.ai.mcp.annotation` 패키지(실제 jar에서 확인) |
| (미결) | `get_project` 분리 여부 | M-1 목업 실험 결과로 결정 (PRD 11장) |

## 미해결 이슈

- `docs/evals/eval-cases.md`는 초안 — M-1 목업 실험 결과로 입력 문구·기대값 갱신 필요
- git-workflow.md의 "docs/chore는 main 직접 커밋 허용" 예외가 ruleset(모든 push에 PR 필수)과 충돌 — bypass 추가 또는 문서 개정 중 택일 필요
- CI(`.github/workflows/ci.yml`)가 루트 `gradlew`만 검사해 `pms-mcp-mock/`은 CI에서 빌드되지 않음 — 로컬 `bash scripts/verify.sh`는 한 단계 하위 gradlew를 인식하므로 커버됨. 목업은 폐기 예정 자산이라 CI 수정은 보류(M0에서 루트 gradlew가 생기면 자연 해소)

## 작업 큐 (Ralph 루프용)

> `bash scripts/ralph.sh`가 이 섹션의 `- [ ]`를 새 세션으로 하나씩 소화한다 (`/next --loop`).
> **계획이 이미 합의된, 커밋 단위로 잘게 쪼갠 작업만** 넣는다. 발견한 후속 일감은 `- [ ]`로 추가만 하고 몰래 실행하지 않는다.

- [ ] M-1 B-2: 목업에 HS256 테스트 JWT + 역할별(팀원/팀장/본부장/관리자) 가시성 흉내 추가 (부록 B-2 — 2026-07-22 계획 승인 시 후속으로 합의)

---

## 세션 로그

### 형식 (복사해서 사용)

```
### YYYY-MM-DD — <작업 요약>
- 완료: <ROADMAP 체크 항목>
- 검증: <실행한 테스트/Inspector 결과>
- 미해결: <다음 세션으로 넘기는 것>
- 다음 작업: <구체적으로>
```

### 2026-07-22 — M-1 목업 MCP 서버 구성 (B-0)
- 완료: ROADMAP M-1 첫 항목 — `pms-mcp-mock/` 신설 (Boot 4.0.7 · Spring AI 2.0.0 · `spring-ai-starter-mcp-server-webmvc` · Streamable HTTP · 포트 8090 · 무인증 B-0). 부록 B 구조 그대로: `MockData`(사람 9·프로젝트 7·배정 15·이력 64 — 2026-07 과부하 2명(120%·105%)·50건 절단·주입 문자열 등 심은 케이스), `port/` 4계약+DTO 8종(승격 대상), `mock/` 4구현(폐기 대상), `mcp/` 4클래스=조회 도구 5종(description은 가이드 5-2 문안 그대로)
- 검증: TDD 사이클 6회(Red→Green) 테스트 27개 green · `bash scripts/verify.sh` ALL GREEN · bootRun + Streamable HTTP curl 스모크(initialize→tools/list 5종→tools/call: `list_overbooked(2026-07)`=남민준 120/남도린 105+원인배정, `get_utilization(2026-08,MY_TEAM)`, `search_projects(진행중,API)`=101 1건, `list_maintenance_logs(106)`=정확히 50건 최신순)
- 미해결: CI가 하위 폴더 gradlew 미인식(미해결 이슈 기록). curl 한글 인코딩 gotcha는 CLAUDE.md 교훈에 추가
- 다음 작업: Inspector/Claude 데스크탑 검증 (부록 B-1) → 도구 description 다듬기 + `get_project` 분리 결정 + eval 초안 갱신
- 완료: CI 첫 5회 실행 전부 success 확인(최신 CI #5, 커밋 5dc63e9 포함). main 보호 ruleset `protect-main` 생성(Active, 대상 default branch) — PR 필수(승인 0, 승인 1회는 git-workflow 관례로 운용), 필수 status check `backend`·`frontend`(GitHub Actions), 머지 방식 Squash만 허용, force push·브랜치 삭제 차단. General 설정에서 merge commit·rebase merging 비활성 → squash 전용. 협업자 초대는 사용자가 직접 완료
- 미해결: git-workflow.md의 docs/chore main 직접 커밋 예외 vs ruleset PR 필수 충돌 (미해결 이슈 참조)
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-18 — 하네스 영어 메인 전환
- 완료: 하네스 지시 파일 전체를 영어로 전환 — CLAUDE.md, docs/conventions/(java-spring·react-ts·git-workflow), .claude/commands/ 5종, .claude/agents/ 3종, .claude/skills/ 2종, scripts/ 3종(주석·출력 메시지). 한국어 리터럴은 보존: MCP 에러 메시지 문구("담당자만 가능"·"해당 데이터 없음"), 확인 카드 라벨([실행]/[취소]), "Javadoc·주석은 한국어" 규칙 자체, 한국어 문서 파일명, 커밋 메시지 예시
- 제외: PRD·구현 가이드·기술_선택_근거·PROGRESS·ROADMAP·eval-cases — 도메인 원천 문서와 이력 원장은 프롬프트가 아니라 기록물이므로 한국어 유지
- 검증: bash -n 3종 통과, verify.sh --quick 실행(skip 가드 정상), stop-verify.sh exit 0
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-18 — 하네스 v2 (참고 레포 융합) + 2인 협업 브랜치 전략
- 완료: 참고 레포(Eunji1217/pms)·하네스 개념노트와 비교 후 융합 — `.claude/skills/`(mcp-tool·module-scaffold), `.claude/agents/`(architect·boundary-reviewer·verifier), Stop 훅(`scripts/hooks/stop-verify.sh` — Java 변경+컴파일 깨짐이면 세션 종료 차단), `scripts/verify.sh`(로그를 build/last-verify.log로 오프로딩, gradlew/frontend 부재 가드), `scripts/ralph.sh`(작업 큐 기반 신선한 컨텍스트 루프, main 실행 금지 가드), `/sync-docs` 커맨드, `/next`에 루프 모드+브랜치 단계, `/wrap-up`에 브랜치/PR 단계, CLAUDE.md 교훈 섹션, PROGRESS에 작업 큐 섹션. settings.json에 훅 등록 + `bash scripts/*`·`git branch/switch` 허용 + `*.pem` 읽기 차단
- 완료(추가): 2인 개발 전환 반영(CLAUDE.md) + GitHub Flow 브랜치 전략 문서 `docs/conventions/git-workflow.md` 신설(CLAUDE.md import 포함)
- 검증: bash -n 문법 검사 3종 통과, verify.sh 실행(초기화 전 skip 동작 확인), stop-verify.sh 수동 실행(exit 0 확인)
- 미해결: GitHub 저장소 보호 규칙 설정은 사람 작업 (미해결 이슈에 기록)
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-16 — 하네스 점검 + Persist 축 복구
- 완료: 하네스 전체 점검(치명 3·보완 5 발견) → 전부 반영. git init + 리모트 연결 + 초기 커밋/푸시, `.github/workflows/ci.yml` 재작성(gradlew·lint/test 스크립트 존재 시 자동 포함되는 가드 방식), settings.json 권한 정비(gradlew 전체 허용, git add/commit 허용, `**/.env*`·`application-local*` deny), /verify에 프론트 테스트 + Gradle 부재 가드 추가, CLAUDE.md 죽은 경로 정리 + 비마일스톤 커밋 규칙(`chore:`/`docs:`), 하네스_가이드 폴더 구조도 현행화
- 완료(추가): 하네스_가이드.md를 신규 참여자용 설명서로 전면 확장 — 하네스 개념·파일별 역할 상세·세션/마일스톤 사이클 다이어그램·유지보수 기준표
- 완료(추가): java-spring.md 보완 + 전면 한글화 — 타 프로젝트 잔재 예제(FontHandler)를 PMS 도메인 예제로 교체, Spring 규칙 신설(생성자 주입·@Transactional 위치·엔티티 노출 금지·예외→HTTP 매핑표·404 은닉), record 우선 + Lombok 조건부(@Setter/@Data 금지), Modulith 패키지 구조, 로그 금지 항목, Testcontainers 기준
- 검증: `git remote -v`·push 성공, GitHub Actions 첫 실행 success 확인
- 미해결: eval 초안의 목업 기반 갱신 (기존과 동일)
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-13 — 원천 문서 점검 + 결정 3건 반영
- 완료: 3문서 점검(`docs/reviews/2026-07-13_원천문서_점검.md`), SSE 개정(기술_선택_근거 9·13장, 가이드 1-A/1-B/1-C), PRD v1.2 확정 승격, PRD 장 번호 참조 4곳·protocolVersion(2025-11-25)·모듈 목록(chat·mcpconfig) 현행화
- 검증: Spring AI 2.0.0 GA(2026-06-12)·MCP 스펙 현행 리비전 웹 확인. **주의: 2026-07-28 MCP 스펙 대개정(stateless화) 예고 — SDK 채택 시 가이드 4-4 재갱신 필요**
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-13 — 하네스 보강 (eval·gate·컨벤션·CI)
- 완료: `docs/evals/eval-cases.md`(30케이스 초안), `.claude/commands/gate.md`, `docs/conventions/react-ts.md`(+CLAUDE.md import), `.github/workflows/ci.yml`, java-spring.md에 TDD 섹션
- 미해결: 원격 저장소 연결, eval 초안의 목업 기반 갱신
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B) → 목업으로 eval 입력·기대값 확정

### 2026-07-13 — 저장소 이동 + 재사용 자산 확보
- 완료: 하네스를 `C:\Projects\pms_mcp_v2`로 이동, `reference/seed/`(JSON 2종)·`frontend/` 복사, git init + 초기 커밋
- 검증: 시드 JSON 파싱 확인 (people 44 · projects 382)
- 다음 작업: M-1 목업 MCP 서버 (구현 가이드 부록 B)

### 2026-07-13 — 하네스 세팅
- 완료: CLAUDE.md, ROADMAP, PROGRESS, .claude 커맨드/설정, 컨벤션 배치
- 다음 작업: git init → M-1 목업 서버
