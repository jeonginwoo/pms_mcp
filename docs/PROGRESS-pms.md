# 진행 상태 — pms 트랙 (PMS 담당: 도메인·애플리케이션 서비스·프론트)

> 공용 상태·결정 기록·미해결 이슈는 `PROGRESS.md`. 이 파일은 pms 트랙의
> 다음 작업과 세션 로그만 담는다.

## 온보딩 (처음 시작할 때)

1. 루트 `CLAUDE.md` → `pms/CLAUDE.md` → `docs/PROGRESS.md` → `docs/ROADMAP.md` 순으로 읽기
2. `pms/` 안에서도 `/mcp` 어댑터 모듈은 MCP 담당 소유 — 애플리케이션 서비스 API 변경은 공용 결정 기록 경유

## 현재 상태 (2026-08-06)

- **다음 작업:** 2026-08-06 공용 변경 4건(완료 전이 재설계·billable·프로젝트별 권한 커스텀·**유지보수 재설계**)에 대한 **MCP 담당 확인** → 게이트 P 승인(PRD-pms v2.4 포함 일괄) → PMS-M0 스캐폴드. 시드 적재 잔여(월별 M/M·billable 팀 목록·유지보수 시트→JSON 변환+engineerId 매핑)는 PMS-M1 전 결정. 유저_시나리오에 유지보수 여정(계약 목록·이슈 처리) 추가 검토 + HTML 렌더링(게이트 P 리뷰 보조) 예정
- **차단 요소:** 없음 (게이트 P 대기. 권한 모델 MCP 확인은 2026-08-03 완료)

## 세션 로그

### 형식 (복사해서 사용)

```
### YYYY-MM-DD — <작업 요약>
- 완료: <한 것 + 검증 결과>
- 미해결: <다음 세션으로 넘기는 것>
- 다음 작업: <구체적으로>
```

### 2026-08-06 — 유지보수 재설계(계약/사이트/이슈 3층 + phase 탭) → PRD-pms v2.4
- 완료: **유지보수 도메인 재설계**(공용 결정 기록 — MCP 확인 대기): 실무 자료 실측(2026 유지보수 시트 + 구 이슈 게시판 — 계약:고객사 1:N(가온아이 1계약 ~45사이트)·OEM 계약은 원천 프로젝트 부재·열린 이슈 23건 중 할당 1건) 근거로 완료 프로젝트 1:1 파생 모델 폐기 → `MaintenanceContract`(연 단위, sourceProjectId nullable — 이관+직접 등록 입구 2개) / `MaintenanceSite`(1:N, **담당 엔지니어 정본=사이트 engineerId** — 사용자 확정) / `MaintenanceContact`(연락처 정규화) / `MaintenanceIssue`(구 게시판 대체 — type 3종·상태 4단·기본 배정=사이트 담당·append-only 코멘트로 구 MaintenanceLog 불변식 계승). EPIC D 재작성(US-D1 이관 시 계약 필수값·D2 직접 등록 orgRole·D3 이슈 전사·D4 전사 조회) · §7 유지보수 API 재편 + `?phase=` · §8 `MaintenanceIssueRegistered` 이벤트 · 부록 A 화면 3종(계약 목록/상세·이슈 목록 — 담당자 컬럼 상시 노출·미배정/내 담당 필터) · 부록 B 시드=시트 실데이터(마스킹 없음 — 사용자 확정). **phase 탭** = category 컬럼 없이 status 파생(영업/솔루션, 서버 단일 정의). **미채택 결정**: sales/SalesInfo·정기점검 모델링·만료 임박 알림(고통 확인 후 추가). 상위 PRD §4-2 표 "이관/이력"→"이관"·§4-3 계약 등록 행 추가. 구 미해결 "프로젝트:Maintenance 1:1 vs 1:N" 해소(projectId 단순화 불가 — `list_maintenance_logs` 접점, MCP 확인 대기). verify.sh pms SKIP(코드 이전 단계) 정상
- 미해결: MCP 담당 확인 4건(완료/재개·billable·권한 커스텀·유지보수 재설계 — 접점: `list_maintenance_logs` id·구 logs API 대체·`?phase=`), D4-3 전사 조회는 게이트 P에서 확인, 유저_시나리오 유지보수 여정 추가 검토, 시드 잔여(월별 M/M·billable 팀 목록·시트→JSON 변환)
- 다음 작업: MCP 확인 4건 → 게이트 P 승인(v2.4 포함 일괄) → PMS-M0 스캐폴드

### 2026-08-06 — 프로젝트별 권한 커스텀 + 프로젝트별 감사 이력 뷰 → PRD-pms v2.3 (PRD 리뷰 반영)
- 완료: **①§4-2 표를 기본값으로 전환 + 프로젝트별 권한 커스텀 신설**(공용 결정 기록 — MCP 확인 대기): 전역 권한 관리 탭 없이 프로젝트 설정에서 PM이 역할×기능 토글 조정. 상위 PRD §4-1 판정식(`projectPerm(project, …)`)·§4-2 커스텀 규칙(조정 4종 양방향 · 고정 = PM 열/조회/삭제·이관 · 완료·재개 한 토글 · 역할 신설 미채택) + PRD-pms v2.3(US-A8 7개 AC · `ProjectPermissionOverride` — override만 저장 · `GET/PUT /projects/{id}/permissions` · `422 IMMUTABLE_PERMISSION` · §6 도입부 "AC는 기본값 전제" 규칙 · 부록 A 권한 패널 · Out of Scope 커스텀 역할 금지 명시). 기본값 무변경이라 기존 AC·시나리오·eval 기대값 영향 없음. **②프로젝트별 감사 이력 뷰**(pms 내부 — MCP 확인 불요): "프로젝트 로그/통합로그 분리" 요구를 **단일 AuditLog + 권한 다른 두 조회 뷰**로 설계(이중 기록 기각 — 용량 2배·불변식 이원화 리스크, 용량 자체는 비쟁점: 후한 추정 연 7.3만 행). `AuditLog.projectId`(nullable) · `GET /projects/{id}/audit` **가시성 범위 전체**(참여자 포함 — 사용자 확정) · US-G2 3개 AC · 부록 A 이력 탭 · 통합 `/api/audit`는 ADMIN 전용 유지. verify.sh SKIP(코드 이전 단계)
- 미해결: MCP 담당 확인 3건(완료/재개·billable·**권한 커스텀** — 거동 변화: 참여자 진척률 off 프로젝트에서 `update_progress` 403), 유저_시나리오에 권한 패널·이력 탭 웹 여정(WS) 추가 검토, 기존 잔여(`list_maintenance_logs` id·시드 적재·HTML 렌더링)
- 다음 작업: MCP 확인 → 게이트 P 승인 → PMS-M0 스캐폴드

### 2026-08-06 — 완료 전이 재설계 + 12장 일괄 해소 → PRD-pms v2.2
- 완료: **완료 전이 재설계**(공용 결정 기록 — MCP 확인 대기): 100% 자동 전이 폐지 → 명시적 완료 처리(`/complete`, progress=100 전제)·재개(`/reopen`, 완료→진행중·progress=90 리셋·사유 없음) **배정 전원** 권한, 완료 상태 진척률 수정 `409 PROJECT_COMPLETED`, 완료 지연 D+7 리마인드(PM·PL)·재개 시 미읽음 회수 — US-A7·US-F3 신설, A2-3·A2-8·A5-1·§5 재작성, 상위 PRD §4-2 표 확장. 근거: 참여자 100% 저장이 A5 상태 전이 권한을 우회하는 부수효과 + 실무 100%≠완료 + PM 편중(1인 128건) 병목. **billable 모집단**(공용 결정 기록 — MCP 확인 대기): 시드 실측 18/44 근거, 상위 PRD §3·C1-5·부록 B. **PL 복수 허용**(결정 기록): A1-7·`MULTIPLE_PL` 삭제. **12장 잔여 5건 해소**: D-N=7(F2-1) · JWT access 1h+refresh 14일(§7) · `FORBIDDEN_FIELD`→`FORBIDDEN`(프로토타입 미사용 grep 확인) · 가동률 캐시 미도입 · 설정 편집 탭 승격 안 함. 연동 반영: 유저_시나리오(SC-23·§7 #3 해소·WS-01·WS-02) · eval D-08 기대값 확정+확장 후보 1건(host 소유 — MCP 확인에 포함). verify.sh pms SKIP(코드 이전 단계) 정상
- 미해결: MCP 담당 확인 2건(완료/재개 — eval D-08·SC-23 선반영 포함 · billable), `list_maintenance_logs` id 도메인 확인(1:1 vs 1:N), 시드 적재 잔여(월별 M/M·유지보수 데모·billable 팀 목록), 유저_시나리오 HTML 렌더링
- 다음 작업: MCP 확인 → 게이트 P 승인 → PMS-M0 스캐폴드

### 2026-08-03 — PRD-pms 정합성 리뷰 → v2.1 (권한 재작성 직후 잔여 검토)
- 완료: 발견 10건 → 수정 14건 적용. ①ADMIN 태그 통일(§6 도입부 치환 규칙 1문장, A2·B1·B2·D1·D2·부록 A에서 `· ADMIN` 제거 — 상위 §4-1 정합) ②**A5-3 신설**(문서 전체에 없던 프로젝트 정보 수정 권한 AC — 참여자 403, US-A5에 [PM·PL] 태그) ③A6 구멍 보강(A6-3 해제=PARTICIPANT 변경 · **A6-7 신설**: `/roles`로 PM 지정 `422 INVALID_ROLE`·2번째 PL `422 MULTIPLE_PL` · 자동 생성 배정 기본값 기간=지정일~종료일·monthlyMM=0) ④유령 라우트 해소(`PUT /assignments/{id}`→**B1-4 신설**+`AssignmentUpdated` 이벤트, `GET /api/me/account`→H1-1 대응 — 프로토타입 api.js:84 실사용 확인) ⑤AuditLog 정리(STATE_CHANGE는 §5 전이 전용 — A6-1·E1-1→UPDATE) ⑥에러 표 422 행 보완(PM_REQUIRED·MULTIPLE_PM·MULTIPLE_PL·INVALID_ROLE) ⑦H1-2 email 중복 `409 DUPLICATE_EMAIL` ⑧§6 도입부 거짓 문장 제거. 판단 3건은 단순·표준 원칙으로 확정(monthlyMM=0·INVALID_ROLE·AssignmentUpdated — pms 내부, 협업 접점 무변경). verify.sh pms SKIP(코드 이전 단계) 정상
- 미해결: MCP 담당 확인 대기(권한 모델 공용 문서 변경 — `whoami` 유효 권한 반영 여부 포함), 12장 잔여(HQ 가동률 집계 제외·PL 복수 실무 확인·D-N·JWT 정책), 시드 적재 잔여(월별 M/M·유지보수 데모)
- 다음 작업: MCP 담당 합의 → 게이트 P 승인(문서 5종 중 기술_선택_근거 v2·구현 노트는 host 트랙 미작성) → PMS-M0 스캐폴드

### 2026-08-03 — 권한 모델 확정 (12장 첫 항목 해소)
- 완료: 시드 실측으로 근거 확보(PM의 orgRole=MEMBER **91/382건 24%** · `managerId` ∈ `assigneeIds` **305/305** · PM 편중 1인 128·55·36건 · 참여자 최대 4명 · 2개 팀 이상 46건) → 상위 `PRD.md` §4 재작성(합집합 판정 4-1 · PM/PL/참여자 3단 4-2 · 조직 권한 4-3 · 가시성 확장 4-4), `PRD-pms.md` 2장·§4 도메인(`ProjectAssignment.role{PM,PL,PARTICIPANT}` 정본화)·EPIC A AC 재작성(A1-4~A1-7·A2-1·A2-4·A3-1·A3-3·A4-2·**US-A6 역할 지정·교체 신설**)·EPIC B·D·E 역할 태그 정정·부록 A 화면 표·부록 B 적재 규칙·1장 Out of Scope·12장 반영, 공용 결정 기록 추가
- 미해결: **MCP 담당 확인 대기**(공용 문서 변경 — `whoami` 유효 권한 반영 여부 포함), PL 복수 API 제약 해제 여부(실무 확인), HQ 가동률 집계 제외(권한에서 분리된 잔여 건)
- 다음 작업: MCP 담당 합의 → 게이트 P 승인 → PMS-M0 스캐폴드

### 2026-08-02 — PRD-pms v2.0 작성 (host 트랙 세션에서 대행 — 분리 결정 합의 후)
- 완료: 구 "PMS — AI 구현용 PRD" v1.0 현행화 이관(충돌 7건 — 스택·전제·시드·MCP 시점·마일스톤 라벨 PMS-M0~M6·SSE·ADMIN 가시성) + `frontend/` 프로토타입 대조 보완(웹 2단계 진행률 US-A2·상태 전이 강제 US-A5·사용자 CRUD US-E2·마감 임박 US-F2·감사 조회 G1-3·내 계정 EPIC H·chat BFF·SSE 쿼리 토큰·email 로그인+`proten1!` 확정·화면 3종 — 공용 결정 기록 2026-08-02 참조)
- 미해결: 권한 모델 재기술 결정(12장 첫 항목), 시드 적재 잔여(월별 M/M·유지보수 데모)
- 다음 작업: PRD-pms 리뷰·권한 모델 결정 → 게이트 P → PMS-M0 스캐폴드
