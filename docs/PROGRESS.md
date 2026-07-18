# 진행 상태 원장 (PROGRESS)

> **모든 세션은 이 파일을 읽는 것으로 시작하고, 이 파일을 갱신하는 것으로 끝난다.**
> 최신 상태가 항상 맨 위. 오래된 세션 로그는 아래로.

## 현재 상태 (2026-07-18)

- **마일스톤:** M-1 (사전 검증) — 시작 전
- **협업:** 2인 개발 — GitHub Flow (브랜치 규칙: `docs/conventions/git-workflow.md`)
- **코드:** 백엔드 없음(새로 시작). 재사용 자산만 확보: `reference/seed/`(사원 44·프로젝트 382 JSON), `frontend/`(React 프로토타입, M1에서 재연동).
- **다음 작업:** M-1 목업 MCP 서버 구성 (구현 가이드 부록 B)
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
| (미결) | `get_project` 분리 여부 | M-1 목업 실험 결과로 결정 (PRD 11장) |

## 미해결 이슈

- `docs/evals/eval-cases.md`는 초안 — M-1 목업 실험 결과로 입력 문구·기대값 갱신 필요
- GitHub 저장소 설정 필요(사람 작업): main 브랜치 보호 규칙(PR 필수·CI 필수), squash merge 기본값, 협업자 초대

## 작업 큐 (Ralph 루프용)

> `bash scripts/ralph.sh`가 이 섹션의 `- [ ]`를 새 세션으로 하나씩 소화한다 (`/next --loop`).
> **계획이 이미 합의된, 커밋 단위로 잘게 쪼갠 작업만** 넣는다. 발견한 후속 일감은 `- [ ]`로 추가만 하고 몰래 실행하지 않는다.

(비어 있음)

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
