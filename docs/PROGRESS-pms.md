# 진행 상태 — pms 트랙 (PMS 담당: 도메인·애플리케이션 서비스·프론트)

> 공용 상태·결정 기록·미해결 이슈는 `PROGRESS.md`. 이 파일은 pms 트랙의
> 다음 작업과 세션 로그만 담는다.

## 온보딩 (처음 시작할 때)

1. 루트 `CLAUDE.md` → `pms/CLAUDE.md` → `docs/PROGRESS.md` → `docs/ROADMAP.md` 순으로 읽기
2. `pms/` 안에서도 `/mcp` 어댑터 모듈은 MCP 담당 소유 — 애플리케이션 서비스 API 변경은 공용 결정 기록 경유

## 현재 상태 (2026-08-03)

- **다음 작업:** 권한 모델 확정분에 대한 **MCP 담당 확인**(공용 문서 변경) → 게이트 P 승인 → PMS-M0 스캐폴드. 시드 적재 잔여(월별 M/M·유지보수 데모)는 PMS-M1 전 결정 (PRD-pms 잔여 리뷰는 v2.1로 완료)
- **차단 요소:** 없음 (게이트 P 대기)

## 세션 로그

### 형식 (복사해서 사용)

```
### YYYY-MM-DD — <작업 요약>
- 완료: <한 것 + 검증 결과>
- 미해결: <다음 세션으로 넘기는 것>
- 다음 작업: <구체적으로>
```

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
