# ROADMAP

> 게이트 없이 다음 단계로 넘어가지 않는다. 게이트 통과는 **사람이 승인**하고 PROGRESS 결정 기록에 남긴다.

## P단계 — 기획 (완료 — 게이트 P 통과 2026-08-09)

> 아래 항목은 **P단계 당시의 산출 기록**이므로 버전 표기도 그때 값이다. 산출물의
> **현행 버전**(2026-08-23 실측): 상위 PRD v1.0 · PRD-host **v2.6** · PRD-pms **v2.13** ·
> 기술_선택_근거 v2.0 · 구현 노트 **v1.1** · 유저 시나리오 **v1.5** · eval-cases **v1.7**
> (+ `docs/evals/seed-anchor-map.md` v1.0 — 2026-08-23 신설, eval 인물·수치 앵커 정본).

- [x] **PRD-host v2.0** (2026-08-02 작성 → `docs/PRD-host.md` + 공유 정의는 상위 `docs/PRD.md`로 승격, 게이트 P 통과 2026-08-09) — 구 PRD(v1.2) 기반 재작성: 허점 6종 수정(측정 불가 기준·요구 충돌·version 반환 구멍·용어 정의 누락·열린 질문 처리·시효 정보), 2026-07-31 결정 반영(`scope=ME`·version 반환·오류/점검 FR·기준 모델·get_project 조건 명시). 시스템 프롬프트 전문·마일스톤 표·비용 모델은 PRD에서 분리(소유권: 프롬프트→구현 노트, 마일스톤→이 문서, 비용→기술 근거)
- [x] **PRD-pms 현행화 이관 (pms 트랙)** (2026-08-02 작성 → `docs/PRD-pms.md`, 게이트 P 통과 2026-08-09 · 분리 결정은 담당자 합의 완료) — 구 "PMS — AI 구현용 PRD" v1.0(2026-06-21, 전사본 `reference/PMS_구현용_PRD_v1.0.md`)을 `docs/PRD-pms.md`로 이관하며 충돌 7건 수정: ①스택 Boot 4.1·Modulith 2.1 ②전제 프로텐 전사 44명·2인 개발(Out of Scope의 "MS본부 2차" 무효) ③부록 B 시드 → `reference/seed/` 44명·382프로젝트 기준 재작성(가동률 검증 케이스 실제 인물로 재지정) ④MCP를 2차가 아닌 M0 `/mcp`로(모듈 목록 6고정 해제 — M0 스캐폴드에서 확정) ⑤마일스톤 라벨 M0~M6 → **PMS-M0~M6** 개명(본 ROADMAP의 M-1~M3와 충돌 방지) ⑥알림 30초 폴링 → SSE(구 2026-07-13 결정) ⑦조직 가시성에 ADMIN(대표)=전사 추가(시드 orgRole 정합 — 상위 `docs/PRD.md` §4로 승격, 게이트 P에서 최종 승인)
- [x] **기술_선택_근거 v2** (2026-08-03 작성 → `docs/기술_선택_근거.md`, 게이트 P 통과 2026-08-09) — 버전 명기(Boot 4.1·Modulith 2.1·Spring AI 2.0.0, 근거 §3-1 신설), MCP 절 stateless 갱신(§11-6 신설), 1인 전제 → 2인·전사 44명 갱신(2차 확장 전제 삭제), "어떻게" 서술(SSE 구현·낙관적 락 절차·인증 체인)은 구현 노트로 이동 표기, 비용 모델 §13 신설(PRD-host FR-AI-06에서 소유권 이관), 구 NFR 번호 참조 제거
- [x] **구현 노트** (2026-08-03 작성 → `docs/구현_노트.md`, 게이트 P 통과 2026-08-09) — 구 가이드의 고유 가치만 이관(~40%): 인증 체인(audience validator·PAT를 JWT로·BFF 위임 토큰), 예외→도구 에러 매핑(404 은닉·409), `spring.ai.mcp.client.enabled: false`의 이유, 부록 A(ProLLMOps 판단)·부록 B(목업 전략, seed JSON 기준으로 갱신). 추가 수용: 시스템 프롬프트 전문 v0.1(PRD-host §5 위임)·SSE 인프라 공통분(기술_선택_근거 §9 위임). 구 가이드의 제거된 API(`customizeRequest`)는 현행(`httpRequestCustomizer`)으로 교체 기재
- [x] **유저 시나리오 생성** (2026-08-03 작성 → `docs/유저_시나리오.md`, 게이트 P 통과 2026-08-09) — 시드 실존 인물 페르소나 8명(합집합 키스톤: PM인데 orgRole=MEMBER인 전세아 포함) × 시나리오 25종(SC-01~25): 조회 9·쓰기/권한 7·경계 응답 4·**실패/오남용 5**(쓰기 대상 모호·확인 카드 취소·범위 밖 값/완료 전이 미고지·도구 결과 인젝션(원칙 6)·자격증명 입력), eval 30케이스 분류 커버리지 표(§9) 포함. **PMS 웹 여정 6종(WS-01~06, §10 — pms 트랙 재량)** 추가: 생애주기 한 줄기(명화공업 앵커)·역할 교체·관리자/감사·알림·웹 권한 경계("챗=화면" 검증)·409 — PMS-M1~M4 데모·수용 경로, AC 참조만(재정의 없음). 가동률·유지보수 기대값은 시드 공백(월별 M/M·유지보수 데모)으로 유예(§8). 공백 3건 발견(§7 — 전사/타부문 가동률 scope 부재 · `list_maintenance_logs` id 의미 미정의 · 완료 프로젝트 진척률 수정 AC 부재) → M-1 실험·pms 확인 항목
- [x] **eval-cases 이관** (2026-08-03 작성 → `docs/evals/eval-cases.md`, 게이트 P 통과 2026-08-09) — 구 30케이스의 분류·비중을 감축 없이 유지하고 실패·오남용 6케이스 확장 = **36케이스**(쓰기 확인 3→8 · 오류/점검(FR-AI-26) 1 신설 — 유저_시나리오 §9가 이관 시로 위임한 비중 결정, PROGRESS 결정 기록). 화자를 시드 실존 인물로 고정(가시성별 채점 — 구 양시온 등 비실존 인물 폐기), 용어·모델 정합화(본부→부문 · 합집합 판정 · `scope=ME` · `whoami` 케이스 신설), 기준일 2026-08 고정. 입력 문구·기대값은 M-1 목업 후 확정
- [x] **화면 프로토타입 (pms 트랙 재량 — 게이트 P 리뷰 보조)** (2026-08-09 작성 → `prototype/`) — "기획한 기능이 맞는지" 눈으로 확인하기 위해 PRD-pms v2.4 부록 A 화면 전체를 백엔드 없이 목업 데이터(시드 44명·382건 그대로)로 재현: phase 탭·진척률 2단계·완료/재개·권한 커스텀 패널·이력 탭·유지보수 3층(계약/사이트/이슈)·가동률 billable 집계·404 은닉/403/409/422 의미론. 사용자 전환으로 44명 권한·가시성 검증 가능. 기존 `frontend/`는 참고용 보존(예전 기획 반영본) — M1 재연동 기반은 그때 결정. **명세 아님** — 충돌 시 PRD가 원본. M-1 목업(MCP 서버)과는 별개·병행
- [x] **게이트 P**: 사람이 문서 5종(상위 PRD·PRD-host·PRD-pms·기술_선택_근거·구현 노트) + 유저 시나리오 + eval 분류 승인 — **2026-08-09 통과**(사용자 승인, PROGRESS 결정 기록)

## M-1 — 사전 검증 (목업)

- [x] 목업 MCP 서버 (구현 노트 목업 전략, stateless 지향으로 검증) — **스캐폴드 + B2-0 배관 검증 완료**(2026-08-10, `pms-mcp-mock/` — 도구 7종·테스트 32개·오염 레코드 기심음). **B2-1 자연어 검증 완료**(2026-08-10 동석 실험 + 전 라운드 재실행 — 조회·권한·경계·인젝션 통과, **실패 2건(D-06·A-06 — 확인 규칙 계열)** → 프롬프트 §4·description 보강 근거, `docs/evals/B2-1-자연어검증.md`). **B2-2 JWT 권한 흐름 완료**(2026-08-13 — caller-id 프로퍼티 → HS256 토큰 `sub`, §1-1 체인 예행. 인증 3케이스·화자별 가시성·404 은닉 E2E 테스트 7종). **B2-3 자체 호스트 연결 완료**(2026-08-13 — `host/` 앱 스캐폴드 + 챗~호스트~목업 MCP 실 LLM 관통 3케이스: scope=ME 가동률·후속 턴 메모리+과부하 원인·404 은닉 정본 문구)
- [x] `get_project` 분리 여부 결정 (2026-07-31 유예) — **분리 불요 확정**(2026-08-12 결정 기록. B2-1 근거: 2단 도약 자연 완주 — §5)
- [x] `search_projects` 상세 응답 `myRole` 포함 여부 결정 (2026-08-03 유예) — **불포함 확정**(2026-08-12 결정 기록. B2-1 근거: 역할 확인 헤맴 0 — §5)
- [x] 카탈로그 공백 2건 해소 — `get_utilization` 전사/타부문 scope · `list_maintenance_logs` **id 확보 경로** — **B2-1 공백 실증 완료**(모델이 `scope=COMPANY` 지어냄 · id 미도달 — §5). **확장안 구체화·pms 측 결정 완료**(2026-08-11 결정 기록 2행 — ③ scope에 `COMPANY` 추가 + 응답 team·division, ④ `search_maintenance` 신설로 7종→8종). **MCP 담당 확인 완료 — 양측 합의 성립 + host 반영 완료**(2026-08-12 — 목업 8종화·테스트 39개·PRD-host v2.4·시스템 프롬프트 v0.2 확정·eval-cases v1.5)
- [x] eval 입력·기대값 확정 — **2026-08-12~13 완료**(eval-cases **v1.6**): B2-1 §5 승격 + 시드 시뮬레이션으로 36케이스 전량 기입(산출 전제 3건 명문화 — 가동률 모집단=진행중 배정·반올림 ±1%p·이슈→계약 링크 사이트명 기준(~~pms 확인 대기~~ → **2026-08-14 확인 완료 — 사이트명 포함 확정**, 결정 기록)). **C류 시드 재앵커**(한국수출입은행·롯데관광 계약이 시드에 부재 → 한국거래소 101). **F-01 `[]` 쟁점 해소 = 유보형 기대값 채택**(R2-5 모범 기준). **재실험 전량 통과**(2026-08-13 B2-1 §6 — `search_maintenance` 흐름·사이트명 키·COMPANY 한 방·404 은닉)
- [x] **게이트 M-1**: 핵심 시나리오(가동률·오버부킹·쓰기 확인 카드) 목업 통과 — **2026-08-17 통과**(사용자 승인, PROGRESS 결정 기록. 실측 근거: B2-1 §5·§6 + B2-3 실 LLM 관통 3케이스)

## M0 — 뼈대

- [x] PMS 백엔드 스캐폴드 (Modulith 모듈 경계 + 경계 테스트) — **2026-08-17 완료**(`pms/` Gradle 프로젝트 — Boot 4.1·Modulith 2.1.0·Java 25, 모듈 6종 확정(결정 기록)·경계 테스트 2종+도메인 순수성 ArchUnit+컨텍스트 스모크, PG(compose)/H2(테스트). **이후 무효화**: 2026-08-21 재구축이 이 스캐폴드를 대체했다 — 현행 모듈 6종은 이때의 6종과 다른 집합(person·auth·project·resource·notification·audit)이고, 테스트 DB는 H2를 버리고 Testcontainers PG 전용이며, "도메인 순수성 ArchUnit"은 3계층 전환으로 `LayerRuleTest`가 됐다(PRD-pms §0·§3))
- [x] `/mcp` 엔드포인트 + 인증 체인 (토큰 패스스루·audience) — **2026-08-18 완료**(host 트랙 — 목업 `mcp/`·`port/` 승격 = `pms/`의 7번째 모듈 `mcp`(루트=port 계약·internal=도구 8종+보안 체인), audience 검증·JWKS 우선/HS256 폴백(**전환 유예 — 결정 기록**, 트리거=실 발급 체계 등장), 임시 시드 어댑터(인력 44명 실데이터·나머지 4포트 FR-AI-26 503), 게이트 인증 3케이스 E2E 예행 포함 테스트 16개)
- [x] 시드 데이터 적재 (`reference/seed/`) — **identity분 2026-08-18 완료**(pms 트랙: `PersonSeedLoader`(구 `IdentitySeedLoader` — 2026-08-21 재구축에서 `person/seed/`로 이동·개명) — 인력 44+시스템 계정·조직 트리 18노드·직급 9·기본 그룹 4종·User 45, 기동 시 빈 DB 자동 적재·멱등·시드 id 정합 보증). projects 382건·maintenance 시드는 **아직 미적재** — 해당 EPIC(A·C·D) 구현 시 각 모듈이 적재한다(PRD-pms §10)
- [x] **LLM 학습 미사용 조항 확인 (사람 작업 — 게이트 전제)** — **2026-08-20 확인 완료**(사용자 — 게이트 M0 판정과 함께 접수)
- [x] **게이트 M0**: 인증 3케이스 (본인/타인/무토큰) — **2026-08-20 통과**(사용자 승인, PROGRESS 결정 기록. 실서버 실측: 무토큰 401·타 audience 401·정상 토큰 whoami=그 사용자 화자 2명 교차. 부수: JWKS 전환 스위치 활성화 — 전환 스모크 3종 실측)

## M1 — 조회 전용 → M2 — 안전한 쓰기 → M3 — 프롬프트·외부

- 큰 줄기: 조회 6도구 + whoami → eval 게이트(**G1**) → `update_progress` 2단계 쓰기 → 운영 검증(**G2**) → 프롬프트·확장(**G3**: 모델·단가·학습 조항 최종 확인) (조회 6도구 = 2026-08-11 결정 ④ 8종화 반영)
- **G1 선행은 양쪽 다 끝났다 — 남은 것은 어댑터 배선 하나다**(2026-08-24 재갱신): 도구 8종 중 **6종 실연결**(person 2 + maintenance 2 + project 2). 남은 2종(가동률)은 **EPIC C 실구현**(2026-08-23)과 **`resource` 루트 계약 승격**(2026-08-24)이 모두 끝나 `UtilizationTools`가 부를 면이 서 있다. host 몫이던 **eval 코퍼스 시드 재앵커**도 완료됐다.

### M1 — pms 트랙 (owner: PMS 담당)

세부 상태는 `docs/PRD-pms.md` §10(EPIC 기준 상태표)이 원장이고, 여기에는 **G1까지의 순서**만 둔다.

- [x] **project·person 루트에 가동률용 계약 신설** — **2026-08-23 완료**. `project.AssignmentDirectoryService`·`MonthlyAssignment`(그 달과 겹치는 배정을 행 단위로) + `person.WorkforceDirectoryService`·`WorkforceProfile`(capacity·billable·gradeCoeff·team·division·subtree 인원). 착수 전 가정("분자만 없다")이 실측에서 깨졌다 — 분모·모집단·계수·조직 표시까지 전부 경로가 없어 계약이 둘이 됐다. 부수로 AC B2-1 종료일 당김(`close()`가 종료 시점을 남기지 않아 "종료월 이후 제외"가 표현 불가였다). **MCP 담당 확인 요청 중**: 시그니처가 `UtilizationEntry`·`OverbookedEntry.Cause`를 채우기에 충분한가 · port 의존 방향(`project → mcp` 구현 vs `mcp → project` 호출 — 두 기록이 갈린다)
- [x] **감사 조회 2뷰** (G1-3 `/api/audit` · G2-2 `/projects/{id}/audit`) — **2026-08-23 완료**. 판정은 골격 단계에서 이미 실구현이었어서 파생 질의·스냅샷 역직렬화만 남아 있었다. 정렬은 호출자가 뒤집을 수 없게 저장소 메서드 이름이 정한다. 부수: 통합 목록의 최신순 인덱스가 없어 Flyway **V8** 신설(V3의 둘은 선행 컬럼이 달라 쓰이지 못했다)
- [x] **projects 시드 적재** — **2026-08-23 완료**. `ProjectSeedLoader`가 382건을 §5 상태 전이를 실제로 밟아 적재(배정 포함), 부록 B의 M/M 부여 규칙으로 2026-08 오버부킹을 재현. **부수 발견**: 인원 정본이 `seed_org_proten.sql`(실제 명부)인데 문서는 구 익명 명부(`people.json`) 이름을 194곳에서 참조하고 있었다 — eval 채점이 이름 대조를 포함하므로 **G1 전 재매핑 필요**(PRD-pms §12 등재)
- [x] **도메인 루트 계약 승격 (안 ② 도메인 몫)** — **2026-08-23 완료**. `maintenance.MaintenanceLookupService` · `project.ProjectLookupService` · `project.ProgressCommandService` + `WorkforceProfile` 조직 id 2종(MCP 요청) + `PersonRef.division`. 승격 전에는 `PersonTools`만 실연결이고 나머지 4도구가 503이었다. **남은 것은 `UtilizationTools`뿐**이고 그건 EPIC C 구현에 묶인다. 부수: 유지보수 계약·이슈 id를 시드 원본 번호로(eval 앵커 101이 우연에 기대던 것·ISSUE 갈래가 죽어 있던 것을 함께 해소)
- [x] **EPIC C 가동률 실구현** (C1-1~C1-6) — **2026-08-23 완료**. `UtilizationQueryServiceImpl` 본문 구현 + 모집단 판정을 `UtilizationPopulation`으로 분리(`ProjectVisibilityService` 선례 — 규칙이 개인 지정/집계 · 전사/제한 두 축으로 갈려 산식과 한 클래스에 두면 읽는 사람이 매번 되짚는다). **착수 전 오산 정정 2건**: ①"C1-4는 이벤트 재계산"으로 봤으나 골격 javadoc이 이미 답을 적어 뒀다 — 조회 시점 계산이라 구현할 것이 없고 **증명(통합 테스트)만** 남아 있었다 ②실측한 계약 공백 — 전사 scope 가시성은 `unrestricted`라 `visiblePersonIds`가 **빈 집합**이어서(제약 없음 ≠ 아무도 없음) 집계 호출자가 명단을 얻을 경로가 없었다 → `WorkforceDirectoryService.findAllAggregatablePersonIds()` 신설(공개 API 변경 — 결정 기록). 부수: 시드 테스트가 **산식을 자체 구현**하고 있어(정본 두 벌) 실서비스 호출로 교체 · M/M 합의 부동소수점 노이즈가 `기본 > 100` 판정을 뒤집을 수 있어 6자리 반올림. 테스트 347 → **368**
- [x] **EPIC D 유지보수 — 조회분(D-a)** — **2026-08-23 완료**. maintenance 모듈 신설(모듈 6 → 7종) · 엔티티 4종 + Flyway **V9** · 시드 적재(계약 105·사이트 157·이슈 14) · D4-1 목록(keyword 3종 매칭)·D4-2 상세·D4-3 전사 공개 · D3-4 이슈 조회(미배정 필터). **eval C류 앵커 2종을 통합 테스트로 고정** — 계약 101 이슈 7건 · "가천대길병원"이 사이트명으로만 45사이트 계약에 도달. 시드↔모델 괴리 결정 7건은 공용 결정 기록 참조
- [x] **`resource` 루트 계약 승격** (`get_utilization`·`list_overbooked`가 붙을 면) — **2026-08-24 완료**. **소유자를 먼저 정했다**(공용 결정 기록 — git-workflow §3 "One promotion, one owner"를 처음 실제로 밟았다: 루트 파일은 PMS 담당, 어댑터 배선은 MCP 담당). 루트 4종 신설 — `UtilizationLookupService`·`UtilizationScope`(ME·MY_TEAM·DIVISION·COMPANY·PERSON)·`UtilizationBrief`·`OverbookedBrief`(+`Cause`). **단순 이동이 아니었다 — 공백 2건이 실측으로 드러났다**: ①웹은 `?orgUnitId=`를 호출자가 주지만 챗은 scope 낱말만 오므로 "MY_TEAM이 누구인가"를 푸는 쪽이 없었다 → resource가 든다(어댑터에 두면 웹·챗의 답이 갈릴 수 있다) ②`list_overbooked`가 요구하는 `Cause(projectName, mm)`를 낼 자리가 없었다 — `UtilizationView`가 프로젝트별 행을 버린다 → **산식·모집단·`기본>100` 판정을 `UtilizationCalculator`로 뽑아** 웹은 causes를 버리고 MCP는 싣는다(정본 한 벌). 부수: `UtilizationView.overbooked()` 제거(같은 규칙이 두 곳에 있었다). 테스트 368 → **409**
- [ ] **EPIC D 쓰기(D-b)** — D2 계약·사이트 등록/수정 · D3-1~D3-3 이슈 등록·처리·append-only 코멘트
- [ ] **EPIC D 이관(D-c)** — D1 `POST /projects/{id}/handover`. **선행 2건**: §5에 완료→유지보수중 전이가 없어(`ProjectStatus.next()`에서 COMPLETED는 empty) `Project`에 전용 메서드 신설 필요 · 라우트는 project 경로인데 계약 생성은 maintenance 소관이고 한 트랜잭션이어야 해(D1-2 원자성) **모듈 방향 결정**이 필요하다. 시연 앵커 = 명화공업(부록 B)
- [ ] **EPIC E 쓰기 5종 골격 채우기** (E1-1·E2-2·E3-2·E4·E5) — 웹 제품 완성도. G1에는 무관
- [ ] **project 잔여 AC** — A6-3(역할 지정) → A8(권한 커스텀. A8-5·A8-6이 진척률·배정 판정에 침투) · `?phase=` 목록 필터
- [ ] **EPIC F 알림 + SSE + 스케줄러** (F1~F3) — H1-4 notif-prefs가 F1-5의 선행
- [ ] **EPIC H 내 계정 잔여** (`/api/me/account` · H1-2·H1-3·H1-4)

### M1 — host 트랙 (owner: MCP 담당)

도구 실연결의 순서는 pms 진도에 종속된다(위 pms 절). 아래는 **어댑터 쪽에서 해야 하는 일**만 둔다.

- [x] **`/mcp` 어댑터 재승격 (`pms-old/` → `pms/`)** — **2026-08-23 완료**. 승격 방식을 재구축 구조에 맞춰 바꿨다(공용 결정 기록): 도구 8종·응답 DTO·예외 매핑은 `mcp` 모듈이 소유하고, 각 도메인은 **자기 모듈 루트에 조회 계약을 올려** 어댑터가 그것만 부른다 — 의존이 `mcp → 도메인` 한 방향이라 `mcp/`를 지워도 pms는 그대로 돈다. 디코더는 새로 만들지 않고 auth의 `accessTokenDecoder`를 받는다(audience=pms·token_type=access가 이미 부착 — 빈을 하나 더 만들면 타입 주입이 모호해져 웹 인증이 깨진다). `pms.auth.enabled`와 무관하게 `/mcp`는 항상 토큰을 요구한다(원칙 4). 테스트 11건(인증 3케이스·체인 순서·위조 서명·refresh·카탈로그 8종·가시성·503)
- [x] **person 포트 실연결** — **2026-08-23 완료**. `person/PersonLookupService`(+`PersonIdentity`) 루트 승격 → `whoami`·`find_person`이 실 DB 응답. 가시성 판정은 `PersonService`를 그대로 불러 **챗과 화면이 같은 답**을 내게 했고, `whoami`의 부문은 person 안에서 조직 트리를 올라가 채운다(`MeView`에 없는 값). 권한 플래그는 담지 않는다(FR-AI-16)
- [x] **project 포트 실연결** (`search_projects`·`update_progress`) — **2026-08-23 완료**. 도메인 계약은 PR #25가 올렸고(`ProjectLookupService`·`ProgressCommandService`) 이 항목은 **어댑터 배선**이다. **쓰기 도구가 처음 실연결됐다**: 2단계 확인·권한·낙관적 락·완료 규칙은 내부 유스케이스가 그대로 갖고 어댑터는 `confirmed` 왕복과 표현 변환만 한다(구조 원칙 5) — 프로토콜을 두 곳에 두면 한쪽이 확인을 건너뛰는 길이 생긴다. `summary` 한 줄만 어댑터가 만든다(도메인에 없는 필드 · 커밋 뒤에는 현재값=요청값이 되므로 두 단계의 문장이 갈린다). **404 은닉은 maintenance와 반대로 도메인이 든다** — 프로젝트는 가시성 밖이 부재와 같이 숨어야 하고(A3-2) 유지보수는 숨길 것이 없다(D4-3). 카탈로그 문구 3건 정정(절단 50건 명시·keyword 부분 일치·status 5종 — 결정 기록, PRD-host v2.6). 테스트 13건: 가시성 화자별 갈림 · 절단 50 · 상태 필터 19건 · 422 · 부분 일치(토큰 AND 아님) · 상세 version · 404 은닉 · 2단계 확인 양단 · 409 낙관적 락 · 403 비담당자
- [ ] **resource 포트 실연결** (`get_utilization`·`list_overbooked`) — **선행이 전부 끝났다. 남은 것은 어댑터 배선뿐이다**. EPIC C 실구현 2026-08-23 · **루트 계약 승격 2026-08-24**(`resource.UtilizationLookupService`·`UtilizationScope`·`UtilizationBrief`·`OverbookedBrief.Cause` — 소유자 결정은 결정 기록). 어댑터가 할 일: `UtilizationTools`의 `ToolError.unavailable` 2건 제거 · `scope` 문자열 → `UtilizationScope.from()`(모르는 낱말은 도메인이 400으로 거절한다) · `month` 문자열 ↔ `YearMonth` · `Brief` → `UtilizationEntry`·`OverbookedEntry` 변환. ~~조직 id 후속 1건~~ — 해소됐다(`WorkforceProfile`의 조직 id 2종으로 `MY_TEAM`·`DIVISION`을 **도메인이** 유도한다)
- [x] **maintenance 포트 실연결** (`search_maintenance`·`list_maintenance_logs`) — **2026-08-23 완료**. 도메인 계약은 PR #25가 올렸고(`maintenance.MaintenanceLookupService`) 이 항목은 **어댑터 배선**이다: 503 제거 · 절단 50건과 404 문구를 어댑터가 든다(도구 description·`ToolError`가 이 모듈 소관) · `contractId`를 nullable로 정정(계약 미연결 이슈를 0으로 내보내던 자리). **화자를 넘기지 않는 유일한 도구 묶음**이다(전사 공개 AC D4-3). eval C류 앵커를 **도구 관통으로** 고정: 사이트명으로만 45사이트 계약 도달 · `search_maintenance`가 준 계약 id로 이슈 7건 · **그 이슈 id로 ISSUE 갈래 도달**(eval C-04 전제 — #25의 시드 원본 id 재부여가 열었다)
- [x] **eval 코퍼스 시드 재앵커** — **2026-08-23 완료**. 캐스팅은 **id 보존 + 역할 라벨 정정 + 화자 교체 4건**으로 갔다 — `projects.json`·`maintenance.json`이 인물을 id로만 참조하므로 id를 지키면 프로젝트·배정·수치 앵커가 그대로 살고(실측 대조 7종 동일), 깨지는 축은 권한 그룹·직급계수·`billable` 셋뿐이다. 착수 전 전제("이름은 기계적 치환")는 실측에서 깨졌다 — 구 과부하 3인 중 하나가 실 명부에서 `billable=false`라 모집단에서 빠지므로 수치는 전량 재산출이었다. 산출: eval-cases **v1.7** · 유저_시나리오 **v1.5** · **앵커 정본 `docs/evals/seed-anchor-map.md` + 재현 SQL** 신설. 부수로 미해결 2건(팀원 scope SELF→TEAM · A2-9 진행중 제한)의 host 파급 확인 완료. **시드 구조상 A-02의 부문 경계 판별력이 사라진 것**은 손실로 명기하고 확장 후보에 등재(사용자 승인)
- [ ] **host 앱 실서버 전환** — `pms.mcp.base-url` 8090(목업) → 8080 + 로그인 access 토큰으로 관통 실측. 목업은 카탈로그 실험장으로 존치
- [x] **감사 `source=MCP` 실측** — **2026-08-23 완료**. 어댑터에 배선할 것이 없다는 판단이 맞았고(`AuditSourceResolver`가 `/mcp` 경로 접두사로 판정), 쓰기 도구가 실연결된 그 세션에 관통으로 확인했다: `confirmed=true` 직후 그 프로젝트의 최신 감사 행이 `source=MCP`다(기동 시 시드 적재분은 `WEB`이라 구분된다). 쓰기 도구 없이는 실측할 방법이 없던 항목이라 배선과 같은 세션에서 닫혔다
- [ ] **eval 자동 실행 스크립트 + 오류 주입 장치** — 오염 레코드·오류 주입(하네스 증설 예약 항목, G1 준비 시)
- [ ] **게이트 G1**: 36케이스 — 치명(F1~F4) 0건 · 합격률 ≥ 90%(33/36) · 사람 승인(결정 기록)


## 하네스 증설 예약 (해당 고통이 발생하면 그때 추가)

- **ralph 루프**: 계획 합의된 기계적 작업 큐가 생기면
- ~~**conventions 분리**~~: 2026-08-02 완료 — 구 conventions 3종을 `docs/conventions/`로 이관(v3 정합화), 스코프 CLAUDE.md에서 참조
- ~~**CI + 원격 저장소**~~: 2026-08-02 완료 — 원격(origin) 연결 + 최소 CI(`.github/workflows/ci.yml`, verify.sh 실행·실패 시 로그 아티팩트). Gradle 캐시는 M0, `npm ci`는 M1에 추가. 경량 2인 워크플로 — 구 프로젝트의 과한 ruleset 반복 금지
- **eval 자동 실행 스크립트**: M1 G1 게이트 준비 시
