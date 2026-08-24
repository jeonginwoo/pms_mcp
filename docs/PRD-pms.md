# PMS — 구현용 PRD (PRD-pms)

| 항목 | 내용 |
|------|------|
| 문서 | PMS 본체 구현명세 (코딩 에이전트용) · **소유: pms 트랙** |
| 버전 / 상태 | v2.15 · **확정** (게이트 P 통과 2026-08-09. v2.6 = 가동률 의미 재정의 + 시드 정책 완결(2026-08-10 — MCP 담당 확인 완료). v2.7 = M-1 카탈로그 공백 2건 해소의 PMS 측 반영(2026-08-11 결정 기록, 2026-08-12 MCP 담당 확인 완료 — 양측 합의 성립·host 반영 완료). 이슈→계약 링크 기준 = 사이트명 포함 확정(2026-08-14 — 부록 B·결정 기록). v2.8 = 재구축·골격 확장 이후의 문서↔코드 정합(2026-08-23 — 결정 기록). v2.9 = 모듈 간 계약 2종 신설(가동률 분자·분모) + B2-1 종료일 규칙(2026-08-23 — MCP 담당 확인 요청). v2.10 = 프로젝트 시드 적재 + 인원 정본 확정(`seed_org_proten.sql`). v2.11 = 감사 조회 2뷰 실구현(G1-3·G2-2). v2.12 = maintenance 모듈 신설(EPIC D 조회분) + 시드↔모델 괴리 결정 7건. v2.13 = 도메인 루트 계약 3종 승격(안 ② 이행) + 조직 id·시드 원본 id(2026-08-23 결정 기록). v2.14 = `resource` 루트 계약 승격 + 승격 소유자 결정 선행(2026-08-24). **v2.15 = 부록 B 구 익명 이름 3곳 재앵커 + 이슈 작성자 교정(Flyway V10) — 2026-08-24**) |
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

### v2.2 반영 (2026-08-06 — 12장 일괄 해소 + 완료 전이 재설계, PROGRESS 결정 기록)

- **완료 전이 재설계**: 진행률 100% 자동 전이 폐지 → 명시적 완료 처리·재개(**US-A7 신설** — 배정 전원, 상위 PRD §4-2 표 확장). 완료 상태의 진척률 직접 수정은 `409 PROJECT_COMPLETED`(A2-8). 완료 지연 D+7 리마인드(**US-F3 신설**, 재개 시 미읽음 알림 회수)
- **PL 복수 허용**: A1-7 삭제 · A6-7의 `MULTIPLE_PL` 삭제 · 에러 표 정리 (상위 PRD §4-2 확정)
- **가동률 집계 모집단**: `Person.billable` 신설(상위 PRD §3) — 집계 AC C1-5, 적재 규칙은 부록 B
- 12장 잔여 해소: D-N=**7** (F2-1) · JWT **access 1h + refresh 14일**(§7) · `FORBIDDEN_FIELD`→**`FORBIDDEN`** 개명(프로토타입 미사용 확인) · 가동률 캐시 미도입(매 조회 계산) · 설정 화면 편집 탭 승격 안 함(로컬 데모 유지)

### v2.3 반영 (2026-08-06 — 프로젝트별 권한 커스텀 + 프로젝트별 감사 이력 뷰)

- **프로젝트별 권한 커스텀 신설** (PROGRESS 결정 기록 · MCP 담당 확인 완료 2026-08-06): 상위 PRD §4-2 표는 **기본값**이 되고, PM이 프로젝트 설정에서 역할별 기능 토글로 조정한다(전역 권한 관리 탭 없음 — 프로젝트 스코프 UI). **US-A8 신설** · `GET/PUT /api/projects/{id}/permissions` · `ProjectPermissionOverride` 엔티티(기본값과 다른 셀만 저장) · 고정 셀 위반 `422 IMMUTABLE_PERMISSION` · 부록 A 상세 화면에 권한 패널 추가. 조정 범위·고정 셀·완료·재개 묶음 규칙은 상위 PRD §4-2가 원본. 기본값에서 거동 무변경 — 기존 AC·시나리오·eval 영향 없음
- **프로젝트별 감사 이력 뷰 신설** (pms 내부 — MCP 확인 불요): 저장은 AuditLog **단일 원본 유지**(이중 기록 없음 — G1-1·G1-2 불변식 보존), `AuditLog.projectId`(nullable) 참조 컬럼 + `GET /api/projects/{id}/audit`(**가시성 범위 전체** — 참여자 포함) + 부록 A 이력 탭. **US-G2 신설**. 통합로그(`GET /api/audit` — G1-3)는 ADMIN 전용 그대로 — 프로젝트 이력과 통합로그는 같은 행의 권한 다른 두 조회 뷰. 용량은 비쟁점(후한 추정 연 7.3만 행·수십~150MB — 보존 정책은 고통 시 재론)

### v2.4 반영 (2026-08-06 — 유지보수 재설계: 계약/사이트/이슈 3층 + phase 탭)

- **유지보수 도메인 재설계** (PROGRESS 결정 기록 · MCP 담당 확인 완료 2026-08-06): 완료 프로젝트 1:1 파생 모델(`Maintenance`·`MaintenanceLog`) → **계약/사이트/이슈 3층**. 실측 근거 — 유지보수 계약은 연 단위이고 계약:고객사 1:N(가온아이 1계약 ~45사이트), OEM 채널 계약은 원천 프로젝트가 없다. 프로젝트:계약 **1:1**(이관 경로, nullable) + **직접 등록 입구 병존**. **담당 엔지니어 정본 = 사이트 단위**(이슈 기본 배정 원천 — "누가 뭘 담당하는지" 구조화). 이슈는 구 게시판 대체 — type{장애,문의,요청}·상태{접수,처리중,고객확인대기,완료}·append-only 코멘트. EPIC D 재작성(US-D1~D4) · §7 API 재편 · 부록 A 화면 3종 · 부록 B 시드 확정
- **phase 탭**: category 컬럼을 만들지 않고 **status 파생 그룹**(영업={계약대기,수주확정} · 솔루션={진행중,완료})으로 탭 구별 — `GET /api/projects?phase=` + 응답 파생 필드, 서버 단일 정의. 유지보수 탭은 프로젝트가 아닌 계약 목록
- **미채택(안 하기로 하는 결정)**: sales 모듈·SalesInfo 확장 엔티티(영업 탭은 필터 뷰만 — 필요 시 확장 테이블 신설로 무마이그레이션 추가 가능) · 정기점검 모델링(계약 정보 필드만 — "특이사항 없으면 기록 안 함" 실무) · 계약 만료 임박 알림(고통 확인 후 추가)
- 구 미해결 "프로젝트:Maintenance 1:1 vs 1:N" 해소 — `list_maintenance_logs`는 projectId로 단순화 불가(프로젝트 없는 계약 존재), 계약/이슈 id 유지 (MCP 확인 완료 2026-08-06)

### v2.5 반영 (2026-08-09 — 게이트 P 리뷰 기획 결정 8건, PROGRESS 결정 기록. ④⑦ 공용 변경은 리뷰 동석으로 MCP 합의 성립)

- **orgRole → 편집 가능한 권한 그룹 일반화** (⑦ — 규칙 원본은 상위 PRD §4-3): `PermissionGroup` 엔티티(가시성 scope 4단 + 프로젝트 밖 기능 플래그 4종) · **US-E5 신설**(그룹 CRUD — 관리자 그룹 시스템 고정 `422 IMMUTABLE_GROUP`, 소속 인원 있으면 삭제 거절 `409 IN_USE`) · 시드 orgRole 4종 → 기본 그룹 4종 적재(부록 B) · `whoami`(H1-1) 응답 orgRole → 그룹명(host 측 문서 반영은 host 트랙)
- **조직 임의 깊이 트리** (⑧): `Division`·`Team` 2단 → **`OrgUnit` 트리**(회사 root → 부문 → 팀 → 임의 깊이) · **US-E3 신설**(노드 추가·개명·삭제 — 인원·프로젝트·하위 있으면 `409 IN_USE`) · 팀 가시성 = subtree(상위 §4-4)
- **팀·직급 관리 채택** (⑤ — 2026-08-06 "설정 편집 탭 승격 안 함" 대체): **US-E4 신설**(직급 CRUD — coeff 변경 시 보정 가동률 즉시 반영(매 조회 계산이라 자동))
- **시스템 관리자 계정** (④): `admin@proten.co.kr` — 관리자 그룹, 삭제·수정 불가(`422 IMMUTABLE_ACCOUNT` — E2-5), 인력·가동률·배정 목록 제외(`Person.system`)
- **웹 진척률 2단계 완화** (①): 웹 UI는 100% 저장만 확인 모달, 그 외 1클릭(confirmed=true 단건 호출) — **서비스 프로토콜·MCP 무변경**(US-A2 주석·부록 A)
- **engagement 3종 확정** (③⑥): 원격(REMOTE)·상주(ONSITE)·부분상주(PARTIAL_ONSITE) — **OFFSITE 폐지**, 시드 32건은 적재 시 REMOTE로 흡수(부록 B)
- 권한 조정 감사 단위 = 저장 1건 (② — A8-2 현행 정합 확인, 변경 없음)

### v2.6 반영 (2026-08-10 — 가동률 의미 재정의 + 시드 정책 완결, PROGRESS 결정 기록 · MCP 담당 확인 완료 2026-08-10 동석)

- **배정 M/M = 실투입 계획** (상위 PRD §3 재정의가 원본): 계약 배분 숫자가 아님 — 계약 관점은 프로젝트 `contractMm`에만. 근거: 계약 배분≠실투입(체크만 하는 PM 케이스)이면 과부하 경보 오발·여유 인력 오판. B1-5 신설(UI 레이블 "계약 배분 아님")
- **오버부킹·집계 정본 = 기본 가동률**(기본>100 — C1-3) · **보정 가동률 재정의 = Σ(배정MM×coeff)÷가용**(단가 가중 보조 지표, ÷→× — C1-1·C1-2 기본120/보정144) — 구 ÷coeff는 배정 M/M이 단가 기준일 때만 성립
- **PM 역할 가동률 하드 제외 미채택**: PM 자동 배정 기본 M/M=0(A6-7) + 실투입 의미로 충족 — 하드 제외는 실무형 PM(시드: 1인 프로젝트 다수)의 과부하 사각 + 부하 은닉 우회 구멍
- **시드 확정 2건**(부록 B): 월별 M/M 부여 규칙(실무자=PM 외 참여자, 없으면 PM 본인 · contractMm 안분 근사 · 2026-08 오버부킹 3명 시뮬레이션 검증) · billable=false = 프로텐·AX사업기획부·관리•마케팅부 3부문 10명
- **유지보수 시드 변환 완료** (같은 날 후속): 시트 3섹션 → `reference/seed/maintenance.json`(계약 105·사이트 157·이슈 14) + engineerId 매핑 규칙·이관 시연 대상(명화공업) 확정 — 부록 B. **시드 적재 정책 전량 해소**(12장)

### v2.7 반영 (2026-08-11 — M-1 카탈로그 공백 2건 해소의 PMS 측 반영, PROGRESS 결정 기록 · ~~MCP 담당 확인 대기~~ → **2026-08-12 확인 완료 — 양측 합의 성립**)

B2-1 자연어 검증(2026-08-10)이 실증한 도구 카탈로그 공백 2건의 결정 중, **PMS 응답 계약에 해당하는 부분만** 반영한다. MCP 도구 카탈로그·description·시스템 프롬프트·eval은 host 트랙 소유이므로 이 문서는 접점만 갖는다(상위 PRD §6).

- **가동률 응답에 조직 정보 동봉** (③ — C1-6 신설 · §7): `GET /api/utilization` 응답 항목에 **`team`·`division` 추가**. MCP `get_utilization` 응답도 동일(scope 열거에 `COMPANY` 추가는 host 소유). 근거: 전사 집계 결과를 "부문별로" 정리하려면 소속이 필요한데 현 응답에 없어 인원 수만큼 개인 조회를 반복해야 했다(B2-1 R3-1 실측)
- **유지보수 계약 keyword 검색** (④ — D4-1 확장 · §7): `GET /api/maintenance/contracts`에 **`keyword=` 추가 — 계약명·계약사·사이트명(고객사) 부분 일치**. 근거: 가온아이 1계약에 고객사 사이트 45개(시드 실측)인데 사용자는 계약명이 아니라 고객사명으로 부른다. 현 필터(status·계약사·종료일)로는 사이트명에 도달할 수 없어 웹·챗이 같은 한계를 공유했다. MCP 측은 `search_maintenance` 신설로 대응(카탈로그 7종→8종 — host 소유)
- **미채택**: 프로젝트 상세에 계약 id 동봉(ⓑ안) — 시드 계약 105건 전부 `sourceProjectId=null`(시트 실데이터라 이관 이력 없음)이라 현시점 커버 0건

### v2.8 반영 (2026-08-23 — 재구축·골격 확장 이후의 문서↔코드 정합, PROGRESS 결정 기록)

2026-08-21 재구축(3계층·도메인별 모듈)과 2026-08-22 골격 확장(auth·audit 분리·계약 통합·응답 봉투) 이후 **문서가 코드를 따라오지 못한 부분**을 실측 대조로 맞춘 개정이다. 새 결정은 §10 라벨 폐기 하나뿐이고 나머지는 전부 사실 정정이다.

- **§0 스택**: 테스트 DB `H2/Testcontainers` → **Testcontainers PostgreSQL 전용**(H2 대체 금지 — `pms/build.gradle`·conventions §8) · 프론트엔드 항목의 "프로토타입(JSX)은 M1 재연동" 전제 소멸(2026-08-22 전면 TS 재작성 완료, 목업은 `prototype/`으로 분리)
- **§0 모듈 간 통신**: `@NamedInterface`로 공개한 `service`·`service/dto` 참조 → **모듈 루트 패키지가 공개 API**(2026-08-22 정렬). 이 조항이 §3과 정면으로 모순인 채 남아 있었다 — `package-info.java`는 0개이고 하위 패키지는 전부 internal이다
- **§1 성공 지표 · §11 DoD**: 가동률 예시 `기본120/`**`보정100`** → **`보정144`**. 2026-08-10 산식 재정의(÷coeff → ×coeff)가 §6 C1-2·부록 B·상위 `PRD.md` §3에는 반영됐는데 이 두 곳만 구 수치로 남아 상위 문서와 어긋나 있었다
- **§11 DoD**: "domain 레이어에 Spring/JPA import 0" → **3계층 방향·영속/웹 관심사 격리(`LayerRuleTest`)**. §0이 2026-08-21에 폐기한 규칙이 DoD에 살아 있었다. 함께 "`501` 던지는 자리 0건" 항목 신설 — 골격이 남아 있으면 그 EPIC은 미완이다
- **§10 구현 순서 → 구현 상태 (라벨 폐기 — 이번 개정의 유일한 결정)**: 구 **PMS-M0~M6 순차 라벨을 폐기**하고 **EPIC 기준 상태표**(실구현 / 골격 / 미착수)로 대체. 근거는 실제 진행이 순서를 벗어난 것 — M6(프론트)이 M3~M5보다 먼저 끝났고, M1의 "person + 인증"은 `auth` 분리로 두 단위가 됐고, M3·M5는 골격만 서 있다. 순서를 지키지 못한 계획은 진행 상황을 숨긴다. 횡단 기반(시드 적재 범위·인증 스위치·`/mcp` 부재)도 같은 표에 실측으로 올렸다
- **§12**: 해소 항목이 가리키던 "구현 시점 = PMS-M1"을 **실제 소관 EPIC**으로 교체(C1-6 → EPIC C · D4-1·유지보수 시드 → EPIC D)

### v2.9 반영 (2026-08-23 — 모듈 간 계약 2종 신설 + B2-1 종료일 규칙, PROGRESS 결정 기록 · **MCP 담당 확인 요청**)

가동률(EPIC C)을 막고 있던 접점을 열었다. 실측해 보니 막힌 곳이 배정 조회 하나가 아니라 **분자·분모·모집단·계수·조직 표시까지 전부**였다 — `PersonRef(id, name, orgUnit, grade)`로는 가동률을 계산할 수 없다.

- **§3 모듈 간 공개 계약 표 신설** — 모듈 루트에 있는 것이 곧 밖으로 나가는 전부이므로(§0) 그 목록을 문서에도 원장으로 둔다. 신설 2종: **`AssignmentDirectoryService`·`MonthlyAssignment`**(project → resource, 가동률 분자를 **행 단위**로) · **`WorkforceDirectoryService`·`WorkforceProfile`**(person → resource, capacity·billable·gradeCoeff·team·division·subtree 인원)
- **§6 B2-1 개정** — 종료 시 **`endDate`를 종료월 말일로 당긴다**. 이전에는 상태만 CLOSED로 바꾸고 종료 시점을 남기지 않아 AC가 약속한 "종료월 이후 제외"를 데이터로 표현할 수 없었다(상태만 보고 거르면 종료한 순간 지난달 집계에서까지 사라지고, 상태를 무시하면 영영 빠지지 않는다)
- **§10 `/mcp` 행 정정** — `/mcp` 승격이 도메인 루트 승격에 걸린다고 적었던 것을 바로잡았다. M0 산출물은 도메인이 `mcp` 루트의 port를 **구현**하는 형태(의존 `project → mcp`)이고, 구조 원칙 3의 "호출"과는 방향이 반대다. **어느 쪽인지는 MCP 담당 결정 사항**으로 양쪽 문서에 미정임을 명시했다

---

## 0. AI 에이전트에게 주는 지시

**고정값 — 임의 변경 금지.** 너는 이 PRD를 구현하는 시니어 풀스택 엔지니어다. 아래 스택·규칙을 고정값으로 받아들이고, 명세되지 않은 선택은 "가장 단순·표준적인 방법"을 택하되 결정을 주석으로 남긴다. 각 기능은 대응하는 수용기준(AC)을 테스트로 구현하며, AC 없는 코드는 작성하지 않는다. 각 마일스톤 끝에서 `bash scripts/verify.sh pms`(내부: gradle test + Modulith 경계 검증)가 통과해야 다음으로 넘어가고, 이전 마일스톤 테스트가 깨지면 새 기능 진행을 멈추고 회귀부터 고친다. 명세 밖 세부 결정은 단순·표준을 택해 `// ASSUMPTION:` 주석(한국어)으로 남기되, 12장 항목은 임의 구현하지 말고 질문한다.

**고정 기술 스택**
- Backend: Java 25 · Spring Boot 4.1 · Spring Modulith 2.1 · JPA
- DB: PostgreSQL(운영) · **Testcontainers PostgreSQL(테스트)** — H2 대체 금지(2026-08-18 도입, conventions §8. 방언 차이 버그를 H2 통과가 가려 준다)
- Frontend: React + TypeScript + Vite (CSR SPA) — `frontend/`는 **2026-08-22 전면 TS 재작성**(구현된 API에만 붙는 실연동 클라이언트, `.js`/`.jsx` 0개). 백엔드 없는 기획 검증 목업은 `prototype/`으로 분리했다 (`docs/conventions/react-ts.md`)
- Build: Gradle · Test: JUnit 5 · AssertJ · Modulith Test
- 배포: Nginx → Spring Boot → PostgreSQL (Docker Compose)

**아키텍처 규칙 (위반=실패)**
- 모듈러 모놀리식 · Modulith 경계 강제 · **도메인 하나 = 모듈 하나** (모듈 목록은 §3)
- 모듈 내부는 **3계층 `controller → service → repository`** 한 방향이며 **JPA 엔티티가 곧 도메인 모델**이다 (2026-08-21 재구축 결정 — 구 `api→application→domain←infra`·"domain은 Spring/JPA import 0" 규칙을 대체. 근거·경위는 PROGRESS 결정 기록). `service` = 유스케이스 인터페이스(모듈 계약), 하위에 `impl`(구현·내부 협력자)·`dto`(입출력)·`entity`(JPA 엔티티·VO). 영속 관심사(`jakarta.persistence`)는 `service/entity`·`repository`에만, 웹 관심사는 `controller`와 common의 에러 봉투 변환에만 둔다 — `LayerRuleTest`(ArchUnit)가 강제한다
- 모듈 간 객체참조 금지, ID로만 연결. **모듈의 공개 API = 모듈 루트 패키지**이므로 다른 모듈은 그 루트에 놓인 타입만 참조한다 (2026-08-22 — Modulith 기본 규약으로 정렬. `package-info.java`·`@NamedInterface`는 쓰지 않는다: 계약을 하위 패키지에 두면 기본값이 닫아 둔 것을 다시 여느라 그것들이 필요해진다). `controller`·`service`·`service/impl`·`service/dto`·`service/entity`·`repository`는 전부 모듈 내부라 엔티티·리포지토리가 경계를 넘지 못한다(`ModularityTest`가 검증). 경계를 넓히려면 **타입을 모듈 루트로 옮기는 눈에 보이는 행위**가 필요하다 — 루트의 파일 목록이 곧 그 모듈의 공개 계약이다
- 단일 DB · **단일 스키마** · 모듈 간 물리 FK 금지 (2026-08-21 재구축 결정 — 구 "모듈별 스키마" 대체. 스키마는 Flyway가 소유: `src/main/resources/db/migration/V__*.sql`, `ddl-auto=none`. 기존 마이그레이션은 수정하지 않고 새 버전을 추가한다)
- 이벤트는 사후 fan-out만, 즉시·원자적은 동기 호출
- 권한은 서버 최종 판정, 프론트는 UI 노출 제어만
- `/mcp` 어댑터는 애플리케이션 서비스만 호출 — 리포지토리 직접 접근 금지 (구조 원칙 3)

## 1. 제품 개요

**한 줄 정의** — 여러 고객사 프로젝트를 인력(M/M)·가동률·유지보수 이력 관점에서 관리하는 사내 도구. 스프레드시트를 대체한다.

**목표 (1차)**: 한 프로젝트가 수주확정 → 진행(배정·가동률) → 완료·검수 → 유지보수 이관 → 이력관리로 끊김 없이 흐르는 한 줄기를 완성. 배정 변경 시 가동률 2초 내 갱신, 오버부킹 자동 감지.

**성공 지표**: 프로텐 전 직원(시드 44명)이 시트 대신 사용 · "한 줄기" 데모 성공 · 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정144) 검증.

**In Scope**: 프로젝트 CRUD · 인력 배정 · 월별합산/직급보정 가동률 · 오버부킹 감지 · **유지보수 계약·사이트·이슈 관리(이관 + 직접 등록 — v2.4)** · 두 축 권한 · 감사로그 · 인앱 알림 · `/mcp` 어댑터 접점(어댑터 자체는 MCP 담당 소유).

**Out of Scope (구현 금지)**: 태스크/칸반(프로젝트 작업 관리 — **유지보수 이슈 관리(EPIC D)는 별개로 In Scope**, v2.4 경계 명시) · sales 모듈(영업 탭은 status 파생 필터 뷰로만 — v2.4) · 파일 업로드 · 메일/Slack 알림 · SSO · 소속 시점이력 · **프로젝트 역할(커스텀 역할) 추가/삭제 — 3단 고정** · MSA. (구 "orgRole 커스텀 추가/편집" 금지는 2026-08-09 **권한 그룹 일반화로 해제** — US-E5. 단 관리자 그룹은 시스템 고정) (구 "프로젝트별 권한예외 · 4역할 세분화" 금지는 2026-08-03 권한 모델 확정으로 해제 — 프로젝트 역할 PM/PL/참여자와 **프로젝트별 권한 커스텀(US-A8, 2026-08-06)**은 In Scope. 커스텀은 고정 3역할의 기능 토글까지만 — 역할 신설은 여전히 금지) (v1.0의 "MS본부(2차)"는 삭제 — 전사 범위 전환으로 시드에 MS사업부 포함)

## 2. 사용자 · 권한 모델

권한 모델(두 축·조직 가시성·기능별 권한 표·404 은닉 의미론)은 **상위 `docs/PRD.md` §4가 유일 원본**이다. 구현 관점 보충만 남긴다:

- 소유 모듈: 조직 가시성 = person(구 identity — 2026-08-21 개명), 프로젝트 역할 = project
- 판정식은 상위 `PRD.md` §4-1이 원본(`canDo = orgPerm OR projectPerm`, 가시성은 §4-4). 서버가 최종 판정(§0 아키텍처 규칙), 프론트는 UI 노출 제어만
- **권한 그룹 (2026-08-09 확정 — 상위 `PRD.md` §4-3이 규칙 원본)**: 구 orgRole 고정 4단을 편집 가능한 `PermissionGroup`(가시성 scope 4단 + 프로젝트 밖 기능 플래그 4종)으로 일반화. 판정·가시성·404 은닉이 전부 그룹 정의를 따른다. 시드 `people.json`의 orgRole 값(ADMIN/DIVISION_HEAD/TEAM_LEAD/MEMBER)은 기본 그룹 4종(관리자·부문장·팀장·팀원)으로 매핑 적재(부록 B)
- **확정(2026-08-03)**: 판정은 **합집합** — `canDo = orgPerm(orgRole) OR projectPerm(프로젝트 역할)`. orgRole을 선행 게이트로 쓰지 않는다. 프로젝트 역할은 **PM / PL / 참여자** 3단이며 프로젝트마다 개별 판정한다(구 "관리자/담당자" 대체). orgRole은 가시성 + 프로젝트 밖 행위(생성·조직 관리)만 담당. 프로토타입 기능 플래그 5종 미채택, 부문장 `editProgress:false` 폐기. 상세 표는 상위 `PRD.md` §4가 유일 원본 — 본 문서는 참조만 한다 (PROGRESS 결정 기록 2026-08-03)
- 가동률 집계 모집단은 `Person.billable`로 판정 — **2026-08-06 확정**(상위 PRD §3이 원본, 구 "HQ 제외 여부" 미결 해소). 적재 시 false 지정 팀 목록은 부록 B
- **프로젝트별 권한 커스텀 (2026-08-06 — MCP 담당 확인 완료)**: 상위 PRD §4-2 표는 기본값, `projectPerm` 판정은 프로젝트별 매트릭스(기본값 + override) 참조. 조정 범위·고정 셀 규칙은 상위 §4-2가 유일 원본 — 본 문서는 구현(US-A8·§4 엔티티·§7 API)만 가진다

## 3. 시스템 구성 (요약)

```
[사용자] --HTTPS--> [Nginx] --> [React SPA] --REST/JWT·SSE--> [PMS Boot 앱 (Modulith 모듈 + /mcp 어댑터)] --JPA--> [PostgreSQL]
                                          [AI 호스트 Boot 앱] --MCP(Streamable HTTP)--> 위 /mcp
```

- Frontend=화면·검증·표시(권한은 UI노출만) / Backend=단일앱 모듈러 모놀리식 / DB=단일PG·단일스키마(Flyway 소유 — §0) / 인증=자체 로그인+JWT(stateless) / 알림=SSE 즉시 푸시(⑥) / 스케줄러=일1회(마감알림)만 / 파일저장소 없음.
- **모듈 목록(2026-08-22 골격 확장에서 갱신 — 공용 결정 기록. 구 2026-08-21 재구축 3종을 대체)**: 현재 **person · auth · project · resource · notification · audit · maintenance 7종**(maintenance는 2026-08-23 신설 — EPIC D 조회분). `audit`는 2026-08-22에 `common`에서 승격했다(쓰임은 횡단이지만 자기 엔티티·저장소·유스케이스를 가진 도메인이다). **`common`은 모듈이 아니다** — 에러 모델·응답 봉투·호출자 식별 같은 공용 배선이라 `ModularityTest`가 Modulith 탐지에서 제외한다. **모듈의 공개 API = 모듈 루트 패키지**(Modulith 기본 규약)이므로 밖으로 나가는 타입만 루트에 두고 `package-info.java`는 쓰지 않는다. `identity`는 계정·인증이 범위에서 빠지며 담는 것이 사람·조직·직급·권한 그룹뿐이 되어 **`person`으로 개명**했고, 2026-08-22에 그 계정·인증이 실제로 **`auth`** 모듈로 나갔다(의존은 auth → person 한 방향, 반대 방향은 person이 모듈 루트에 정의한 `AccountPort`를 auth가 구현 — 직접 상호 호출은 모듈 순환이라 `ModularityTest`가 막는다). `resource`·`notification`은 **로직보다 골격을 먼저 세웠다**(사용자 지시 — 미구현 유스케이스는 `501 NOT_IMPLEMENTED`). ~~`maintenance`~~는 2026-08-23 착수와 함께 신설했다. `/mcp` 어댑터 모듈(MCP 담당 소유 — 구현은 `pms-old/`에 보존)은 **해당 작업 착수 시 담당이 추가**한다. 지원 모듈 후보(chat BFF·mcpconfig)는 미생성 유보 — 챗 연동 시점 M1에 재론. 베이스 패키지 = `kr.proten.pms`. "모듈 고정"은 해제 상태 유지(증설은 열림).
- **모듈 간 공개 계약 (모듈 루트에 있는 것 = 밖으로 나가는 전부 — §0)**: 소비자가 실제로 쓰는 좁은 면만 열고, 내부 계약(`ProjectQueryService` 등)은 하위 패키지에 둔 채로 둔다.

  | 계약 | 소유 | 소비자 | 무엇을 |
  |------|------|--------|--------|
  | `PersonDirectoryService` · `PersonRef` | person | project · maintenance | 배정·이슈에 붙일 인원 참조. **`division` 추가**(2026-08-23) — 프로젝트 응답의 팀·부문이 PM 소속에서 나오고, 부문은 가시성 DIVISION scope와 같은 해석(`OrgTree.topDivisionIdOf`)이다. `findIdByExactName`은 시드가 사람을 이름으로 적어 둔 경우의 창구 |
  | `OrgVisibilityService` · `OrgVisibility` | person | project · resource | 가시 인원 집합(scope 4단 판정 결과) |
  | `OrgPermissionService` · `OrgPermission` | person | project · auth | 프로젝트 밖 행위의 그룹 플래그 |
  | `AccountPort` | person(정의) | auth(구현) | 인력 등록 시 계정 생성 — 순환 회피의 방향 역전 |
  | **`WorkforceDirectoryService` · `WorkforceProfile`** | person | resource · `mcp` | **가동률의 분모·모집단·계수**(capacity·billable·gradeCoeff) · team·division **이름과 조직 id 2종**(2026-08-23 추가 — 웹은 `?orgUnitId=`를 받지만 챗은 화자로부터 유도해야 하고 `org_units.name`에 유니크 제약이 없다) · 조직 subtree 인원 · **집계 모집단 전체 명단**(2026-08-23 추가 `findAllAggregatablePersonIds` — 전사 scope 가시성은 `unrestricted`라 인원 집합이 비어 있어 집계 호출자가 명단을 얻을 경로가 없었다. 재직·비시스템만, 사용자 결정) |
  | **`AssignmentDirectoryService` · `MonthlyAssignment`** | project | resource | **가동률의 분자** — 그 달과 겹치는 배정 행(personId·projectId·projectName·**projectStatus**·monthlyMm) (2026-08-23 신설) |
  | `AuditQueryService` · `AuditRecord` 등 | audit | person · project | 감사 조회(권한 판정 없는 순수 조회) |
  | `NotificationService` · `NotifyCommand` 등 | notification | project · resource | 알림 적재 요청 |
  | **`ProjectLookupService` · `ProjectBrief`·`ProjectDetailBrief`** | project | `mcp` | **`search_projects`** — 가시성 판정 포함(404 은닉), 팀·부문은 **PM 소속 파생** (2026-08-23 신설) |
  | **`ProgressCommandService` · `ProgressResult`** | project | `mcp` | **`update_progress`** — 2단계 확인은 내부 유스케이스가 갖는다(구조 원칙 5). 밖으로 여는 유일한 쓰기 (2026-08-23 신설) |
  | **`MaintenanceLookupService` · `ContractBrief`·`ContractIssues`·`IssueBrief`·`CommentBrief`** | maintenance | `mcp` | **`search_maintenance`·`list_maintenance_logs`** — 가시성 판정 없음(D4-3 전사 공개). 계약 우선 해석 (2026-08-23 신설) |
  | **`UtilizationLookupService` · `UtilizationScope`·`UtilizationBrief`·`OverbookedBrief`** | resource | `mcp` | **`get_utilization`·`list_overbooked`** — 승격 소유자를 먼저 정한 첫 계약(2026-08-24 결정 기록 · git-workflow §3). `scope`(ME·MY_TEAM·DIVISION·COMPANY·PERSON) **해석을 도메인이 든다**: 웹은 `?orgUnitId=`를 호출자가 주지만 챗은 화자밖에 모르므로 서버가 팀·부문을 유도해야 하고(`WorkforceProfile`의 조직 id 2종), 그 판정이 어댑터에 남으면 챗과 화면의 범위가 갈릴 수 있다. `OverbookedBrief.Cause(projectName, mm)`는 과부하의 원인 배정 — 산식·모집단·`기본>100` 판정은 `UtilizationCalculator` 하나가 갖고 웹은 원인을 버린다(정본 한 벌) |
  | `PersonDirectoryService`에 **`findIdByExactName`** 추가 | person | maintenance | 시드가 사람을 이름으로 적어 둔 경우(유지보수 영업대표 3명). **정확히 한 명일 때만** 답한다 — 동명이인이면 이름은 식별자가 아니고 그때 조용히 틀린 사람을 가리킨다 (2026-08-23 신설) |

  신설 2종의 설계 근거: **`ProjectStatus`를 모듈 루트로 올려 배정 행에 함께 싣는다** — 가동률 모집단은 "진행중 프로젝트의 배정만"인데(2026-08-10 결정. 완료·수주확정까지 세면 시드 실측에서 김영삼(id 13)이 **1171%** 로 왜곡된다 — 구 기록의 "정태휘"는 익명 명부 이름이다(2026-08-24 재앵커)) 그 규칙은 EPIC C의 것이므로 project는 상태라는 **사실만** 내주고 판정은 resource가 한다. project가 걸러 버리면 모집단 정의가 두 모듈에 나뉜다. **행 단위로 내준다**(합계가 아니라) — 과부하 응답이 원인을 프로젝트별로 보여 주므로(`Cause(projectName, mm)`) 합계만 주면 같은 행을 두 번 읽는다. **인원 명단을 받는다**(orgUnitId가 아니라) — 가시성·billable 판정은 person·resource의 몫이고 project는 조직을 알지 못한다. **`PersonRef`를 확장하지 않고 나눴다** — 그것은 project가 쓰는 표시용 참조라, capacity·billable을 얹으면 resource의 관심사가 project의 컴파일 면에 올라온다(conventions §5).

- `/mcp` 어댑터가 호출하는 애플리케이션 서비스는 EPIC A(조회·진척률)·C(가동률)·D(이력)·H(`/api/me` = `whoami`)와 동일 — 도구 카탈로그 대응은 PRD-host §4-2. 서비스 API 변경은 공용 결정 기록 경유(2인 협업 경계). **"호출"의 방향은 미정** — M0 산출물은 도메인이 `mcp` 루트의 port를 구현하는 형태(의존 `project → mcp`)이고 이 줄은 그 반대로 읽힌다. 어느 쪽인지는 MCP 담당이 정한다(§10 횡단 기반 표).

## 4. 도메인 모델

모든 수정 가능 엔티티는 `version:long`(낙관적 락). 모듈 간 참조는 `*Id`.

- **person**(구 identity — 2026-08-21 개명): `OrgUnit`(parentId — nullable(회사 root), name — **임의 깊이 트리**, 2026-08-09 ⑧. 구 `Division`·`Team` 2단 대체, "부문"·"팀"은 트리 상 위치의 파생 개념) · `Grade`(name, coeff — 시드 값은 부록 B, 편집 가능 US-E4) · `PermissionGroup`(name · visibilityScope{COMPANY,DIVISION,TEAM,SELF — TEAM은 subtree 포함} · 기능 플래그 4종{createProject, manageContracts, manageAllProjects, manageOrg} · systemFixed — 2026-08-09 ⑦, 상위 PRD §4-3이 규칙 원본, US-E5) · `Person`(orgUnitId, gradeId, **groupId** — 구 orgRole 대체, capacity, **billable** — 가동률 집계 대상 여부, 상위 PRD §3 · 2026-08-06, **system** — 시스템 계정 플래그: 삭제·수정 불가, 인력·가동률·배정 목록 제외 · 2026-08-09 ④) · `User`(personId, email(로그인 ID), passwordHash, phone, notifPrefs — 내 계정 EPIC H 대응)
  - v1.0의 `Division.inScope`는 제거 — 전사 범위 전환으로 불필요(②)
- **project**: `Project`(client·name·solution(제품군)·engagement{REMOTE,ONSITE,PARTIAL_ONSITE — 원격·상주·부분상주. **OFFSITE 폐지**, 2026-08-09 ③⑥}·**managerId(PM)**·contractMM·기간·status·progress·deleted·version)  · `ProjectAssignment`(personId·**role{PM,PL,PARTICIPANT}**·기간·monthlyMM·status)
  - **기간 규칙 (2026-08-22)**: 종료일은 시작일보다 **뒤여야** 한다 — 같은 날짜도 거절(`400 VALIDATION_ERROR` field=endDate). 프로젝트·배정 양쪽에 같은 규칙이고 엔티티가 갖는다. 한쪽만 비어 있는 열린 기간은 허용한다(계약 전 단계에 종료일이 없을 수 있다)
  - 필드명·값은 시드 `projects.json` 정합(client·solution·managerId — v1.0의 account·productType 대체. AC A1-3의 pmId = managerId). 시드 engagement의 OFFSITE 32건은 적재 시 REMOTE로 흡수(부록 B)
  - **`ProjectAssignment.role`이 프로젝트 역할의 정본**(2026-08-03). `Project.managerId`는 대표 PM 파생 읽기 필드로 유지 — 시드 정합·조회 편의. 불변식: 프로젝트당 `role=PM` 정확히 1행, `managerId`와 일치. 값은 `PARTICIPANT`를 쓴다 — orgRole의 `MEMBER`와 이름이 겹치면 안 된다. `role=PL`은 복수 행 물리적으로 허용하되 API에서 당분간 1명으로 제약(제약 해제 시 스키마·접점 변경 없음)
  - `ProjectPermissionOverride`(projectId·role{PL,PARTICIPANT}·action{EDIT_INFO,ASSIGN,PROGRESS,COMPLETE_REOPEN}·allowed) — **기본값(상위 PRD §4-2 표)과 다른 셀만 저장**, 행 부재 = 기본값. PM 열·조회·삭제·이관은 저장 대상이 아니다(고정 — 상위 §4-2). 완료 처리·재개는 `COMPLETE_REOPEN` 단일 action(묶음 규칙). 낙관적 락은 `Project.version` 공용 (2026-08-06 — US-A8)
- **resource**: `Capacity`(personId·month·availableMM). 가동률은 배정 합산으로 계산(저장 엔티티 아님).
- **maintenance** (2026-08-06 재설계 — 계약/사이트/이슈 3층):
  - `MaintenanceContract`(**id = 시드 원본 계약 id**(부록 B) · sourceProjectId — **nullable**, 이관 생성 시 1:1·OEM 직접 등록은 null · 계약사 · 계약명 · 상태{예정,신규,유지,종료} · 계약일 + **contractDateNote**(비날짜 원문 보존) · 시작/종료일 · 계약금액 · 월간금액 · 영업대표 personId · **sheetSection**(원본 시트 섹션 — status에서 파생되지 않는다, 2026-08-23) · **category**(대분류 검색엔진\|인프라)·**targetInfra**(라이선스·제품 사양 "검색엔진 3Copy+추천모듈") — 둘 다 계약 레벨이다: 다중 사이트 계약에서도 계약당 한 값이고 라이선스 수량은 상거래 조건이다(2026-08-23 결정) · 정기점검(정보 텍스트 — 일정 엔진·자동 이슈 없음) · 비고 · version)
  - `MaintenanceSite`(contractId · 고객사명 · **channel{OEM,ENT} — nullable**(OEM 채널 계약은 원천 프로젝트가 없다는 US-D2 근거, 2026-08-23) · **serverSpec** — 서버는 사이트마다 다르다(시드는 계약 행에 적어 두었지만 값이 45사이트 중 한 곳을 가리킨다, 2026-08-23 결정) · **engineerId — 담당 엔지니어의 정본(사이트 단위), nullable**: 신규 예정·종료 섹션의 사이트는 미배정이고 그 상태를 "미배정 이슈" 필터(D3-4)가 드러낸다) — 계약:사이트 **1:N** (실측: 가온아이 1계약 45사이트, 다만 105계약 중 103건은 사이트 1개다)
  - `MaintenanceContact`(siteId · 구분{계약사,고객사} · 이름 · 직급 · 전화 · 이메일 + **raw**(시트 원문 보존)) — 구 시트 "담당자 정보" 텍스트 블롭의 정규화. 원문 형식이 불규칙해(4필드 완비 / 전화만 / 회사명 접두 / 이름에 괄호 주석) **전화·이메일만 파싱하고 나머지는 원문이 답한다**(2026-08-23 결정 — 파싱 실패가 정보 유실로 이어지지 않게)
  - `MaintenanceIssue`(**id = 시드 원본 이슈 번호**(부록 B) · siteId — **nullable** · type{장애,문의,요청} · 제목 · 상태{접수,처리중,고객확인대기,완료} · assigneeId — **기본값 = 사이트 engineerId** · 접수일 · 완료일 · version) · `IssueComment`(issueId · 작성자 personId · 내용, **append-only** — 구 `MaintenanceLog` 불변식 계승. 수정·삭제 경로도 version도 없다)
    - **siteId가 nullable인 이유 (2026-08-23)**: 이슈를 사이트에 잇는 것은 **사이트명 일치만** 인정한다. 링크 기준 3종(계약명·계약사·사이트명 — 2026-08-14)은 "어느 **계약**인가"를 찾는 기준이고, 이슈가 갖는 것은 siteId이므로 계약 단위 매칭으로는 어느 사이트인지 정할 수 없다. 실측: 시드 이슈 14건 중 태그가 `[전력거래소, 사이버다임]`인 6건은 전력거래소가 실제 고객이고 사이버다임은 벤더(계약사)라, 계약사로 붙이면 그 계약사의 여러 계약 중 하나의 첫 사이트에 매달린다 — 모르는 것을 아는 척하는 것이다. 결과: **연결 7건(한국거래소 → 계약 101) · 미연결 7건**(부록 B "미연결 실데이터 그대로 둠", host 2026-08-12 실측과 일치)
  - 프로젝트:계약 = **1:1**(이관 경로) · 프로젝트 없는 계약 존재(직접 등록 — US-D2). MCP `list_maintenance_logs` 접점 영향은 PROGRESS 결정 기록 참조(확인 완료 2026-08-06)
- **notification**: `Notification`(recipientId·type·refType·refId·message·read·createdAt)
- **common**: `AuditLog`(entityType·entityId·action·actorId·source{WEB,MCP}·before·after·**projectId(nullable)**·createdAt, append-only) · `CommonCode`
  - `createdAt`은 2026-08-21 구현에서 명시화 — G2-2의 "최신순"과 A7-3의 "행위자·시각은 감사 로그가 담당"이 시각 컬럼을 전제한다. before·after는 **바뀐 필드만** 담는 JSON 스냅샷이고, 무엇이 바뀌었는지 판정하는 지점은 도메인 쪽 한 곳(`ProjectAuditRecorder`)이다
  - `projectId`는 프로젝트 스코프 이벤트(프로젝트 CRUD·상태 전이·진행률·배정·역할·권한 커스텀)에만 채운다 — 배정·역할처럼 entityId가 프로젝트가 아닌 행을 프로젝트별로 필터하기 위한 참조 컬럼(US-G2, 2026-08-06). 조직·계정 변경(E1·E2·H1)은 null. **저장은 이 테이블 하나뿐** — 프로젝트별 로그를 이중 기록하지 않는다(통합로그와 프로젝트 이력은 같은 행의 두 조회 뷰)

## 5. 상태 전이 (Project)

```
계약대기 → 수주확정 → 진행중 → 완료 → 유지보수중
```

- 진행중→완료는 **명시적 완료 처리**(`POST /complete` — US-A7)로만 일어난다. 진행률 100%가 전제조건이며, **100% 저장 자체는 상태를 바꾸지 않는다** (2026-08-06 — 자동 전이 폐지: 참여자의 진척률 쓰기(US-A2)가 상태 전이 권한(US-A5)을 우회하는 부수효과 제거 + 실무의 100%≠완료(검수·납품 잔여) 수용). 완료 처리 시 `ProjectCompleted` 발행.
- 완료 상태에서만 이관 가능 → 완료→유지보수중.
- **역방향 전이 금지**(불변식). 유일한 예외 = **재개**(완료→진행중, `POST /reopen` — US-A7). `PUT /projects/{id}`로는 어떤 역방향도 불가(A5-2 유지) — 완료·재개·이관은 전용 경로로만. 유지보수중에서는 재개 불가.
- 모든 전이 AuditLog `STATE_CHANGE`.
- 시드 status 분포는 4단계(완료 319·진행중 34·수주확정 19·계약대기 10) — "유지보수중"은 운영 중 이관으로만 생성.
- **phase(탭) = status 파생 그룹** (2026-08-06): 영업={계약대기,수주확정} · 솔루션={진행중,완료}. 저장 컬럼이 아니라 서버 단일 정의의 파생값(원본 이중화 금지) — 목록 `?phase=` 필터 + 단건 응답 파생 필드(§7). 유지보수 탭은 프로젝트가 아닌 `MaintenanceContract` 목록(§4)이 원천 — status=유지보수중 프로젝트의 화면 노출은 연결된 계약이 담당한다.

## 6. 기능 요구사항 — 유저스토리 + 수용기준(AC)

각 Given·When·Then = 테스트 1개. AC 없는 코드 금지. **역할 태그 규칙**: `[PM·PL·참여자]`는 프로젝트 역할(상위 PRD §4-2), `[그룹: …]`은 권한 그룹의 기능 플래그(§4-3 — 2026-08-09 일반화. 구 `[orgRole: …]` 태그는 기본 그룹 매핑으로 읽는다: TEAM_LEAD·DIVISION_HEAD·ADMIN 열거 = 해당 플래그가 켜진 기본 그룹들, 실제 판정은 언제나 그룹 플래그). **"전 프로젝트 관리" 플래그 보유자(기본: 관리자 그룹)는 §4-1 치환(모든 프로젝트에서 PM 간주)으로 모든 PM 태그에 자동 포함되므로 태그에 별도 표기하지 않는다.** 역할 태그와 권한 AC(403 검증 포함)는 **기본값 매트릭스·기본 그룹 전제**다 — 프로젝트별 커스텀(US-A8)이나 그룹 편집(US-E5) 적용 시 그 정의가 판정 기준(2026-08-06·2026-08-09).

### EPIC A · 프로젝트

**US-A1 프로젝트 생성 권한자로서 새 프로젝트를 등록한다** [그룹: 프로젝트 생성 — 기본 그룹 관리자·부문장·팀장]
- A1-1 Given 유효 입력 When `POST /api/projects` Then `201`, 상태=계약대기, AuditLog CREATE 1건
- A1-2 Given 같은 고객사·같은 이름(정규화 후, soft-deleted 제외) When 생성 Then `409 DUPLICATE_NAME` — 정규화 = trim·연속 공백 1개로 축약·영문 대소문자 무시
- A1-3 Given 없는 clientId/pmId When 생성 Then `422 REF_NOT_FOUND`
- A1-4 Given 참여자 목록 When 생성 Then 각자 지정 role(PM/PL/PARTICIPANT)로 배정. **PM 1명 지정 필수** — 생성자 본인이 아니어도 된다. 미지정 시 `422 PM_REQUIRED`
- A1-5 Given "프로젝트 생성" 플래그 없는 그룹(기본: 팀원) 토큰 When 생성 Then `403` — 생성은 그룹 플래그가 판정한다(상위 PRD §4-3). 프로젝트 역할은 판정 축이 아니다
- A1-6 Given `role=PM`이 2행 이상인 입력 When 생성 Then `422 MULTIPLE_PM` — 프로젝트당 `role=PM` 정확히 1행 불변식
- ~~A1-7~~ 삭제(2026-08-06) — PL 복수 허용 확정(상위 PRD §4-2). `role=PL` 복수 행은 정상 입력이다

**US-A2 참여자로서 본인 배정 프로젝트 진행률을 갱신한다** [PM·PL·참여자] — 웹·MCP(`update_progress`) **동일 서비스·동일 2단계 프로토콜** (2026-08-02 프로토타입 동작 승격. **2026-08-09 ① 완화: 웹 UI는 100% 저장만 확인 모달을 띄우고, 그 외 값은 confirmed=true 단건 호출로 1클릭 저장** — 서비스 프로토콜과 MCP 2단계 확인 카드는 무변경, UI 확인 강도만 분리. 부록 A)
- A2-1 Given 본인 배정(role 무관 — PM·PL·PARTICIPANT 동일) When `PUT /progress {progress:90, version, confirmed:false}` Then `200` + 변경 요약 반환, DB 미변경. `Project.progress`는 단일 값이므로 부분("본인 몫") 수정 개념은 없다
- A2-2 Given 요약 확인 후 When `confirmed:true` 재호출 Then `200` 커밋 + AuditLog UPDATE
- A2-3 Given progress=100·confirmed=true When 저장 Then `200` 커밋 — **상태는 그대로**(자동 전이 폐지 — 2026-08-06, §5). 응답에 완료 처리 가능 안내(completable=true)를 포함해 프론트·챗이 완료 처리(US-A7)를 유도할 수 있게 한다
- A2-4 Given 본인 미배정 When 수정 Then 가시성 밖이면 `404`(은닉), 가시성 안이면 `403`
- A2-7 Given 관리자 그룹("전 프로젝트 관리" 플래그)·미배정 When 수정 Then `200` — 플래그 보유자는 모든 프로젝트에서 PM으로 간주(상위 PRD §4-1 치환)
- A2-5 Given progress<0 or >100 When 저장 Then `400`
- A2-6 Given version 불일치 When `confirmed:true` Then `409 STALE_VERSION` + 최신 progress·version 반환
- A2-8 Given status=완료 When `PUT /progress` Then `409 PROJECT_COMPLETED` — 완료 상태의 진척률 직접 수정 금지, 재개(US-A7) 후 수정 (2026-08-06 — 유저_시나리오 §7 발견 #3 해소)
- A2-9 Given status ∈ {계약대기, 수주확정, 유지보수중} When `PUT /progress` Then `409 NOT_IN_PROGRESS` — **진척률은 진행중에서만 수정한다** (2026-08-22 결정: 계약 전·수주 단계에는 기록할 진척이 없고, 이관 후에는 계약이 소관이다). 완료는 A2-8이 더 구체적인 안내(재개 후 수정)를 주므로 그대로 둔다. **MCP `update_progress`도 같은 서비스라 같은 거절을 받는다** — 챗은 "상태를 먼저 옮기라"는 거절을 전달한다

**US-A3 가시성 범위 내에서 프로젝트를 조회한다**
- A3-1 Given 팀장 When `GET /projects` Then **자기 팀 범위 ∪ 본인이 배정된 프로젝트**(타 팀 포함) page 봉투 — 가시성은 프로젝트 역할이 확장한다(상위 PRD §4-4). 시드 기준 참여자가 2개 팀 이상인 프로젝트가 46건이라 상시 발생하는 경로다
- A3-2 Given 범위 밖 id When 상세조회 Then `404` (은닉)
- A3-3 Given 타 팀 프로젝트에 배정된 사용자 When 그 프로젝트 상세조회 Then `200` + 해당 프로젝트의 배정 레코드(타 팀 인원 포함) 노출. 단 그 인원의 **다른 프로젝트·개인 전체 가동률은 조직 가시성 규칙을 그대로 따른다**(프로젝트 컨텍스트 한정)

**US-A4 PM으로서 프로젝트를 소프트 삭제한다** [PM]
- A4-1 When `DELETE /projects/{id}` Then `200` + `{success:true}`(2026-08-22 공통 봉투 — 구 `204`), deleted=true, 목록·중복검사 제외, AuditLog DELETE
- A4-2 Given PL 또는 참여자 토큰 When 삭제 Then `403` — 단 **"프로젝트 생성" 플래그 보유자는 PM이 아니어도 삭제 가능**(2026-08-22 결정 — 상위 PRD §4-2 삭제 행 확장. 판정 = PM 역할 OR 생성 플래그)

**US-A5 프로젝트 정보 수정은 PM·PL만, 상태는 정의된 전이만 허용된다** [PM·PL] — 프로토타입 수정 폼의 status 자유 편집 대비 서버 강제 (2026-08-02)
- A5-1 Given 순방향 전이(계약대기→수주확정→진행중 — §5) When `PUT /projects/{id}` (status 포함) Then `200` + AuditLog STATE_CHANGE. **완료·재개·이관으로의 전이는 이 경로에서 불가**(`409 INVALID_TRANSITION`) — 전용 경로(US-A7 `/complete`·`/reopen`, US-D1 `/handover`)로만 (2026-08-06)
- A5-2 Given 역방향·건너뛰기 전이 When 저장 Then `409 INVALID_TRANSITION`, 아무것도 안 바뀜
- A5-3 Given 참여자 토큰 When `PUT /projects/{id}` (정보 수정) Then `403` — 정보 수정 권한(상위 PRD §4-2 PM·PL)은 이 AC가 검증한다. 진행률은 별도 경로(US-A2)라 참여자도 가능

**US-A6 프로젝트 역할을 지정·교체한다** [PM] (2026-08-03 권한 모델 확정에 따른 신설)
- A6-1 Given 현 PM When `PUT /projects/{id}/pm {personId, version}` Then `200`, `ProjectAssignment.role` PM 이동 + `Project.managerId` 동기화, AuditLog UPDATE — STATE_CHANGE는 §5 상태 전이 전용
- A6-2 Given PL·참여자 토큰 When PM 교체 Then `403`
- A6-3 Given PM When `PUT /projects/{id}/roles {personId, role}` (role ∈ {PL, PARTICIPANT}) Then `200`, AuditLog UPDATE. PL·참여자 토큰은 `403`. **해제 = `role=PARTICIPANT`로 변경**(배정은 유지 — 배정 자체의 종료는 US-B2)
- A6-6 Given 대상이 해당 프로젝트에 미배정 When `role=PL` 지정 Then 배정을 함께 생성 — PM·PL은 항상 배정 인원(상위 PRD §4-2). **자동 생성 배정의 기본값: 기간 = 지정일~프로젝트 종료일 · monthlyMM = 0** (M/M 입력은 PM이 배정 패널에서 별도 수행)
- A6-4 Given 교체 대상이 해당 프로젝트에 미배정 When PM 교체 Then 배정을 함께 생성(기본값은 A6-6과 동일). 직전 PM은 `role=PARTICIPANT`로 강등(배정은 유지)
- A6-5 Given 임의 시점 When 조회 Then 프로젝트당 `role=PM` 정확히 1행 · `Project.managerId`와 일치 (불변식 — 경계 테스트 대상)
- A6-7 Given `role=PM` When `/roles` 경유 지정 Then `422 INVALID_ROLE` — PM 변경은 `/pm` 전용(A6-5 불변식 우회 차단). ~~두 번째 PL 차단(`MULTIPLE_PL`)~~은 삭제(2026-08-06 — PL 복수 허용, A1-7과 함께)

**US-A7 프로젝트를 완료 처리하고, 필요 시 재개한다** [PM·PL·참여자] (2026-08-06 신설 — §5 자동 전이 폐지 대응. 진척률과 같은 실무 경로라 배정 전원 — 상위 PRD §4-2)
- A7-1 Given status=진행중·progress=100·본인 배정(role 무관) When `POST /projects/{id}/complete {version}` Then `200`, status=완료 + `ProjectCompleted` + AuditLog STATE_CHANGE
- A7-2 Given progress<100 When 완료 처리 Then `409 PROGRESS_INCOMPLETE`, 아무것도 안 바뀜 — 완료의 전제는 진행률 100%(재개 시 90 복귀 값이 의미를 갖는 근거)
- A7-3 Given status=완료 When `POST /projects/{id}/reopen {version}` Then `200`, status=진행중 + **progress=90으로 리셋** + `ProjectReopened` + AuditLog STATE_CHANGE — 사유 입력 없음, 행위자·시각은 감사 로그가 담당. 이후 진척률은 US-A2 정상 경로로 수정
- A7-4 Given status=유지보수중 When 재개 Then `409 INVALID_TRANSITION` — 이관 후 재개 불가(Maintenance 정합 보호). 완료 처리도 진행중에서만(그 외 상태 동일 코드)
- A7-5 Given 본인 미배정(비ADMIN) When 완료 처리/재개 Then 가시성 밖 `404`(은닉) / 가시성 안 `403` — A2-4와 동일 의미론, ADMIN은 A2-7과 동일 치환

**US-A8 PM으로서 이 프로젝트의 역할별 권한을 조정한다** [PM] (2026-08-06 신설 — 상위 PRD §4-2 "프로젝트별 권한 커스텀"이 규칙 원본. 기본값 = §4-2 표)
- A8-1 When `GET /projects/{id}/permissions` Then `200` — 역할×기능 매트릭스(기본값 + override 병합 결과)와 **셀별 고정 여부**(editable) 반환. 조회는 가시성 범위(프론트가 잠금 표시를 그릴 수 있어야 한다)
- A8-2 Given PM When `PUT /projects/{id}/permissions {overrides:[{role, action, allowed}], version}` Then `200` + AuditLog UPDATE(before/after) — **기본값과 같은 값은 저장하지 않는다**(해당 override 행 삭제). `overrides: []` = 전체 기본값 복원(별도 API 없음). 감사 단위 = 저장 1건(배치 저장 — 2026-08-09 ② 프로토타입 검증으로 확인)
- A8-3 Given PL·참여자 토큰 When `PUT` Then `403` — 조정은 PM만(ADMIN은 §4-1 치환)
- A8-4 Given 고정 셀(role=PM · action=조회/삭제/이관) 포함 요청 When `PUT` Then `422 IMMUTABLE_PERMISSION`, 아무것도 안 바뀜 — 완료·재개를 개별 action으로 쪼갠 요청도 동일(유효 action은 §4의 4종)
- A8-5 Given `PROGRESS` off인 프로젝트의 참여자 When `PUT /progress`(US-A2) Then `403` — 판정이 프로젝트 매트릭스를 참조함을 검증. **MCP `update_progress`도 동일 서비스라 동일 거동**(챗은 거절 전달 — 상위 §4-2 MCP 영향)
- A8-6 Given `ASSIGN` on(PL로 확장)인 프로젝트의 PL When `POST /assignments`(US-B1) Then `201` — 확장 방향 검증(B1-4의 PL 403은 **기본값 프로젝트** 전제)
- A8-7 Given version 불일치 When `PUT` Then `409 STALE_VERSION` — `Project.version` 공용(§4)

### EPIC B · 인력 배정

**US-B1 PM으로서 인력을 배정한다** [PM]
- B1-1 When `POST /projects/{id}/assignments` Then `201` + `MemberAssignedToProject`
- B1-2 Given 종료 안된 동일 personId 존재 When 재배정 Then `409 DUPLICATE_ASSIGNMENT` (키=projectId+personId+status≠종료)
- B1-3 When 커밋 후 Then resource가 해당 월 가동률 재계산
- B1-4 When `PUT /assignments/{id}` (기간·monthlyMM 수정, `version` 포함) Then `200` + `AssignmentUpdated` → 영향 월 가동률 재계산. PL·참여자 토큰은 `403`(M/M 입력은 PM — 상위 PRD §4-2) — §7의 `PUT /assignments/{id}` 대응 AC
- B1-5 배정 M/M의 의미 = **실투입 계획**(상위 PRD §3 · 2026-08-10) — 입력 UI 레이블·헬프에 "계약 배분 아님"을 명시(부록 A 배정 패널)

**US-B2 배정 종료 시 이후 월 가동률에서 빠진다** [PM]
- B2-1 When `DELETE /assignments/{id}` Then status=종료, `AssignmentClosed`, **`endDate`가 종료월 말일로 당겨진다**(그보다 이른 종료일은 늘리지 않는다), 종료월 이후 가동률 모집단에서 제외. 행은 남는다 — 지난달 가동률은 그때의 배정으로 계산된다
  - **종료일을 당기는 이유 (2026-08-23 결정)**: 이전에는 상태만 CLOSED로 바꾸고 종료 시점을 어디에도 남기지 않아 "종료월 이후"를 데이터로 표현할 수 없었다. 상태만 보고 거르면 종료한 순간 지난달 집계에서까지 사라지고, 상태를 무시하면 영영 빠지지 않는다. 종료일을 당기면 **기간 겹침 판정 하나로** 두 요구가 함께 성립하고 C1-1의 분자 질의도 상태를 볼 필요가 없다. 시작 전 배정을 종료하면 빈 구간(종료일 < 시작일)이 되어 어느 달에도 세지 않는다 — 시작하지 않은 채 끝난 배정에는 그것이 맞는 결과다(`// ASSUMPTION:` 주석)

### EPIC C · 가동률

**US-C1 특정 월의 가동률을 조회한다**
- C1-1 When `GET /utilization?month=&personId=` Then 기본=Σ배정MM÷가용×100, 보정=Σ(배정MM×coeff)÷가용×100 (2026-08-10 재정의 — 배정 M/M=실투입 계획, 산식 원본은 상위 PRD §3)
- C1-2 Given A0.5+B0.7, 가용1.0, coeff1.2(책임 — 시드 직급계수 기준) Then 기본=120, 보정=144
- C1-3 Given `overbooked=true` Then **기본**>100인 사람만 (2026-08-10 — 구 "보정>100" 대체)
- C1-4 Given 배정 변경 Then 커밋 후 2초 내 가동률 조회 API에 반영 — 이벤트 재계산 완료 기준, 통합 테스트로 검증
- C1-5 Given `billable=false` 인원 When 팀·부문·전사 집계 또는 `overbooked` 목록 조회 Then 모집단에서 제외 — 개인 지정 조회(personId)는 billable 무관 (상위 PRD §3 · 2026-08-06)
- C1-6 When 가동률 조회(단건·집계 공통) Then 응답 항목에 **`team`·`division` 포함** — 집계 결과를 소속별로 정리하려면 필요(인원 수만큼 개인 조회를 반복하지 않게 한다). MCP `get_utilization` 응답과 동일 (2026-08-11 — PROGRESS 결정 기록 · 2026-08-12 MCP 확인 완료 — 합의 성립)

### EPIC D · 유지보수 (2026-08-06 재설계 — 계약/사이트/이슈. 권한·가시성 규칙은 상위 PRD §4-2·§4-3 참조)

**US-D1 완료 프로젝트를 유지보수로 이관한다** [PM]
- D1-1 Given status=완료·계약 필수 정보(계약명·기간·금액·사이트 1개 이상, 각 사이트 **engineerId**) When `POST /handover` Then `201` — Contract+Site 생성+상태전이(완료→유지보수중) **한 트랜잭션**, 커밋 후 `MaintenanceHandedOver`. 필수값을 이관 시점에 받으므로 "유지보수중인데 계약 정보 없는 프로젝트"는 원천적으로 못 생긴다
- D1-2 Given status≠완료 When 이관 Then `409 NOT_COMPLETED`, 아무것도 안 바뀜(원자성)
- D1-3 Given 계약 필수 정보 누락 When 이관 Then `400 VALIDATION_ERROR`, 아무것도 안 바뀜 — 상태 전이도 미발생

**US-D2 유지보수 계약을 직접 등록·수정한다** [그룹: 계약 관리 — 기본 그룹 관리자·부문장·팀장] (v2.4 신설 — OEM 채널 계약은 원천 프로젝트가 없다)
- D2-1 When `POST /api/maintenance/contracts` (sourceProjectId 없이) Then `201` + AuditLog CREATE — 이관과 직접 등록, 입구 2개
- D2-2 When `PUT /api/maintenance/contracts/{id}` (`version` 포함) Then `200` + AuditLog UPDATE. **삭제 API 없음** — 계약 종료는 상태{종료}로 (연 단위 갱신 이력 보존)
- D2-3 Given "계약 관리" 플래그 없는 그룹(기본: 팀원) 토큰 When 계약·사이트·연락처 등록/수정 Then `403` — 계약은 프로젝트 밖 행위라 그룹 플래그가 판정한다(상위 PRD §4-3)
- D2-4 When `POST /contracts/{id}/sites` · `PUT /sites/{id}` (engineerId·연락처 포함) Then 계약과 동일 권한 + AuditLog

**US-D3 유지보수 이슈를 등록·처리한다** [로그인 사용자 전체] (v2.4 신설 — 구 이슈 게시판 대체)
- D3-1 When `POST /api/maintenance/issues {siteId, type, 제목}` Then `201` · **assigneeId 기본값 = 해당 사이트 engineerId** · 담당자에게 알림(`MaintenanceIssueRegistered` — §8)
- D3-2 When `PATCH /api/maintenance/issues/{id}` (상태·담당 재배정, `version`) Then `200` + AuditLog — 상태 흐름 접수→처리중→고객확인대기(선택)→완료, 완료 시 완료일 기록. 역방향은 재개(완료→처리중)만 허용
- D3-3 When `POST /issues/{id}/comments` Then `201` **append-only** — 수정·삭제 API 없음, 보정은 새 코멘트로만(구 MaintenanceLog 불변식 계승)
- D3-4 When `GET /api/maintenance/issues?status=&assigneeId=&siteId=&contractId=` Then page 봉투 — **미배정(assigneeId=null) 필터 포함**, "내 담당 열린 이슈"가 조회 한 번에 나와야 한다

**US-D4 유지보수를 조회한다** [로그인 사용자 전체]
- D4-1 When `GET /api/maintenance/contracts?status=&계약사=&종료일=&keyword=` Then page 봉투 — 유지보수 탭의 원천(시트 대체). **`keyword`는 계약명·계약사·사이트명(고객사) 부분 일치**이며 매칭된 사이트를 함께 반환한다 — 45사이트 계약(가온아이)에서 고객사명으로 계약에 도달하는 유일한 경로. MCP `search_maintenance`와 동일 매칭 범위 (2026-08-11 신설 — PROGRESS 결정 기록 · 2026-08-12 MCP 확인 완료 — 합의 성립)
- D4-2 When `GET /api/maintenance/contracts/{id}` Then 계약 + 사이트 목록(engineerId) + 연락처 + 이슈 요약 · 원 프로젝트 링크(sourceProjectId nullable)
- D4-3 유지보수 조회는 **전사(로그인 사용자 전체)** — 조직 가시성 미적용·404 은닉 없음. 시트·게시판 현행 계승: 계약·이슈는 팀 경계 없는 회사 공용 자산 (게이트 P에서 확인)

### EPIC E · 조직 · 사용자 관리 (E1~E5 전체 [그룹: 사용자/조직/권한 관리 — 기본 그룹 관리자만])

**US-E1 사람의 소속 조직을 이동한다**
- E1-1 When `PUT /people/{id}/org-unit` Then orgUnitId 변경, 가시성 즉시 반영, AuditLog UPDATE — STATE_CHANGE는 §5 상태 전이 전용(v2.1 정리)
- E1-2 Given 진행 중 배정 보유 When 이동 Then 허용+경고. 과거 집계는 현재 소속 기준(시점 미보존)

**US-E2 관리자로서 사용자를 등록·수정·삭제한다** (2026-08-02 채택 — 프로토타입 기구현)
- E2-1 When `POST /api/people {name, orgUnit, grade, group}` Then `201` + AuditLog CREATE — User 계정은 부록 B 규칙(email·초기 비밀번호)으로 생성
- E2-2 When `PUT /api/people/{id}` Then `200` — 이름·소속 조직·직급(coeff)·**권한 그룹**(2026-08-09 ⑦ — 그룹 부여는 이 경로) 변경, AuditLog UPDATE
- E2-3 When `DELETE /api/people/{id}` Then `200` + `{success:true}`(구 `204`) — soft 비활성(로그인 차단·목록 제외), 과거 배정·감사·집계는 보존
- E2-4 Given "사용자/조직/권한 관리" 플래그 없는 토큰 When 위 요청 Then `403`
- E2-5 Given 시스템 관리자 계정(`Person.system` — admin@proten.co.kr) When `PUT`/`DELETE` Then `422 IMMUTABLE_ACCOUNT`, 아무것도 안 바뀜 — 감사 actor·수습 주체 보존 (2026-08-09 ④. 목록 제외는 §4 `Person.system` 정의)

**US-E3 관리자로서 조직 트리를 관리한다** (2026-08-09 ⑧ 신설 — 회사(root)→부문→팀→임의 깊이, 상위 PRD §4-3)
- E3-1 When `POST /api/org-units {parentId, name}` Then `201` + AuditLog CREATE — 임의 깊이 허용(2단 고정 해제)
- E3-2 When `PUT /api/org-units/{id} {name}` Then `200` + AuditLog UPDATE — 소속 인원·프로젝트는 orgUnitId 참조라 표시가 즉시 동기화된다(비정규화 이름 컬럼 금지)
- E3-3 Given 소속 인원·프로젝트·하위 조직 중 하나라도 존재 When `DELETE /api/org-units/{id}` Then `409 IN_USE`, 아무것도 안 바뀜 — 빈 노드만 삭제 가능
- E3-4 Given TEAM scope 사용자의 소속 노드에 하위 조직 존재 When 목록 조회 Then 하위 조직(subtree) 인원·프로젝트 포함 — 가시성 subtree 검증(상위 §4-4)

**US-E4 관리자로서 직급을 관리한다** (2026-08-09 ⑤ 신설 — 2026-08-06 "설정 편집 탭 승격 안 함" 대체)
- E4-1 When `POST /api/grades {name, coeff}` Then `201` + AuditLog CREATE
- E4-2 When `PUT /api/grades/{id}` (coeff 변경) Then `200` + AuditLog UPDATE — 보정 가동률은 매 조회 계산(캐시 미도입 — 2026-08-06)이라 다음 조회부터 즉시 반영
- E4-3 Given 사용 인원 존재 When `DELETE /api/grades/{id}` Then `409 IN_USE`

**US-E5 관리자로서 권한 그룹을 관리한다** (2026-08-09 ⑦ 신설 — 규칙 원본은 상위 PRD §4-3)
- E5-1 When `POST /api/permission-groups {name, visibilityScope, flags}` Then `201` + AuditLog CREATE
- E5-2 When `PUT /api/permission-groups/{id}` (`version` 포함) Then `200` + AuditLog UPDATE(before/after) — 판정·가시성·404 은닉이 즉시 새 그룹 정의를 따른다
- E5-3 Given 관리자 그룹(systemFixed) When `PUT`/`DELETE` Then `422 IMMUTABLE_GROUP` — 자기 잠금 방지(상위 §4-3, US-A8 고정 셀과 동일 원리)
- E5-4 Given 소속 인원 존재 When `DELETE /api/permission-groups/{id}` Then `409 IN_USE` — 그룹 부여 해제(E2-2) 후 삭제

### EPIC F · 알림

**US-F1 이벤트를 구독해 인앱 알림을 적재하고 SSE로 푸시한다**
- F1-1 Given `OverbookingDetected` Then 해당 인원과 같은 소속 조직의 팀장 그룹 사용자(구 orgRole=TEAM_LEAD — 기본 그룹 매핑)에게 Notification 생성
- F1-2 Given 동일 이벤트 중복 Then 알림 1건만(멱등)
- F1-3 When `GET /notifications?read=false` Then 본인 미읽음만, `PATCH /{id}/read` → `200` + `{success:true}`(구 `204`)
- F1-4 Given 알림 생성 When 수신자가 SSE 연결 중 Then 즉시 푸시. 미연결이면 재연결·재조회 시 반영 (⑥ — 구 2026-07-13 SSE 채택 결정)
- F1-5 Given 수신자의 알림 설정(notifPrefs — H1-4)이 해당 유형 꺼짐 Then 적재·푸시하지 않음

**US-F2 마감 임박 프로젝트를 매일 점검해 알린다** (2026-08-02 — PMS-M5 @Scheduled의 AC 보강)
- F2-1 Given 종료일이 **D-7** 이내인 진행중 프로젝트 When 일일 스케줄러 실행 Then PM(managerId)에게 Notification 1건 (N=7 — 2026-08-06 확정)
- F2-2 Given 같은 프로젝트·같은 날 재실행 Then 중복 알림 없음(멱등)

**US-F3 완료 처리가 지연된 프로젝트를 리마인드한다** (2026-08-06 신설 — 완료 전이가 명시적 행위가 되며 "다 끝났는데 완료 처리를 잊은" 상태가 생김)
- F3-1 Given progress=100·status=진행중인 채 **7일 경과** When 일일 스케줄러 실행 Then 해당 프로젝트의 **PM·PL**에게 Notification 1건 (100% 도달 시각 추적은 단순·표준 구현 — `// ASSUMPTION:` 주석) · 참여자 제외는 노이즈 방지, F1-5 알림 설정으로 개별 해제 가능
- F3-2 Given 동일 프로젝트·미해소 상태 Then 알림은 1회만(멱등 — F2-2와 동일). 재개 후 다시 100% 도달하면 새 사이클로 재계산
- F3-3 Given `ProjectReopened` 수신 Then 그 프로젝트의 **미읽음 완료 지연 알림 삭제**(회수) — 읽은 알림은 유지, SSE 재푸시 없이 다음 목록 조회에 반영

### EPIC G · 감사 (횡단)

**US-G1 모든 변경이 자동 기록된다**
- G1-1 Given 임의 변경 Then AuditLog 1건 자동 생성(before/after)
- G1-2 Then AuditLog 수정·삭제 API 부재(append-only)
- G1-3 When `GET /api/audit` ("사용자/조직/권한 관리" 플래그 — 기본 그룹 관리자만) Then page 봉투 목록 · 플래그 없는 토큰 `403` (2026-08-02 채택 — 프로토타입 설정 화면의 감사 탭 대응, 권한 판정은 2026-08-09 그룹 플래그로 일반화. **통합로그** — 조직·계정 변경까지 전체를 담는 유일한 뷰. 프로젝트 스코프 뷰는 US-G2)

**US-G2 프로젝트별 변경 이력을 조회한다** [가시성 범위] (2026-08-06 신설 — 완료·재개가 배정 전원으로 열리며(US-A7) PM·팀장의 오조작 추적 수요 대응. **별도 저장 없음** — AuditLog 단일 원본(G1-1·G1-2 불변식 유지), `projectId` 필터 뷰만 추가)
- G2-1 Given 프로젝트 스코프 변경(프로젝트 CRUD·상태 전이·진행률·배정·역할·권한 커스텀 — §4 목록) When AuditLog 기록 Then `projectId` 채움 · 조직·계정 변경은 null
- G2-2 When `GET /projects/{id}/audit` Then `200` page 봉투 — 해당 `projectId` 행만 최신순, before/after·actorId·source 포함(통합로그와 같은 행)
- G2-3 Given 가시성 밖 사용자 When 조회 Then `404`(은닉 — A3-2와 동일 의미론). **가시성 안이면 역할 무관 조회 가능**(참여자 포함 — 2026-08-06 "가시성 범위 전체" 확정. 챗에서 보이는 것 = 화면에서 보이는 것 원칙과 정합: 이력의 대상 데이터를 볼 수 있는 사람은 그 변경 사실도 본다)

### EPIC H · 내 계정 (2026-08-02 채택 — 프로토타입 기구현)

**US-H1 로그인 사용자로서 내 계정을 관리한다**
- H1-1 When `GET /api/me` Then 본인 personId·이름·소속 조직(팀·부문 — OrgUnit 경로 파생)·**권한 그룹명**(2026-08-09 ⑦ — 구 orgRole 대체. MCP `whoami` 응답 동일 변경, PRD-host·eval 반영은 host 트랙 소유) — MCP `whoami` 도구(PRD-host FR-AI-16)와 동일 서비스. `GET /api/me/account`는 계정 상세(email·phone·notifPrefs — 프로토타입 내 계정 모달 대응)
- H1-2 When `PUT /api/me/profile {name, email, phone}` Then `200` + AuditLog UPDATE. email은 로그인 ID — 타 사용자와 중복 시 `409 DUPLICATE_EMAIL`
- H1-3 Given 현재 비밀번호 일치·새 비밀번호 8자 이상 When `PUT /api/me/password {current, newPassword}` Then `200`(해시 저장) / 불일치·형식 오류 Then `400`
- H1-4 When `PUT /api/me/notif-prefs {progress, project, org, weekly}` Then `200` — 알림 적재·푸시 시 수신자 설정 필터로 적용(F1-5)

## 7. API 계약 (공통 규약)

- **인증**: `Authorization: Bearer <JWT>` 필수, 없으면 `401`. (`/mcp` 경유 호출도 사용자 토큰 패스스루 — 별도 서비스 계정 없음, 구조 원칙 4)
- **JWT 정책 (2026-08-06 확정)**: access **1시간** · refresh **14일**(사용 시 회전 — `POST /api/auth/refresh`). 2주 이상 미사용 시 재로그인. 구현 노트 §1의 BFF 위임 토큰(5분)과 층 구분
- **응답(공통 봉투 — 2026-08-22 변경, 결정 기록)**: 모든 응답이 `{success, data}` 또는 `{success, error}` 한 형태다. 목록은 `data`에 page 봉투(content,page,size,totalElements,totalPages)가 들어가고(바깥=성공/실패, 안쪽=페이지 메타), 에러 본문은 `{code,message,field,traceId}` 그대로다. 본문 없는 성공(삭제·읽음 처리)은 **204가 아니라 200 + `{success:true}`** — 호출자가 상태 코드로 응답 형태를 먼저 갈라야 하는 상황을 없앤다. **유일한 예외는 `GET /api/auth/jwks`**: RFC 7517이 형태를 정한 표준 문서라 봉투로 감싸면 표준 디코더가 읽지 못한다. (구 계약: 단건=원본 / 목록=page 봉투 / 에러만 봉투)
  - **`/mcp` 어댑터는 영향 없음** — 어댑터는 HTTP가 아니라 애플리케이션 서비스를 직접 호출한다(구조 원칙 3).
- **에러 code는 `ErrorCode` 열거가 유일한 정의** (2026-08-22) — 아래 표가 곧 그 열거이고, HTTP 상태도 거기 함께 있다. 코드를 문자열 리터럴로 만들지 않는다.
- **단건 응답은 `version`을 포함한다** — 동시성 제어 및 MCP 도구 계약(PRD-host FR-AI-10: 프로젝트 상세의 version 반환)의 전제.
- **상태코드**: 200 조회·수정·**본문 없는 성공(삭제·읽음)** / 201 생성 / 4xx 에러. **204는 쓰지 않는다**(2026-08-22 공통 봉투) — 본문 없는 성공도 `{success:true}`를 실어 형태를 하나로 유지한다.
- **동시성**: 본문에 `version`, 불일치 시 `409 STALE_VERSION` → reload-and-retry.
- **페이징**: `?page=0&size=20&sort=field,desc` · **가시성**: 조회 선필터, 범위 밖 `404`(은닉 — 권한/부재 비구분).

| code | HTTP | 의미 |
|------|------|------|
| VALIDATION_ERROR | 400 | 입력 형식 오류 |
| UNAUTHENTICATED | 401 | 토큰 없음/만료 |
| FORBIDDEN | 403 | 권한 없는 행위 (구 `FORBIDDEN_FIELD` 개명 — 2026-08-06, 프로토타입 미사용 확인) |
| NOT_FOUND | 404 | 없음/가시성 밖 |
| DUPLICATE_* / NOT_COMPLETED / INVALID_TRANSITION / PROJECT_COMPLETED / PROGRESS_INCOMPLETE / NOT_IN_PROGRESS / IN_USE | 409 | 중복·상태 위반·전이 위반·사용 중 삭제 거절 (A2-8·A7-2·A7-4 · IN_USE는 E3-3·E4-3·E5-4 — 2026-08-09 · **NOT_IN_PROGRESS는 A2-9 — 2026-08-22** · `DUPLICATE_*`에 `DUPLICATE_EMAIL`(E2-1·H1-2)·`DUPLICATE_ROOT`(회사 root 중복 — 2026-08-22)가 든다) |
| STALE_VERSION | 409 | 동시 수정 충돌 |
| REF_NOT_FOUND / PM_REQUIRED / MULTIPLE_PM / INVALID_ROLE / IMMUTABLE_PERMISSION / IMMUTABLE_GROUP / IMMUTABLE_ACCOUNT | 422 | 참조 대상 없음 · 역할 구성 위반 · 고정 대상 변경 시도 (A1-4·A1-6·A6-7·A8-4 · E5-3·E2-5는 2026-08-09 — `MULTIPLE_PL`은 2026-08-06 삭제) |

```
GET/POST    /api/projects              GET/PUT/DELETE /api/projects/{id}
            # 목록 ?phase=SALES|SOLUTION (status 파생 필터 — §5) · 단건 응답에 파생 필드 phase 포함 (v2.4)
PUT         /api/projects/{id}/progress        # 2단계: confirmed=false 요약 → true 커밋 (US-A2)
PUT         /api/projects/{id}/pm                  # PM 교체 (US-A6 A6-1)
PUT         /api/projects/{id}/roles               # 프로젝트 역할 지정·해제 {personId, role} (US-A6 A6-3)
GET/PUT     /api/projects/{id}/permissions         # 프로젝트별 권한 매트릭스 조회·조정 (US-A8 — 기본값은 상위 PRD §4-2 표)
GET         /api/projects/{id}/audit               # 프로젝트별 변경 이력 (US-G2 — 가시성 범위. 통합 /api/audit는 그룹 "사용자/조직/권한 관리" 전용 — G1-3)
POST        /api/projects/{id}/complete            # 완료 처리 {version} — 진행률 100% 전제 (US-A7)
POST        /api/projects/{id}/reopen              # 재개 {version} — 완료→진행중, progress=90 (US-A7)
GET/POST    /api/projects/{id}/assignments     PUT/DELETE /api/assignments/{id}
GET         /api/utilization?month=&personId=&orgUnitId=&overbooked=    # orgUnitId = subtree 집계 (구 teamId — 2026-08-09 ⑧) · 응답에 team·division 포함 (C1-6 — 2026-08-11)
POST        /api/projects/{id}/handover        # 계약 필수 정보 포함 — Contract+Site 생성 (US-D1, v2.4)
GET/POST    /api/maintenance/contracts         GET/PUT /api/maintenance/contracts/{id}    # US-D2·D4 — 목록 ?keyword= 계약명·계약사·사이트명 부분 일치 (D4-1 — 2026-08-11)
GET/POST    /api/maintenance/contracts/{id}/sites    PUT /api/maintenance/sites/{id}      # 사이트·담당 엔지니어 (D2-4)
GET/POST    /api/maintenance/issues            PATCH /api/maintenance/issues/{id}         # US-D3 — 구 /maintenances/{id}/logs 대체 (MCP list_maintenance_logs 접점: 확인 완료 2026-08-06)
POST        /api/maintenance/issues/{id}/comments    # append-only (D3-3)
GET/POST    /api/people    PUT/DELETE /api/people/{id}    PUT /api/people/{id}/org-unit    # 그룹 부여는 PUT /people/{id} (E2-2)
GET/POST    /api/org-units    PUT/DELETE /api/org-units/{id}              # 조직 트리 (US-E3 — 구 GET /api/teams 대체)
GET/POST    /api/grades       PUT/DELETE /api/grades/{id}                 # 직급 관리 (US-E4)
GET/POST    /api/permission-groups    PUT/DELETE /api/permission-groups/{id}    # 권한 그룹 (US-E5 — 관리자 그룹 systemFixed)
GET /api/audit (그룹: 사용자/조직/권한 관리)
GET /api/me    GET /api/me/account    PUT /api/me/profile    PUT /api/me/password    PUT /api/me/notif-prefs
POST /api/auth/login (email + password)    POST /api/auth/refresh (refresh 회전 — JWT 정책)
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
| `ProjectCompleted` | project | notification | 완료/이관 안내 (발행 시점: `/complete` 커밋 후 — US-A7) |
| `ProjectReopened` | project | notification | 완료 지연 알림 회수 (F3-3) |
| `MaintenanceHandedOver` | maintenance | notification | 이관 완료 알림 (D1-1 — 계약·사이트 생성은 동기·원자적, 알림만 fan-out) |
| `MaintenanceIssueRegistered` | maintenance | notification | 이슈 담당자(assigneeId)에게 알림 (D3-1 — "누가 뭘 담당하는지" 고통의 직접 해소) |

발행=트랜잭션 커밋 후(`AFTER_COMMIT`) · 신뢰성=Modulith Event Publication Registry 재시도 · 재계산 멱등.

## 9. 비기능 요구사항

성능: 조회 P95 < 1초 · 가동률 반영 < 2초 · 알림 SSE 즉시 푸시(재연결 복구 포함) · 권한 서버 최종판정 · 비밀번호 해시 · 낙관적 락 · 감사 append-only·source 구분 · 도메인 순수 단위테스트 · Modulith 경계 검증 · compose 로컬 기동 · 1요청=1트랜잭션 · 앱 무상태 재기동 복구

## 10. 구현 상태 (EPIC 기준 — 2026-08-24 실측)

**구 PMS-M0~M6 순차 라벨을 폐기하고 EPIC 기준 상태표로 대체했다** (2026-08-23 — 결정 기록). 폐기 근거는 라벨이 실제 진행과 맞지 않게 된 것이다: M6(프론트)이 M3~M5보다 먼저 끝났고(2026-08-22 실연동 재작성), M1의 "person + 인증"은 `auth` 모듈 분리로 두 단위가 됐고, M3·M5는 로직 없이 골격만 선 상태다. 순서를 지키지 못한 계획은 진행 상황을 숨긴다 — 대신 **어느 EPIC이 어디까지 서 있는지**를 원장으로 둔다. 구 라벨을 인용하는 문서(§12·PROGRESS 과거 기록)는 그 시점의 기록으로 읽는다.

상태 표기: **실구현** = AC 테스트 초록 · **골격** = 라우트·권한 판정은 있고 로직이 `501 NOT_IMPLEMENTED` · **미착수** = 라우트 없음.

| EPIC | 상태 | 남은 것 |
|------|------|---------|
| **A 프로젝트** | 대부분 실구현 — A1 생성 · A2 진척률 2단계 · A3 조회·가시성 · A4 삭제 · A5 수정·상태전이 · A6-1·2·4·5 PM 교체 · A7 완료·재개 | **A6-3·A6-6·A6-7**(`PUT /projects/{id}/roles`) · **A8 전체**(`GET`/`PUT /permissions` — A8-5·A8-6이 진척률·배정 판정에 침투) · `?phase=` 목록 필터 |
| **B 인력 배정** | 실구현 — B1-1·B1-2·B1-4 · B2-1 종료 | **B1-3**(커밋 후 가동률 재계산)은 C가 서기 전엔 성립하지 않는다 |
| **C 가동률** | **구현 완료** (2026-08-23 — `GET /api/utilization`) · **루트 계약 승격 완료** (2026-08-24) | 없다. C1-1~C1-6 전부 — 산식·분모(그 달 `Capacity` 우선)·모집단(진행중 배정 · 집계만 billable)·과부하(기본>100)·소속 동봉. `/mcp`가 붙을 면도 섰다: `UtilizationLookupService`(+`UtilizationScope`·`UtilizationBrief`·`OverbookedBrief`) — **chat의 `scope=MY_TEAM`·`DIVISION`을 도메인이 유도한다**(웹의 `?orgUnitId=`와 같은 조회 조건으로 옮기므로 두 경로의 답이 같다). 남은 것은 **어댑터 배선**(MCP 담당) |
| **D 유지보수** | **조회 실구현**(2026-08-23) — 모듈·엔티티 4종·Flyway V9·시드 적재(계약 105·사이트 157·연락처·이슈 14) · D4-1 목록(keyword 3종 매칭) · D4-2 상세 · D4-3 전사 공개 · **D3-4 이슈 조회**(미배정 필터 포함) · `IssueComment` 표·조회(적재 0건 — 시드에 본문 없음) | **쓰기 전부**: D1 이관(§5에 완료→유지보수중 전이가 없어 `Project`에 전용 메서드 신설 필요 + 모듈 방향 결정) · D2 계약·사이트 등록/수정 · D3-1~D3-3 이슈 등록·처리·코멘트 |
| **E 조직·사용자** | 절반 실구현 — E2-1 등록 · E2-3 비활성 · E2-5 시스템 계정 보호 · E3-1 생성 · E3-3 `IN_USE` 삭제 거절 · E3-4 subtree 가시성 · E2-4 플래그 403 | **골격 5종**: E1-1 소속 이동 · E2-2 수정 · E3-2 개명 · E4 직급 3종 · E5 권한 그룹 3종 |
| **F 알림** | **골격** (`GET /api/notifications` · `PATCH /{id}/read` → 501) | F1 전부 · **SSE 라우트 미착수**(토큰 마스킹과 한 묶음) · **스케줄러 F2·F3 미착수** |
| **G 감사** | 실구현 — G1-1 자동 기록 · G1-2 append-only 구조 · G2-1 `projectId` 채움 · **G1-3 `/api/audit`** · **G2-2 `/projects/{id}/audit`**(2026-08-23) | 없음. 두 뷰는 같은 테이블의 필터 차이이고 정렬은 호출자가 뒤집을 수 없다(이력은 시간 순서가 의미의 일부) |
| **H 내 계정** | 부분 — H1-1 `GET /api/me` 실구현 | `GET /api/me/account` · H1-2 profile · H1-3 password · H1-4 notif-prefs(**F1-5의 선행**) 전부 미착수 |

**횡단 기반 (EPIC 밖)**

| 항목 | 상태 |
|------|------|
| 모듈 경계 (§3 6종) · 3계층 · Flyway V1~V7 · 단일 스키마 | 실구현 — `ModularityTest`·`LayerRuleTest` |
| 인증 체인 (자체 로그인·JWT·JWKS) | 구현 완료, **기본 OFF**(`pms.auth.enabled`) — 호출자는 `X-Caller-Person-Id` 헤더 |
| 응답 봉투 §7 · `ErrorCode` 열거 | 실구현 |
| 시드 적재 | **조직·직급·권한 그룹·인원·User만**(`seed_org_proten.sql`). **`projects.json` 382건·`maintenance.json` 미적재** — A·C·D의 실데이터 전제이자 `/mcp` project port의 전제 |
| 프론트엔드 (`frontend/`) | 실연동 TS 재작성(2026-08-22) + **미연동 화면 5종 연결 완료(2026-08-24)** — 가동률(C) · 유지보수 계약 목록/상세·이슈 목록(D 조회분) · 통합 감사 로그(G1-3) · 프로젝트 이력 탭(G2-2). 구현된 라우트만 사용한다는 원칙은 그대로다: 알림(F1-3 501)과 유지보수 쓰기(D1·D2·D3-1~3 라우트 없음)는 화면에 없다. **부록 A와의 차이 1건**: 통합 감사 로그를 `/settings` 3탭이 아니라 별 항목으로 뒀다(§12) |
| `/mcp` 어댑터 | **이 앱의 8번째 모듈**(2026-08-23 재승격 — MCP 담당 소유, §3). **의존 방향 확정: `mcp` → 도메인 루트 한 방향**(구조 원칙 3 — 어댑터가 애플리케이션 서비스를 호출한다). 각 도메인이 자기 모듈 루트에 조회 계약을 올리고 어댑터는 그것만 부른다 — port 인터페이스를 `mcp` 루트에 두고 도메인이 구현하던 M0 방식은 폐기했다. 도구 8종 중 **6종 실연결**, 가동률 2종은 계약이 선 뒤(2026-08-24) 배선 대기 |

## 11. 완료 정의 (Definition of Done)

- ☐ 모든 EPIC의 AC가 테스트로 구현되고 초록 (`bash scripts/verify.sh pms` 통과)
- ☐ Spring Modulith 경계 위반 테스트 0건 (`ModularityTest` — 순환·internal 접근)
- ☐ 3계층 방향 위반 0건 · `jakarta.persistence`는 `service/entity`·`repository`에만 · 웹 관심사는 `controller`와 common에만 (`LayerRuleTest`) — 2026-08-21 재구축으로 구 "domain 레이어에 Spring/JPA import 0"을 대체한 항목이다(§0)
- ☐ `501 NOT_IMPLEMENTED`를 던지는 자리 0건 — 골격이 남아 있으면 그 EPIC은 미완이다
- ☐ 시드 데이터(44명·382프로젝트)로 "수주→배정→가동률→완료→이관→이력" 한 줄기 시연 성공
- ☐ 가동률 예시(A0.5+B0.7, coeff1.2 → 기본120/보정144) 테스트 검증
- ☐ `docker compose up` 한 번으로 전체 기동 + 부록 B 시드 자동 적재

## 부록 A. 프론트엔드 화면 명세

에이전트는 아래 라우트·요소를 그대로 구현한다(`frontend/` 프로토타입 재연동 기반). 디자인은 단순·표준(사이드바+콘텐츠)으로 하되 구성 요소는 임의로 빼지 않는다.

| 라우트 | 화면 | 필수 요소 | 접근 |
|--------|------|-----------|------|
| `/login` | 로그인 | **email**/password 폼(계정 ID = 이메일 — 2026-08-02 확정) · 실패 메시지 · JWT 저장 | 전체 |
| `/` | 홈 대시보드 | 내 프로젝트·가동률 요약·최근 알림 (프로토타입 구성 승격) | 로그인 사용자 |
| `/people` | 인력 | 인원 목록(팀 필터·검색) · 상세(참여 프로젝트·가동률) | 가시성 범위 |
| `/settings` | 설정 (2026-08-09 ⑤⑦⑧ — 3탭, 프로토타입 3차 반영 구조) | **[사용자 관리]** 사용자 CRUD(US-E2 — 그룹 부여 포함)·시스템 계정은 수정/삭제 비활성(E2-5) · **[조직 관리]** 조직 트리(좌 — US-E3: +하위/개명/삭제, 노드별 인원·프로젝트 수) + 직급 관리·권한 그룹 관리(우 — US-E4·E5: 그룹 행 = 뱃지·설명·n명·[권한 ▾] 펼침(가시성 select+기능 토글)·[수정]·인원 0일 때만 [삭제], 관리자 그룹은 버튼 비활성) · **[감사 로그]** 통합로그 조회(G1-3) | 그룹: 사용자/조직/권한 관리 |
| `/projects` | 프로젝트 목록 | **phase 탭(영업/솔루션 — status 파생, v2.4)** · 상태·제품군(solution) 필터 · 이름 검색 · 페이지네이션 · (생성 권한자) 등록 버튼 · 완료 건은 "이관 대기" 뱃지(솔루션 탭 잔류) | 가시성 범위 |
| `/projects/new` | 프로젝트 등록 | 입력 항목 폼 + 참여자별 role(**PM/PL/참여자**) 선택, PM 1명 필수 · 수행형태 3종(원격·상주·부분상주 — 2026-08-09 ③⑥) · 422/409 오류 표시 | 그룹: 프로젝트 생성 |
| `/projects/:id` | 프로젝트 상세 | 기본정보 · 상태 뱃지 · **(PM·PL) 상태 전이 버튼 — 다음 한 칸만(계약대기→수주확정→진행중) + 확인 카드. 되돌릴 수 없으므로 조회 화면에서 확인과 함께 수행하고, 정보 수정 폼에서는 status를 다루지 않는다(2026-08-22 결정)** · 진행률(권한 시 수정 — **100% 저장만 확인 모달, 그 외 1클릭** 2026-08-09 ①. 서비스·MCP 2단계는 무변경) · 배정 목록(**역할 뱃지 PM/PL/참여자**) · lastEditedBy/At · (PM) PM 교체·PL 지정 UI · (배정 인원, 100% 시) **완료 처리 버튼** · (배정 인원, 완료 시) **재개 버튼**(US-A7) · (PM, 완료 시) 이관 버튼 · (PM) **권한 패널**(역할×기능 토글 매트릭스 — US-A8. 고정 셀은 잠금 표시, 기본값과 다른 셀은 커스텀 뱃지, **기본값 복원** 버튼. 완료·재개는 한 토글) · **이력 탭**(프로젝트 스코프 변경 이력 — US-G2, 가시성 범위 전체. lastEditedBy/At의 상세판) | 가시성 범위 |
| 〃 배정 패널 | 인력 배정 | 배정 추가(사람 검색→기간·월별 M/M — **레이블·헬프에 "실투입 계획(계약 배분 아님)" 명시**, B1-5) · 종료 처리 · 409 표시 — 프로토타입의 월별 upsert UI는 기간 모델 API(§7)로 재연동 시 조정(2026-08-02 기간 모델 확정) | PM(§6 태그 규칙 — ADMIN 치환 포함) |
| `/utilization` | 가동률 대시보드 | **내 가동률 카드**(2026-08-24 신설 — 아래) · 월 선택 · 팀 필터 · 기본/보정 표 · 과부하(**기본**>100% — 2026-08-10) 강조 · 과부하만 보기 | 가시성 범위 |
| `/maintenance` | 유지보수 계약 목록 (탭 원천) | 상태·계약사·종료일 필터 · (등록 권한자) 계약 등록 버튼 — 시트 대체 (v2.4) | 로그인 사용자(전사 — D4-3) |
| `/maintenance/contracts/:id` | 계약 상세 | 계약 정보(원 프로젝트 링크, 없으면 미표시) · 사이트 목록(솔루션 버전·서버스펙·**담당 엔지니어**) · 연락처 · 이슈 이력 | 로그인 사용자(전사) |
| `/maintenance/issues` | 이슈 목록 | 상태별 뷰(접수/처리중/대기/완료) · **담당자·고객사 컬럼 상시 노출** · 미배정/내 담당 필터 · 이슈 등록 — 구 게시판 대체 (v2.4) | 로그인 사용자(전사) |
| 공통 헤더 | 알림 뱃지 | 미읽음 수 — **SSE 즉시 갱신**(⑥, 프로토타입의 구독 로직 재사용), 재연결 시 미읽음 재조회 · 클릭 시 목록·읽음 처리 | 로그인 사용자 |

**`/utilization` 내 가동률 카드 (2026-08-24 신설 — 사용자 결정)**: 목록 위에 화자 본인의 값을 `?personId=`로 따로 물어 놓는다. 이유는 실측된 공백이다 — 집계는 `billable=false` 인원을 모집단에서 빼므로(C1-5) **지원 조직 인원은 대시보드를 열면 빈 목록을 본다**(AX사업기획부·관리•마케팅부·대표. 실측: 윤종헌은 집계 0명인 화면을 보지만 본인은 182%다). 개인 지정 조회는 그 규칙과 무관하고 서버가 이미 값을 내주는데, 화면에 그것을 볼 자리가 없었다. 집계 목록에 본인이 없을 때는 **그 이유(C1-5)를 카드가 한 줄로 답한다** — 규칙을 모르면 빈 목록이 고장으로 보인다.

**공통 UI 규칙**: 모든 목록은 로딩/빈/에러 3상태 · `409 STALE_VERSION` 수신 시 "OO님이 먼저 수정했습니다. 최신 내용을 불러올까요?" → 확인 시 재조회(reload-and-retry) · 권한 없는 버튼은 렌더링하지 않되 서버 403 처리는 항상 존재.

## 부록 B. 시드(데모) 데이터 — `reference/seed/` 기준

`docker compose up` 후 자동 적재(또는 seed 프로파일). DoD의 "한 줄기 시연"과 가동률 예시 검증이 이 데이터로 가능해야 한다. v1.0의 가상 시드(10명·5건)는 폐기.

**시드에 있는 것** (`seed_org_proten.sql` 인원 44명 · `projects.json` 382건):

> ⚠ **인원 정본은 `seed_org_proten.sql`(실제 명부)이다** — 2026-08-22 재구축에서 전환했고, 구 `people.json`은 **익명 명부**라 쓰지 않는다(2026-08-23 확인·결정 기록). 두 파일은 같은 id에 다른 사람이 들어 있고 이름이 하나도 겹치지 않는다. **아래와 `docs/evals/eval-cases.md`·`docs/유저_시나리오.md`의 인물 이름은 익명 명부 기준으로 작성된 것**이므로 실제 DB 조회·eval 채점에 쓰기 전에 재매핑해야 한다(미해결 — §12).
- 인원 44명 · 7부문(AI기술연구소·AX기술연구소·AX솔루션사업부·AX사업기획부·MS사업부·관리•마케팅부·대표) · orgRole: ADMIN 1·DIVISION_HEAD·TEAM_LEAD·MEMBER
- 직급계수(gradeCoeff): 대표이사 2.0 · 부사장 1.8 · 상무 1.7 · 이사 1.6 · 수석 1.5 · 책임 1.2 · 선임 1.0 · 주임 0.8 · 수습 0.5
- 프로젝트 382건: 완료 319 · 진행중 34 · 수주확정 19 · 계약대기 10. 필드: client·solution·engagement·contractMm·기간·progress·managerId·assigneeIds(305건 보유)

**시드에 없는 것 → 적재 정책**:
- `User` 계정 — **확정(2026-08-02)**: 로그인 ID = 시드 email 전체, 초기 비밀번호 `proten1!` 해시 저장(프로토타입 데모 계정 4종과 정합), 최초 로그인 후 변경 안내
- **기본 권한 그룹 4종 + 매핑 — 확정(2026-08-09 ⑦)**: 관리자(scope=전사 · 플래그 4종 전부 on · **systemFixed**) · 부문장(부문 · 프로젝트 생성+계약 관리) · 팀장(팀 subtree · 프로젝트 생성+계약 관리) · 팀원(본인 · 플래그 없음). 시드 orgRole {ADMIN→관리자, DIVISION_HEAD→부문장, TEAM_LEAD→팀장, MEMBER→팀원} 매핑 적재 — 기본 그룹에서 판정 결과는 일반화 이전과 완전 동일
- **시스템 관리자 계정 — 확정(2026-08-09 ④)**: `admin@proten.co.kr` (관리자 그룹 · `Person.system=true` · billable=false · 초기 비밀번호는 계정 규칙 동일) — 시드 44명과 별도로 1건 생성. 인력·가동률·배정 목록 제외
- **`OrgUnit` 트리 — (2026-08-09 ⑧)**: 시드 부문·팀 2단을 회사(root) 아래 그대로 적재 — 임의 깊이는 운영 중 US-E3로 확장(시드에 3단 이상 데이터 없음)
- **engagement OFFSITE 32건 → REMOTE 일괄 변환 — 확정(2026-08-09 ③⑥)**: 적재 시 변환(원본 JSON은 무수정)
- **`status=완료`인데 `progress<100`인 13건 → 100 일괄 보정 — 확정(2026-08-23)**: 적재 시 보정(원본 JSON은 무수정 — OFFSITE와 같은 형태). 시트에서 상태 칸만 완료로 바꾸고 진척률 칸을 갱신하지 않은 자국이고, 완료의 전제가 진척률 100%다(AC A7-2 — 상태 머신을 실제로 통과시켜 적재하므로 보정 없이는 `complete()`가 거절한다). 상태를 진행중으로 내리는 쪽은 위 "완료 319 · 진행중 34" 기대값을 깨뜨리므로 택하지 않았다
- **유지보수 적재 시 보정 3종 — 확정(2026-08-23)** (원본 JSON 무수정, 보정 원문은 계약 `note`에 남긴다): ①계약 상태 **자동연장·갱신 2건 → 유지**(모델·MCP 도구가 4종이고 둘 다 `sheet="2026 계약"`의 살아있는 계약이다. 늘리면 도구 description·목업 검증 문구·eval이 한 세트로 따라온다 — 상위 PRD §6) ②**잘못된 날짜 1건 → 그 달 말일**(계약 #72 `endDate="2027-11-31"` — 11월은 30일까지. 31을 적은 사람은 "그 달 말"을 뜻했고, null로 두면 연·월까지 잃어 종료일 정렬·`endedBefore` 필터에서 빠진다) ③계약 레벨 `serverSpec` **→ 사이트로 내림**(접두가 사이트명과 겹치면 그 사이트, 사이트가 하나면 그 사이트). 그 밖에 `salesRep`은 이름 문자열이라 person에 **정확히 한 명일 때만** 물어 id로 바꾸고(동명이인이면 비운다), 계약 단위 `clientRep`은 사이트가 하나뿐인 계약에서만 그 사이트의 고객사 연락처로 붙인다
- **유지보수 계약·이슈 id = 시드 원본 번호 — 확정(2026-08-23)**: 계약은 `id`(1~105), 이슈는 **`no`**(230~496)를 그대로 쓴다(`@GeneratedValue` 없는 명시 id — `Person`·`OrgUnit` 선례. 하드 삭제가 없으므로(D2-2) 새 행은 `max(id)+1`). 두 이유가 있다. ①**eval 앵커가 우연에 기대지 않게** — C-01~C-03이 계약 **101**(한국거래소)을 앵커로 쓰는데, identity 생성이면 시드 파일에 한 줄이 끼거나 순서가 바뀌는 순간 조용히 어긋난다 ②**`list_maintenance_logs`의 id 해석이 성립하게** — 도구는 "계약 id **또는** 이슈 id"를 한 파라미터로 받고 계약을 우선 해석한다(목업과 동일). identity로 이슈에 1~14를 주면 계약 id 1~105에 전부 가려 **ISSUE 갈래에 도달할 수 없다**(2026-08-23 실측·해소). 원본 번호는 두 공간이 겹치지 않아 그 모호성이 사라진다
- 배정의 **월별 M/M — 부여 규칙 확정(2026-08-10, 배정 M/M=실투입 계획 재정의와 함께)**: ①**실무자** = PM 외 참여자, **없으면 PM 본인**(시드 실측: 진행중 34건 중 다수가 assigneeIds=[PM]뿐인 1인 프로젝트 — 이때 PM이 실무자) ②실무자 배정의 각 월 M/M = `contractMm ÷ 프로젝트 개월수 ÷ 실무자수`(소수 2자리 반올림, 개월수 = max(1, round(기간일수/30.4))) — 실투입 데이터가 없는 시드에서의 근사일 뿐, 운영 입력은 PM의 실투입 계획(B1-5) ③실무자가 따로 있는 프로젝트의 PM 배정 = **0**(A6-7 기본값 그대로 — 체크 역할은 부하 없음) ④상한 없음: 합이 100%를 넘는 달이 자연 발생해 오버부킹 시연 확보(아래 검증 케이스)
- 배정의 **role** — **확정(2026-08-03)**: `managerId` → `role=PM`(382건 전부, 누락 0건), 나머지 `assigneeIds` → `role=PARTICIPANT`. **`role=PL`은 아무도 지정하지 않는다** — 시드에 근거 데이터가 없어 임의 지정 금지. 필요 시 진행중 34건 중 참여자 2명 이상인 9건에 한해 수동 지정
- `Capacity`(월별 가용 MM) — 기본 1.0 적재로 시작
- `Person.billable` — **false 목록 확정(2026-08-10 · 실제 명부로 재앵커 2026-08-24)**: **조직 단위 2곳 = 경영관리팀 전체 4명**(대표 박재완 포함 — 구 문서의 "프로텐(대표 신현랑)"과 "관리•마케팅부(경영관리)"가 실제 명부에서는 이 한 팀이다) **· AX사업기획부 전체 6명**(부문 직속 1 + AX영업팀 2 + AX기획마케팅팀 3), 계 **false 10명 + 시스템 계정 1 / true 33명**(구 문서 "34명 true"는 시스템 계정을 true로 세던 값이다). 근거: 세 부문은 진행중 프로젝트 수행이 거의 없다. **예외는 1명이 아니라 5명이다**(2026-08-24 실측 — 장대근 24건·윤종헌 12·천용우 9·진희원 7·김주선 5): 지원조직 인원의 간헐 참여는 개인 지정 조회로 보이고 **집계는 `billable=true`만 세므로 수치에 영향이 없다** — 플래그는 조직 기준을 유지한다. AI·AX연구소·MS사업부는 수행 이력 있는 딜리버리 조직이라 true — 소속원 0%는 "실제 여유"로 의미 있는 0(플래그는 운영 중 개인 단위 수정 가능)
- 유지보수 데이터 — **변환 완료(2026-08-10)**: `reference/seed/maintenance.json` — "2026년 기술지원 및 유지보수" 시트 3개 섹션 전량 전사(**마스킹 없음**, 2026-08-06 확정): 계약 105건(**2026 계약 57 · 신규 예정 21 · 미체결·종료 27**) · 사이트 157개(가온아이 1계약 45사이트 정합) · 연락처는 사이트별 구조화. 시트의 비날짜 계약일("진행중"·"자동연장" 등)은 `contractDateNote`로 보존, 원문 모순(비즈웰 "총 8개" vs 나열 9개 등)은 원문 유지+주석. **engineerId 매핑 규칙**: 유지보수 운영 조직 = CS사업팀 — 2026 계약 섹션의 사이트만 실무 3명(**배성수 26·김민환 27·남진식 28** — 구 익명 명부의 노도온·한은율·송수람. id는 그대로이고 이름만 실제 명부로 재앵커했다, 2026-08-24) 라운드로빈(37/36/36 — 실측 일치), 신규 예정·종료 사이트는 null(미배정 필터 시연 겸용). **이슈 14건 동봉**: 구 이슈 게시판("검색엔진_유지보수") 목록 전사 — type{장애·문의·요청} 분류, 전부 완료 상태, 작성 엔지니어는 **시트 원본 이름 그대로 실인원에 붙는다 — 남진식(28) 7건·배성수(26) 7건**(2026-08-24 교정). 변환 당시에는 작성자를 구 익명 명부로 매핑했는데(남진식→노도온=26 · 배성수→송수람=28) 인원 정본이 실제 명부로 바뀌며 **id 26이 배성수, 28이 남진식**이 되어 두 사람이 서로 뒤바뀌어 있었다. 둘 다 CS사업팀 실인원이라 화면에 그럴듯하게 보이는 것이 함정이었다 — `maintenance.json`의 이슈 `engineerId`를 교환하고, 이미 적재된 DB는 Flyway **V10**이 수렴시킨다(로더가 계약이 있으면 적재 전체를 건너뛴다). **D3 이슈 쓰기가 열리기 전이라 모든 행이 시드 적재분인 지금이 이 정정의 마지막 시점이다**. 이슈 태그(전력거래소·한국거래소)는 계약 목록과 미연결 실데이터 그대로 — **적재 시 계약 링크 기준 = 계약명·계약사·사이트명 3종 일치(사이트명 포함 — 2026-08-14 확정, PROGRESS 결정 기록)**: D4-1·`search_maintenance`의 keyword 매칭 범위와 동일. 시드 실측(host 2026-08-12): '한국거래소' 이슈 7건의 유일 후보 계약 101(지수방법론)은 **사이트명으로만** 일치 — 계약명·계약사 기준만이면 이슈 14건 전량 무연결(eval C류 기대값 성립 불가). **이관 시연 대상 = 명화공업**(신규 예정 섹션에 계약 실재 + `projects.json` 수주확정 1건 — WS-01 한 줄기 앵커와 동일 인물, 수주확정→진행중→완료→이관 경로 시연)

**검증 케이스**:
- 가동률 단위테스트는 고정값: 배정 0.5+0.7, 가용 1.0, coeff 1.2(책임) → 기본 120% / 보정 144% (AC C1-2 — 2026-08-10 산식 재정의 반영)
- 시드 스모크용 오버부킹(**기본**>100): 위 M/M 부여 규칙으로 2026-08 기준 자연 발생한다. **실제 명부 실측(2026-08-23 — `ProjectSeedLoadIntegrationTest`가 고정)**: 규칙만 적용하면 **이현창(수석) 191% · 윤종헌(주임) 182% · 김경민(선임) 133%** 3명이고, **C1-5(billable=false 집계 제외)를 적용한 `overbooked` 목록은 이현창·김경민 2명**이다 — 윤종헌은 AX영업팀(AX사업기획부 하위)이라 모집단에서 빠진다. 구 기록의 "3명(남민준 190·손윤린 182·전세아 133)"은 **익명 명부 기준 시뮬레이션**이었다: 수치 191/182/133은 그대로 재현되지만 이름과 billable 판정이 실제 명부에서 달라진다(남민준 190/191의 반올림 ±1%p 기록은 유효)

## 12. 미해결 / 결정 필요

**에이전트는 임의 구현하지 말고 질문할 것**

- **가동률 집계와 과거 월의 퇴사자 (2026-08-23 등재 — 미해결)**: 집계 모집단을 **재직자만**으로 정했는데(사용자 결정 — "지금 우리 조직 가동률"에 퇴사자를 세면 집계가 틀어진다), 그러면 **지난달을 오늘 조회하면 그 사이 퇴사한 사람의 배정이 빠진다**. person 계약이 이미 두 규칙을 갖고 있는 것이 이 문제의 표면이다: `findPersonIdsInSubtree`는 재직자만이고 `findProfiles`는 "지난달 가동률은 그때 재직 중이던 사람으로 계산된다"며 비활성을 포함한다. 대안은 모집단을 "재직 ∪ 그 달 배정 있음"으로 넓히는 것인데 규칙이 하나 늘고 "재직 0% 행"과 구분이 필요하다. eval 36케이스는 현재 월만 물으므로 **G1을 막지 않는다**
- **`?orgUnitId=`가 없는/가시성 밖 조직일 때 (2026-08-23 등재 — 미해결)**: 현재 **빈 목록**이다(404가 아니다). 조직 자체의 가시성을 물으려면 person이 계약을 하나 더 열어야 하는데 §7에 그 오류 규칙이 없어, 명세 없는 이유로 모듈 경계를 넓히지 않았다(`UtilizationPopulation`에 `// ASSUMPTION:` 주석). 다른 조회는 가시성 밖을 404로 은닉하므로 규칙이 갈려 있다
- **통합 감사 로그의 화면 자리가 부록 A와 다르다 (2026-08-24 등재 — 미해결)**: 부록 A는 `/settings` 3탭(사용자 관리·조직 관리·감사 로그)인데, `frontend/`는 사용자·조직을 `/people`("인력 · 조직")에 두고 있어 **감사만 사이드바 별 항목**으로 얹었다(사용자 결정 2026-08-24). 이미 동작하는 화면 2개를 재배치하는 것은 "비어 있는 것을 채운다"는 그 작업의 성격을 벗어나고 회귀 여지가 생긴다는 판단이다. 해소 방향 둘: ①부록 A를 현행 배치에 맞춰 고친다 ②`/settings`를 신설하고 두 화면을 탭으로 옮긴다. **어느 쪽도 급하지 않다** — 세 기능 다 접근 가능하고 권한 판정(manageOrg 플래그)은 세 곳 모두 서버가 같게 한다
- **시드만 적재한 DB에는 감사 행이 없다 (2026-08-24 실측 — 확인 필요)**: `audit_logs`가 **0행**이다. 구 기록이 "기동 시 시드 적재분은 `source=WEB`이라 `MCP`와 구분된다"고 적고 있었는데(2026-08-23), 실측하면 시드 적재는 감사 행을 **아예 남기지 않는다**. 감사 화면·G1-3 데모는 쓰기를 한 번 해야 내용이 보인다. 의도된 것인지(시드는 "변경"이 아니다) 누락인지 확인이 필요하고, 의도라면 그 문장을 고쳐야 한다
- **인물 이름 재매핑 (2026-08-23 등재 — 미해결)**: 인원 정본이 `seed_org_proten.sql`(실제 명부)로 바뀌었는데 **기획 문서의 인물 이름은 구 익명 명부(`people.json`) 기준**이다. 두 명부는 이름이 하나도 겹치지 않아, 문서의 이름으로 DB를 조회하면 아무것도 나오지 않는다. 영향 범위: 본 문서 부록 B 검증 케이스(정정 완료) · **`docs/evals/eval-cases.md` 36케이스의 화자·기대값** · **`docs/유저_시나리오.md` 페르소나 8명** · 상위 `PRD.md` §4-1 근거의 인물 언급 · PROGRESS 과거 기록(그 시점의 기록이므로 그대로 둔다). 합계 **194곳**. eval 채점은 이름 대조를 포함하므로 **G1 게이트 전에 반드시 해소**해야 하고, eval·시나리오는 host 트랙 소유라 **공용 결정 기록 경유**다
- ~~**권한 모델 재기술 (pms 담당 결정)**~~ — 2026-08-03 결정 완료(합집합 판정 + PM/PL/참여자 3단, 상위 `PRD.md` §4 재작성 · PROGRESS 결정 기록). ~~MCP 담당 확인 대기~~ — 2026-08-03 확인 완료(PROGRESS 결정 기록)
- ~~**가동률 집계 대상(구 "HQ 제외 여부")**~~ — 2026-08-06 해소: `Person.billable` 플래그(상위 PRD §3·C1-5·부록 B). ~~MCP 담당 확인 대기~~ — 2026-08-06 확인 완료(결정 기록)
- ~~**PL 복수 허용 여부**~~ — 2026-08-06 허용 확정(실무 확인: 다부문 프로젝트에 파트별 리드 실존). A1-7·A6-7 `MULTIPLE_PL` 삭제
- ~~가동률 캐시 테이블~~ — 2026-08-06 미도입 확정(매 조회 계산 — 44명 규모, 성능 고통 시 재론)
- ~~마감 임박 알림 D-N 값~~ — 2026-08-06 **N=7** 확정(F2-1)
- ~~JWT 만료·refresh 정책~~ — 2026-08-06 확정: access 1h · refresh 14일 회전(§7)
- ~~403 에러코드 명칭~~ — 2026-08-06 `FORBIDDEN`으로 개명(프로토타입 미사용 확인 — 에러코드 분기 자체가 없음)
- ~~설정 화면 편집 탭 백엔드 승격~~ — 2026-08-06 승격 안 함 확정 → **2026-08-09 재론·채택으로 대체**(게이트 P 리뷰 결정 ⑤⑦⑧ — US-E3(조직 트리)·E4(직급)·E5(권한 그룹) 신설, PROGRESS 결정 기록)
- ~~유지보수 데모 데이터~~ — 2026-08-06 해소: 시트 실데이터 적재 확정(부록 B). 함께 구 미해결 "프로젝트:Maintenance 1:1 vs 1:N"도 해소 — 프로젝트:계약 1:1(이관) + 프로젝트 없는 계약 존재 → `list_maintenance_logs`의 projectId 단순화 불가 (~~MCP 담당 확인 대기~~ — 2026-08-06 확인 완료, 유지보수 재설계 결정 기록)
- ~~시드 적재 정책 잔여~~ — **전량 해소(2026-08-10)**: 배정 월별 M/M 부여 규칙 · billable=false 팀 목록(가동률 재정의 결정과 함께) · 유지보수 시트→JSON 변환 + engineerId 매핑(`reference/seed/maintenance.json` — 부록 B). 남은 것은 적재 구현 시 수행뿐 — `projects.json`·`maintenance.json`은 아직 미적재다(§10 횡단 기반 표)
- ~~가동률 산식의 배정 M/M 의미(계약 배분 vs 실투입)~~ — **2026-08-10 재정의**(배정 M/M=실투입 계획 · 오버부킹=기본>100 · 보정=단가 가중 ×coeff · PM 하드 제외 미채택 — 상위 PRD §3·C1·B1-5, PROGRESS 결정 기록). ~~MCP 담당 확인 대기~~ — **2026-08-10 확인 완료**(동석 확인 — 결정 기록)
- ~~**M-1 카탈로그 공백 2건의 PMS 측 반영 (2026-08-11 — v2.7)**~~: 가동률 응답 `team`·`division` 동봉(C1-6) · 유지보수 계약 `keyword` 검색(D4-1). ~~MCP 담당 확인 대기~~ — **2026-08-12 확인 완료(양측 합의 성립 — host 반영: 목업 8종화·PRD-host v2.4·시스템 프롬프트 v0.2·eval v1.5)**. 구현 금지 조건 해제 — 구현 시점은 **EPIC C(C1-6)·EPIC D(D4-1)**, 둘 다 §10에서 골격/미착수
- ~~유지보수 이슈→계약 링크 기준 — '이름 일치'에 사이트명 포함 여부(2026-08-12 host 등재)~~ — **2026-08-14 확정: 사이트명 포함**(계약명·계약사·사이트명 3종 — 부록 B 명시화, PROGRESS 결정 기록). eval C류 v1.6 전제 성립, 적재 구현은 **EPIC D와 한 묶음**(§10 — 현재 미착수)
- ~~(2차) MCP 챗봇 PAT 검증 지점~~ → v3에서 M0로 승격: `/mcp` 인증 체인(토큰 패스스루·audience)은 루트 ROADMAP M0 + 구현 노트 소유. 이 문서는 접점(애플리케이션 서비스 계약)만 가진다

---

본 PRD는 구 "PMS — AI 구현용 PRD" v1.0(「PMS 기획서 v2」·「PMS 아키텍처 설계서 v2」 파생)의 v3 현행화판. 충돌 시 이 PRD의 AC를 우선한다. AI 어시스턴트 기능 요구는 `docs/PRD-host.md` 소유.
