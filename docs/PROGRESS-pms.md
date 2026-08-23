# 진행 상태 — pms 트랙 (PMS 담당: 도메인·애플리케이션 서비스·프론트)

> 공용 상태·결정 기록·미해결 이슈는 `PROGRESS.md`. 이 파일은 pms 트랙의
> 다음 작업과 세션 로그만 담는다.

## 온보딩 (처음 시작할 때)

1. 루트 `CLAUDE.md` → `pms/CLAUDE.md` → `docs/PROGRESS.md` → `docs/ROADMAP.md` 순으로 읽기
2. `pms/` 안에서도 `/mcp` 어댑터 모듈은 MCP 담당 소유 — 서비스 계층 API 변경은 공용 결정 기록 경유
3. 구 구현은 `pms-old/`에 참고용 보존 — 게이트 M0 산출물(`/mcp` 어댑터·인증 체인·시드 적재기)이 거기 있다


## 현재 상태 (2026-08-22 — auth 분리 + 모듈 골격 확장)

- **auth 모듈 분리 완료**(사용자 선택 — 공용 결정 기록 2026-08-22 1행): `person/service/impl` 26개 중 **인증 인프라 7개**(AuthKeyConfig·AuthProperties·TokenProvider·NimbusTokenProvider·TokenClaimValidators·PasswordHasher·BCryptPasswordHasher)와 `User`·`UserRepository`·`AuthService`·`AuthController`·`ApiSecurityConfig`를 **`auth/`** 로 옮겼다. PRD §3의 개명 근거("identity는 계정·인증이 범위에서 빠진다")가 코드에서도 성립한다. **순환 회피가 이 분리의 핵심 설계 지점**: 로그인은 인원 활성 여부를 person에 물어야 하고(`PersonDirectoryService.existsActive`) 인력 등록(E2-1)은 계정을 함께 만들어야 해서 서로 부르면 `ModularityTest`가 막는다 → **person이 모듈 루트에 `AccountPort`를 정의하고 auth가 구현**(의존 auth → person 한 방향, 초기 비밀번호·해시는 auth 안에 잔류). `PersonSeedLoader`의 계정 섹션 검사도 이 포트를 탄다
- **골격 신설**(로직 없음 — 미구현 유스케이스는 `NotImplementedException` → **501 `NOT_IMPLEMENTED`**, 각 자리에 `TODO(<AC>)`): **resource**(`GET /api/utilization` · `Capacity` 엔티티 = 월별 예외, 기본값은 `Person.capacity`) · **notification**(`GET /api/notifications`·`PATCH /{id}/read` · 멱등은 `(recipient_id, dedupe_key)` 유니크 제약으로 스키마에 박음) · **EPIC E 쓰기 5종**(E1-1·E2-2·E3-2·E4·E5 — `ReferenceController`를 자원별 `GradeController`·`PermissionGroupController`로 분리) · **감사 조회 2뷰**(G1-3·G2-2). Flyway **V7**(capacities·notifications). `PageResponse`는 원 주석의 예고대로 `common/web`으로 승격
- **골격에서도 권한·가시성 판정은 실구현**: `AuditViewService`(G1-3 — 관리 플래그 없으면 403, 조회 위임 안 함) · `ProjectQueryService.listAudit`(G2-2·G2-3 — 가시성 밖은 403이 아니라 404, 관문은 상세 조회와 같은 `requireVisible`). 403/404는 보안 의미를 갖는 동작이라 나중에 얹을 것이 아니고, 판정이 서 있으면 "플래그 없는 호출자에게 이력이 새지 않는다"를 지금부터 테스트할 수 있다 — 단위 테스트 4건 신설
- **서비스 계약 통합 완료**(사용자 지시 — 공용 결정 기록 2026-08-22 1행): project **9 → 4**(`ProjectQueryService`·`ProjectCommandService`·**`ProjectLifecycleService` 신설**·`AssignmentService`), person **11 → 8**(`PersonService`로 조회·명령·내 계정 통합, `ReferenceQueryService`는 Grade/PermissionGroup에 흡수). cross-module 3종은 소비자가 서로 달라 그대로 둔다. 서비스 반환 타입은 Spring `Page`로 통일하고 §7 봉투는 컨트롤러에서만 씌운다. **`/mcp` 접점 영향**: 어댑터가 바인딩할 project 계약이 4종으로 줄고 진척률 쓰기가 `ProjectLifecycleService.updateProgress`로 이름이 바뀜 — MCP 담당 확인 대상
- **audit 모듈 분리 + 공통 응답 봉투 + ErrorCode + 패키지 정리 완료**(사용자 지시 — 공용 결정 기록 2026-08-22 1행): ①`common/audit` → 최상위 `audit` 모듈. `common`은 모듈에서 제외해 **모듈 6종**. 서블릿 의존은 `common/config/RequestPathResolver`로 내렸다 ②모든 응답이 `{success,data}`/`{success,error}` — 본문 없는 성공은 200 + `{success:true}`, **예외는 `/api/auth/jwks` 하나**(RFC 7517) ③`ErrorCode` 열거가 §7 에러 표의 유일한 정의이고 HTTP 상태도 함께 갖는다 ④`package-info` 6개 제거·`controller/dto/`·`service/impl/scope/`로 묶음. 프런트 `api.ts`는 `unwrap` 한 곳에서 봉투를 벗긴다(tsc 통과)
- **package-info 전량 제거 + 폴더 2차 정리**(사용자 지적 — 공용 결정 기록 2026-08-22 1행): `@NamedInterface`는 실제 Modulith API지만 **기본 규약은 "모듈 루트 = 공개 API"** 여서, 계약을 `service/`에 둔 배치가 package-info를 필요하게 만들고 있었다. 공개 타입을 모듈 루트로 옮겨 **8개 → 0개**. `common`은 모듈에서 제외(ignore 술어) → 모듈 6종. 폴더: `auth/service/impl/token/` · `person/seed/` · `GlobalExceptionHandler` → `common/web`
- **Codex 리뷰 P2 5건 + P3 3건 수정 완료**(공용 결정 기록 2026-08-22 1행): notification 계약을 모듈 루트로 · **골격의 403 선행**(`ScaffoldAuthorizationTest` 신설 — 없는 것은 로직이지 권한이 아니다) · 알림 회수를 **조건부 삭제 한 문장**으로(읽음 경쟁에서 먼저 커밋한 읽음이 이긴다) · 프런트 `unwrap`이 `success`를 검증 · `field` undefined → null 정규화 · 구 204 계약 문서 일괄 정정 · 501 로그를 INFO로
- **부수 결함 1건 수정 — 타입 틀린 요청 값이 500으로 나가고 있었다**: `?month=2026-8-1`, `/api/projects/abc` 같은 요청이 `MethodArgumentTypeMismatchException`으로 전역 핸들러의 catch-all에 걸려 **500**이 됐다(2026-08-22 "매핑 없는 경로 500"과 같은 계열의 구멍이고, 골격 라우트의 바인딩 테스트를 쓰다 드러났다). 400 `VALIDATION_ERROR`(field=파라미터명)로 매핑 + 필수 파라미터 누락도 같이 처리 + 회귀 테스트
- **검증**: `verify.sh pms` PASS — 테스트 **268개**. auth 분리·계약 통합 뒤에도 기존 검증(로그인·시드 관통·인증 ON 경로·프로젝트 관통)이 그대로 통과한다
- **미해결 (공용 결정 필요) — resource가 배정 데이터를 읽을 경로가 없다**: 가동률 분자(Σ 월별 배정 M/M)를 얻으려면 project가 "인원×월 배정"을 내주는 서비스 계약이 필요한데 지금 공개된 것은 `ProjectQueryService`(목록·단건)·`AssignmentService`(쓰기)뿐이다. 배정 엔티티 직접 접근은 모듈 경계 위반이므로 **애플리케이션 서비스 API 추가**로 풀어야 하고, 그것은 공용 결정 기록 경유 사항이라 임의로 만들지 않았다(`UtilizationQueryServiceImpl` TODO)
- **다음 작업:** **EPIC C 가동률** — 계약 2종·조직 id·시드가 다 서 있어 바로 착수 가능하고, 끝나면 `/mcp` 카탈로그 **8종이 완성**된다(`UtilizationTools`가 마지막 503). 그 뒤 EPIC E 쓰기 5종 · D 쓰기(D-b)·이관(D-c) · project 잔여 AC(A6-3·A8) · EPIC F·H. **G1 전 필수 선행**: 인물 이름 재매핑 194곳(PRD-pms §12 — host 트랙 소유 문서)
- **차단 요소:** 없음. 다만 `pms-old/`의 게이트 M0 산출물 승격 시점·방식은 MCP 담당 결정 사항

## 이전 상태 (2026-08-21 — 재구축 · 시드 · 인증 · 감사 기록 · 프론트 실연동)

- **pms 앱 재구축 완료**(사용자 지시 — 공용 결정 기록 2026-08-21 2행): 구 `pms/` → `pms-old/` 이동 후 새 `pms/`를 **도메인별 모듈 + 3계층** 으로 신규 작성. 최종 배치는 `도메인/{controller, service{impl,dto,entity}, repository}` + `common/{config,exception}`(참조 프로젝트 검토 후 사용자 지시 확정)이고, 모듈 밖에 여는 것만 `@NamedInterface`로 명시해 **엔티티·리포지토리가 모듈 경계를 넘지 못함을 `ModularityTest`가 검증**한다. 스키마는 **Flyway 소유·단일 스키마**(`db/migration/V1__init.sql`·`ddl-auto=none`), H2 제거로 DB 테스트 전량 Testcontainers PG. 모듈 `person`(구 identity 개명)·`project`·`common`, **JPA 엔티티 = 도메인 모델**(구 순수 도메인 record ↔ `*Jpa` 매핑 왕복 제거). 구현 AC: person 가시성·인력 조회 전량 + project **A1·A2·A3**. `DomainPurityTest` → `LayerRuleTest`(계층 방향·계약↔구현·영속/웹 격리·엔티티↔dto 7종) 교체. **범위 밖(의도)**: 인증·`/mcp` 어댑터·시드 적재기·AuditLog·도메인 이벤트·A4~A8·EPIC B. verify.sh pms PASS — 테스트 83개(경계 2·계층 7·스모크 1·Testcontainers PG 관통 9 포함)
- **모듈 간 계약**: project → person은 person이 공개한 서비스 인터페이스 3종(`PersonDirectoryService`·`OrgVisibilityService`·`OrgPermissionService`)과 dto만 참조, 연결은 id로만. `/mcp` port 5종이 붙을 자리가 이 서비스 계층이다
- **시드 적재기 완료**(사용자 지시 — 공용 결정 기록 2026-08-21 1행): 사용자가 제공한 사내 조직 SQL을 이 앱 스키마로 변환(`reference/seed/seed_org_proten.sql`) + `PersonSeedLoader`(빈 테이블일 때만 실행·적재 후 참조 정합성 검사). **시드 원본이 가명 44명 → 실 조직 43명으로 바뀌었다** — 조직 17노드(회사 root 신설)·직급 9(계수 부록 B)·기본 그룹 4·인원 43+시스템 계정. 실기동 실측 완료(적재 로그·재기동 멱등·API 가시성). **확인 완료 2건**(2026-08-21 사용자): ①**수습 제거** — 실 조직에 해당자가 없어 폐기하고 원본 직위 그대로 `매니저`(계수 1.0, 유일한 미유도 값)로 교체 ②**구 시드는 목업 확정** — 새 시드가 정본. 파급: `projects.json`·`maintenance.json`의 인물 id가 이제 다른 사람을 가리키므로 그 시드 적재 전에 재앵커가 필요하고, eval 기대값(host 트랙 소유)도 같은 영향을 받는다
- **컨트롤러 엔드포인트 신설 완료**(인증 없이 — 결정 기록 2026-08-21 3행): `/api/people`·`/api/people/{id}`·`POST,GET /api/projects`·`GET /api/projects/{id}`·`PUT /api/projects/{id}/progress`(2단계). 호출자는 `X-Caller-Person-Id` 헤더 → `common/config` 리졸버 한 곳(인증 도입 시 이 클래스만 교체). **헤더 신뢰 상태이므로 노출 금지.** 부수로 전역 핸들러의 500 결함 수정(읽을 수 없는 본문 → 400). 테스트 106개
- **인증 완료(스위치 OFF 상태)**(사용자 지시 — 공용 결정 기록 2026-08-21 1행): `User` 엔티티·Flyway V2·시드 계정 44건·RS256 JWT 발급·갱신·JWKS·보호 체인. `pms.auth.enabled=false`가 기본이라 지금은 헤더로 돌고, true로 올리면 토큰 체인이 활성화된다(`AuthEnabledIntegrationTest` 8케이스가 켠 상태를 실증). **`OpenSecurityConfig`를 지우면 전부 401** — 켤 때는 스위치만 올린다
- **감사 기록 + A5·A7·EPIC B 완료**(사용자 선택 (다) "기록만 먼저" — 공용 결정 기록 2026-08-21 1행): `common/audit`에 append-only 감사(`AuditTrail` 계약 + `audit_logs` 단일 테이블 + Flyway V3), project 쪽 판정은 `ProjectAuditRecorder` 한 곳(바뀐 필드만·status 변화만 STATE_CHANGE·무변경이면 미기록). 라우트 신설: `PUT /api/projects/{id}`(A5 — 순방향 한 칸 전이 + 정보 수정) · `POST /complete`·`/reopen`(A7 — 재개 시 진척률 90) · `POST /api/projects/{id}/assignments`·`PUT`·`DELETE /api/assignments/{id}`(EPIC B). 기능별 권한 기본 표는 `ProjectActionPermission` 한 곳(§4-2 — US-A8의 커스텀이 붙을 자리). 실기동 실측: 생성→전이→배정 CRUD→100%→완료→재개 관통 + **감사 9행**(행위자별·`source=WEB`·배정 행도 projectId) + 거절 6종. 테스트 212개. **미검증(의도)**: 감사 조회 API(G1-3·G2-2)·도메인 이벤트·가동률 재계산
- **프론트엔드 실연동 완료**(사용자 지시 "api는 새로 구현한 것에 맞추고 frontend는 디자인만 써서" — 2026-08-22): `frontend/`를 React+**TS**로 재작성(conventions react-ts §도입부 "신규 파일은 TS"). 디자인(`styles.css`·레이아웃·컴포넌트 클래스)은 구 프로토타입 그대로 이식하고, `api.ts`·`store.tsx`·화면은 현재 계약에 맞춰 새로 썼다. 붙인 것: 로그인(토큰 쌍·refresh 회전) · 프로젝트 목록·상세 · 생성(A1) · 정보 수정·전이(A5) · 진척률 2단계(A2) · 완료·재개(A7) · 배정 CRUD(B) · 인력 목록. **인증 두 모드 모두 동작**(스위치 OFF=헤더·ON=토큰 — 토큰 `sub`를 헤더로도 실어 배선을 하나로) + **로그인 우회 경로**(사용자 지시 "로그인 넘기고 나머지 기능을 테스트하고 싶다" — 화자 id만 지정하는 세션 모드 신설, 상단 바에서 화자 전환. 인증 ON이면 401·없는 id면 404로 로그인 화면 복귀). 없는 화면은 목업으로 채우지 않았다(가동률·유지보수·알림·감사 탭·사용자 관리 = 서버 부재). 실측: vite 프록시(5173→8080) 경유로 로그인→생성→전이→배정→요약까지 관통, 인증 ON에서 팀원 토큰은 본인만 조회·헤더 위조 무시·무토큰 401. `npm run build`(tsc+번들) 통과, verify.sh의 frontend 단계가 `tsc --noEmit`를 돌린다
- **화면 피드백 2차 반영 완료**(2026-08-22 — 결정 기록 1행): 기간 규칙(종료일 > 시작일) · **A2-9 진척률은 진행중에서만**(409 NOT_IN_PROGRESS — MCP 동일 거동) · 인력·조직 **등록**(E2-1·E3-1)과 폼 선택지 API · 인력 "비활성"→"삭제" 개명. 조직 id 재사용 결함을 시퀀스 발급으로 교정(V5·V6). 테스트 262개
- **화면 피드백 1차 반영 완료**(2026-08-22 — 결정 기록 2행): 팀원 scope TEAM · 삭제 권한 확장 · `GET /api/me` · A6-1 PM 교체 · A4 삭제 · E2-3 인력 비활성 · E3-3 조직 삭제·목록 · 진척률 1클릭 저장(100%만 확인) · 화자 명부 누적. 테스트 236개
- **다음 작업:** 남은 project AC — **A6-3(역할 지정)** → **A8(프로젝트별 권한 커스텀)**, 그리고 **감사 조회 뷰**(`GET /api/audit` 관리 플래그 전용 G1-3 · `GET /api/projects/{id}/audit` 가시성 범위 G2-2 — 기록은 이미 쌓이므로 조회만 얹으면 된다). A6가 먼저면 PM 배정 종료 거절(422)이 풀리고, `?phase=` 목록 필터도 남아 있다
- **차단 요소:** 없음. 다만 `pms-old/`의 게이트 M0 산출물 승격 시점·방식은 MCP 담당 결정 사항(공용 결정 기록에 영향 명시)

## 이전 상태 (2026-08-20 — 컨벤션 소급 수정, `pms-old/` 기준)

- **컨벤션 소급 수정 5곳 완료**(브랜치 `fix/m1-convention-retrofit`): 호출자 식별 단일화(`@CallerPersonId` 리졸버 — common 배치) · refresh 디코더에 검증자 조합 부착(`TokenClaimValidators` 공유로 access/refresh 스타일 통일) · 시드 로더 `ObjectMapper` 빈 주입 · `count()` 파생 질의 · traceId 봉투-로그 상관 배선(전역 핸들러·보안 체인 401 + 상관 검증 테스트 2건). verify.sh pms PASS — 테스트 73개(신규 2). 이 산출물은 `pms-old/`에 있다 — 새 `pms/`는 common의 에러 봉투·traceId 배선만 이식했다

## 이전 상태 (2026-08-18 — 시드 적재 머지)

- **루트 M0 시드 적재 — identity분 완료**(브랜치 `feat/pms-m0-seed-identity`): `IdentitySeedLoader`(ApplicationRunner — 기동 시 Person이 비어 있으면 `people.json` 44명+부록 B 확정 규칙 자동 적재, 멱등) — 직급 9종·조직 트리 18노드(root 프로텐+부문 6+팀 11)·기본 권한 그룹 4종·시스템 계정 `admin@proten.co.kr`·billable=false 10명·User 45(초기 `proten1!`). **시드 인원 id=생성 id 정합 보증**(불일치 시 기동 실패 — 후속 시드·eval 참조 전제). verify.sh pms PASS — 테스트 64개(신규 8, Testcontainers PG) + 실기동 스모크(compose PG·bootRun 자동 적재 45/45/18/9/4·curl 관통)
- ~~**게이트 M0 잔여**: LLM 학습 미사용 조항 확인(사람 작업) + 인증 3케이스 실서버 실측·사용자 승인~~ — **2026-08-20 게이트 M0 통과**(공용 결정 기록)
- **host 트랙 참고(접점 정보 — 계약 변경 아님)**: identity 시드가 실 DB에 적재됨 — `/mcp` 임시 시드 어댑터 → `PeopleQueryService`+로그인 JWT(`pms.auth.jwks-uri`) 전환 가능 상태(전환 시점은 MCP 담당 몫)

## 그 이전 상태 (2026-08-18 — PR #11 머지)

- **PMS-M1b 완료**: 가시성 필터(`OrgTree`·`PersonVisibility` scope 4단·TEAM subtree)+`RequesterResolver`+404 은닉 공통 `NotFoundException`+`GET /api/people` 목록/단건·Testcontainers PG 도입 — 상세는 세션 로그(2026-08-18 M1b)
- **차단 요소:** 없음

## 세션 로그

### 형식 (복사해서 사용)

```
### YYYY-MM-DD — <작업 요약>
- 완료: <한 것 + 검증 결과>
- 미해결: <다음 세션으로 넘기는 것>
- 다음 작업: <구체적으로>
```

### 2026-08-23 — 도메인 루트 계약 3종 승격 (안 ② 도메인 몫) + 조직 id·시드 원본 id
- 완료: MCP 담당이 확정한 승격 방식(안 ②)의 도메인 쪽 몫. **`maintenance.MaintenanceLookupService`** · **`project.ProjectLookupService`** · **`project.ProgressCommandService`** + **`WorkforceProfile` 조직 id 2종**(MCP 요청 건 해소) + **`PersonRef.division`**. 승격 전에는 `PersonTools`만 실연결이고 나머지 4도구가 `ToolError.unavailable`이었다 — 이제 `UtilizationTools`만 남고 그건 EPIC C에 묶인다. 검증 `verify.sh pms` PASS
- **범위 오산 정정**: project 조회 승격을 "패키지 이동뿐"으로 봤으나 내부 계약에 status·keyword 필터가 아예 없어(`listVisible(caller, Pageable)`뿐) 저장소 질의를 신설했다
- **사용자 결정 2건**: ①프로젝트의 team·division은 **PM 소속 파생**(실측이 결정 — 시드 값이 382/382 구 익명 명부 PM 소속과 일치해 파생값이었고, 실제 명부에서 300/382 불일치. eval B류·도구 description·프론트 어디도 이 필드를 보지 않는다) ②계약·이슈 id는 **시드 원본 번호**(계약 1~105·이슈 `no` 230~496)
- **②가 닫은 결함 둘**: eval C-01~03의 앵커 **계약 101**이 identity 생성의 우연에 기대고 있었다 · 이슈에 identity 1~14를 주는 바람에 계약 id에 전부 가려 **`list_maintenance_logs`의 ISSUE 갈래에 도달할 수 없었다**(도구는 계약 우선 해석 — 목업과 동일)
- **부수 수습**: 트랜잭션 안에서 `NotFoundException`을 잡아 갈래를 가르던 코드가 그 트랜잭션을 롤백 대상으로 표시해 뒤 질의를 조용히 망가뜨렸다(실측 — 999999 조회가 빈 값이 아니었다) → 존재 검사로 대체. 앞선 PostgreSQL 타입 없는 null 건과 같은 계열의 "조용히 틀리는" 함정
- 미해결: 없음. 다음 도구 1개(`UtilizationTools`)는 EPIC C에 묶여 있다
- 다음 작업: **EPIC C 가동률**(계약 2종·조직 id가 다 서 있어 바로 착수 가능 — 끝나면 카탈로그 8종 완성)

### 2026-08-23 — maintenance 모듈 신설 (EPIC D 조회분) + 시드↔모델 결정 7건
- 완료: ROADMAP M1 pms 절 **EPIC D 조회분(D-a)**. 모듈 신설(6 → **7종**) · 엔티티 4종 + Flyway **V9** · 시드 적재(계약 105·사이트 157·연락처·이슈 14) · D4-1 목록(keyword = 계약명·계약사·**사이트명** 3종)·D4-2 상세·D4-3 전사 공개·D3-4 이슈 조회(미배정 필터) · `IssueComment` 표·조회. 검증 `verify.sh pms` PASS — 테스트 298 → **314개**
- **분할이 이 작업의 첫 결정이었다**(사용자 선택): D 전체는 모듈 신설 + 4엔티티 + 시드 + 상태머신 확장이라 한 덩어리로는 컸다. 조회+시드만으로 `search_maintenance`·`list_maintenance_logs` port가 열리고 eval C류 전제가 성립해 **G1 임계경로가 여기서 풀린다** — 이관(D1)의 설계 부담을 조회와 섞지 않았다
- **시드↔모델 괴리 7건을 하나씩 물어 결정**(공용 결정 기록 2026-08-23 참조): status 4종 유지+2건 흡수 · 모델 밖 3종(sheet·clientRep·channel) 전부 살림 · category·targetInfra는 계약 / **serverSpec은 사이트** · 코멘트 표는 만들고 적재 0건 · 이슈 조회 라우트 함께 · 잘못된 날짜 말일 보정 · **이슈↔사이트는 사이트명 일치만**
- **실측이 판단을 갈랐다**: ①계약 #1의 serverSpec이 "**태광그룹**- 1번서버 …"라 45사이트 중 한 곳을 가리킨다 → 서버는 사이트 속성이고 시트가 계약 칸에 적었을 뿐 ②태그 `[전력거래소, 사이버다임]` 6건에서 전력거래소가 실제 고객이고 사이버다임은 벤더인데, 접두 없는 계약사 "사이버다임"이 시드에 실재해 계약사 매칭이 걸린다 → 붙이면 여러 계약 중 하나의 첫 사이트에 매달려 **모르는 것을 아는 척한다** ③계약 #72 `endDate="2027-11-31"`이 파싱 예외로 **기동을 세우고 있었다**
- **부수 수습 2건**: 선택 필터 `:param is null`이 PostgreSQL에서 "No function matches"로 깨졌다(타입 없는 null + `concat`) → like 패턴을 Java에서 만들고 null 판정에 `cast(... as ...)`로 타입을 준다 · `PersonDirectoryService.findIdByExactName` 신설(시드가 영업대표를 이름으로 적어 둠 — **정확히 한 명일 때만** 답한다)
- 미해결: 없음. D 쓰기(D-b)·이관(D-c)은 ROADMAP에 분리 등재. **D-c 선행 2건**: §5에 완료→유지보수중 전이가 없어 `Project`에 전용 메서드 필요 · 라우트는 project 경로인데 계약 생성은 maintenance 소관이고 한 트랜잭션이어야 해 모듈 방향 결정 필요
- 다음 작업: **EPIC C 가동률**(계약 2종이 PR #19 대기 — MCP 확인 오면 즉시) 또는 **EPIC E 쓰기 5종**(감사 조회와 같은 "판정은 이미 섰고 로직만" 형태)

### 2026-08-23 — 감사 조회 2뷰 실구현 (G1-3 · G2-2) — 골격 501을 처음 걷어냄
- 완료: ROADMAP M1 pms 절 ②. `AuditQueryServiceImpl` 두 메서드 + `AuditLogRepository` 최신순 파생 질의 2종 + `AuditRecordFactory`(스냅샷 역직렬화). **501 골격 7 → 6개**. 검증 `verify.sh pms` PASS — 테스트 289 → **298개**(단위 4 + 관통 5)
- **골격 단계의 판단이 실제로 비용을 줄였다**: 2026-08-22에 "없는 것은 로직이지 권한이 아니다"로 권한·가시성 판정을 미리 세워 둔 덕에, 이번 작업은 파생 질의와 변환뿐이었다. 판정을 나중으로 미뤘다면 이 PR에 403·404 설계가 함께 들어왔을 것이다
- 설계 지점 셋: ①**정렬을 호출자에게 맡기지 않는다** — 이력은 시간 순서가 의미의 일부라 `?sort=` 하나로 뒤집히면 안 된다. `Pageable`에서 페이지·크기만 취하고 순서는 저장소 메서드 이름이 정한다 ②**기록·조회가 같은 `JsonMapper`** — 갈리면 저장은 성공하고 조회만 비는 형태로 어긋난다 ③**깨진 스냅샷 한 행이 목록을 세우지 않는다** — append-only라 고칠 수 없는 행이고(G1-2) 이력 화면이 안 열리는 쪽이 더 나쁘다
- **부수 수정 — 골격 주석의 인덱스 주장이 틀렸다**: "정렬 인덱스가 V3에 이미 있다"고 적혀 있었으나 V3의 둘은 선행 컬럼이 `project_id`·`entity_type`이라 필터 없는 통합 목록에는 쓰이지 못한다(전체 정렬). Flyway **V8**로 `ix_audit_created` 신설
- 미해결: 없음
- 다음 작업: ①의 MCP 확인이 오면 **EPIC C 가동률**(계약 2종이 PR #19 대기). 그 전이면 **EPIC E 쓰기 5종 골격 채우기**(E1-1·E2-2·E3-2·E4·E5 — 같은 "판정은 이미 섰고 로직만" 형태라 감사 조회와 같은 방식으로 간다)

### 2026-08-23 — 프로젝트 시드 적재 382건 + 인원 정본 확정
- 완료: ROADMAP M1 pms 절 ③. **`ProjectSeedLoader`** — `projects.json` 382건을 **엔티티로** 적재하고 상태는 **§5 전이를 실제로 밟아** 만든다(계약대기→수주확정→진행중→100→완료). 382건을 그 길로 통과시키는 것 자체가 상태 머신 통합 검증이다. 배정은 부록 B의 M/M 규칙(실무자 = PM 외 참여자, 없으면 PM 본인 · `contractMm÷개월수÷실무자수` · 실무자 따로 있으면 PM은 0). 멱등은 "테이블이 비었나" — 이름 키로 하면 별건인 동명 2건이 접힌다. 검증 `verify.sh pms` PASS
- **적재 시 보정 1종 신설**(사용자 결정): `status=완료`인데 `progress<100`인 **13건 → 100**. 원본 JSON은 무수정(OFFSITE와 같은 형태 — 시트를 다시 내려받아도 규칙이 살아남는다). 상태 머신을 밟아 적재하므로 보정 없이는 `complete()`가 A7-2로 거절한다
- **부수 발견이 이 세션의 가장 큰 소득 — 인원 정본이 바뀐 것이 기록되지 않았다**: `people.json`(익명 명부)과 `seed_org_proten.sql`(실제 명부)이 **같은 id에 다른 사람**을 담고 **이름이 하나도 겹치지 않는다**. 구 `IdentitySeedLoader`는 전자를, 현 `PersonSeedLoader`는 후자를 읽는다. 사용자 확인으로 **실제 명부가 정본**임을 확정하고 `reference/seed/README.md`에 정본·함정을 못박았다(기존 내용은 무관한 ralph 다이어그램 오붙임이었다)
- **그래서 부록 B 검증 케이스가 달라졌다**: M/M 규칙이 만드는 **수치 191/182/133은 그대로** 재현되지만 이름이 이현창·윤종헌·김경민이고, **윤종헌은 billable=false(AX영업팀)라 C1-5로 `overbooked`에서 빠진다** → 3명이 아니라 **2명**. 통합 테스트가 두 갈래(규칙만 / C1-5 적용)를 함께 고정한다
- 미해결(§12 등재): **인물 이름 재매핑 194곳** — `docs/evals/eval-cases.md` 36케이스 화자·기대값 · `docs/유저_시나리오.md` 페르소나 8명이 전부 익명 명부 기준이다. **eval 채점이 이름 대조를 포함하므로 G1 전 필수**이고 두 문서는 host 트랙 소유라 공용 결정 경유다
- 다음 작업: ROADMAP M1 pms 절 ② 감사 조회 2뷰(G1-3·G2-2 — 판정은 실구현, 파생 질의만). 그 뒤 ④ EPIC C 가동률(계약 2종이 PR #19에서 대기 중)

### 2026-08-23 — 모듈 간 계약 2종 신설 (가동률 분자·분모) + B2-1 종료일 규칙
- 완료: ROADMAP M1 pms 절 ①. **`project.AssignmentDirectoryService`·`MonthlyAssignment`**(그 달과 겹치는 배정을 행 단위로 — personId·projectId·projectName·monthlyMm)와 **`person.WorkforceDirectoryService`·`WorkforceProfile`**(capacity·billable·gradeCoeff·team·division·subtree 인원)을 각 모듈 루트에 신설. **부수 결함 수정**: `close()`가 상태만 바꾸고 종료 시점을 남기지 않아 AC B2-1의 "종료월 이후 제외"를 표현할 수 없었다 → 종료 시 `endDate`를 종료월 말일로 당기고(사용자 결정) `Clock` 주입으로 시간을 고정 검증. 검증 `verify.sh pms` PASS — 테스트 270 → **281개**(신규 11)
- 실측이 바꾼 판단: 미해결 접점이 "배정 조회 하나"라고 적어 뒀는데 산식을 코드에 대보니 **분모·모집단·계수·team/division·subtree까지 전부** 경로가 없었다 — `PersonRef`로는 가동률을 계산할 수 없다. 그래서 계약이 하나가 아니라 둘이 됐고, `PersonRef`를 확장하는 대신 나눴다(소비자가 다르면 나눈다 — conventions §5)
- 두 계약 모두 **판정을 하지 않는다**: 가시성·모집단은 resource가 정해 인원 명단으로 넘긴다. 두 곳에서 거르면 정본이 갈리고 project가 조직을 알아야 해진다
- **정정**: PR #18 §4에 "`/mcp` 승격도 이 승격에 걸린다"고 적었으나 **틀렸다**. M0 산출물(`internal/seed/TemporaryPortsConfig`)은 port·DTO를 `mcp` 루트에 두고 도메인이 구현하는 형태라 의존이 `project → mcp`다. 구조 원칙 3의 "호출"과 방향이 반대여서 두 기록이 갈리는데, **어느 쪽인지는 MCP 담당 결정 사항**이라 문서 양쪽에 미정으로 등재했다
- 미해결: 두 계약이 **아직 소비되지 않았다** — 경계는 열렸지만 실제 횡단은 EPIC C가 첫 사례가 된다(project가 이미 `person` 루트를 쓰고 통과하므로 모양 자체는 검증된 패턴). MCP 담당 확인 2건: 시그니처가 `UtilizationEntry`·`OverbookedEntry.Cause`를 채우기에 충분한가 · port 의존 방향
- 다음 작업: ROADMAP M1 pms 절 ② 감사 조회 2뷰(G1-3·G2-2 — 판정은 이미 실구현, 파생 질의만) 또는 ③ projects 시드 382건. MCP 확인을 기다리는 동안 둘 다 진행 가능

### 2026-08-23 — 문서↔코드 정합 일괄 + PMS-M 라벨 폐기 (코드 변경 없음)
- 완료: 문서 19종을 코드와 실측 대조 → **드리프트 21건** 확인 후 A(pms 단독)·B(공용 사실 오류)까지 정정. 성격별 정리는 공용 결정 기록 2026-08-23 1행. pms 트랙 반영분: **PRD-pms v2.8** — §0 스택(H2 → Testcontainers 전용 · 프론트 JSX 전제 소멸) · §0 모듈 간 통신(`@NamedInterface` → 모듈 루트 = 공개 API, §3과의 정면 모순 해소) · §1·§11의 `보정100` → **`보정144`**(2026-08-10 재정의가 이 두 곳만 못 미쳤고 상위 PRD와 어긋나 있었다) · §11 DoD(폐기된 "domain에 Spring/JPA import 0" → `LayerRuleTest` + "501 던지는 자리 0건" 신설) · **§10을 EPIC 기준 상태표로 재편**(PMS-M0~M6 라벨 폐기) · §12의 "구현 시점 = PMS-M1"을 실제 소관 EPIC으로. 그 밖에 `pms/CLAUDE.md`(`ProgressUpdateService` → `ProjectLifecycleService.updateProgress`) · `conventions/react-ts.md` 전면(api.js→api.ts·store.jsx→store.tsx·JS 전제 소멸·Vitest 부재를 트리거와 함께 명시)
- 실측 근거: 모듈 루트 파일 수(person 7·notification 4·audit 6 · **auth·project·resource 0**) · `package-info` 0개 · 501 골격 7서비스 · 시드 적재 테이블 5종(projects·maintenance 미적재) · `frontend/src` `.js`/`.jsx` 0개 · `@Test` 276
- **§10 재편이 드러낸 것**: `project` 모듈 루트가 비어 있어 `resource`도 `/mcp`도 project를 부를 수 없다 — 가동률 접점 미해결의 진짜 이유가 "메서드가 없다"가 아니라 **"공개 API가 없다"** 였다. 그래서 다음 작업 ①을 "계약 루트 승격 + 배정 조회 계약"으로 한 묶음으로 잡았다
- 미해결: conventions §8의 "방언 무관 테스트는 H2 허용" — pms는 Testcontainers 전용이라 어긋나지만 **host 앱 사실 확인이 필요해 손대지 않았다**(양측 합의 대상). MCP 소유 문서 드리프트 4건은 목록으로 전달만 함 — 그중 **PROGRESS-host의 "첫 후보 = identity `PeopleQueryService`"가 재구축으로 사라진 클래스**라 다음 host 세션이 그대로 착수하면 즉시 막힌다
- 다음 작업: ROADMAP M1 pms 절 ① — project 계약 루트 승격 + 배정 조회 계약 안을 만들어 결정 기록 등재(MCP 담당 확인 요청)

### 2026-08-22 — auth 모듈 분리 + resource·notification·EPIC E 쓰기·감사 조회 골격
- 완료: ①**auth 분리** — 계정·토큰·비밀번호를 person에서 떼어 6번째 모듈로. 순환은 **person이 SPI(`AccountPort`)를 정의하고 auth가 구현**하는 방향 역전으로 풀었다(auth → person 단방향) ②**골격 신설** — resource(EPIC C)·notification(EPIC F)·EPIC E 쓰기 5종·감사 조회 2뷰, 미구현은 `501 NOT_IMPLEMENTED`에 `TODO(<AC>)` ③`PageResponse` → `common/web` 승격 ④Flyway V7(capacities·notifications) ⑤**감사 조회의 권한·가시성 판정은 실구현**(403/404는 나중에 얹을 것이 아니다) ⑥**부수 결함 수정** — 타입 틀린 요청 값(`?month=2026-8-1`·`/api/projects/abc`)이 500으로 나가던 것을 400 VALIDATION_ERROR로. 검증: `verify.sh pms` PASS, 테스트 268개(신규 6)
- 사용자 결정 근거: "ttt/pms에서 큰 틀만 가져오고 핵심 로직은 남겨놓는 방향" + "person/service에 서비스가 너무 많다" → 실측해 보니 계약 8개는 과하지 않았고(3개는 project가 실제로 쓰는 cross-module 계약) 붐비는 곳은 impl 26개였으며 그중 7개가 성격이 다른 인증 인프라였다 — 그래서 하위 패키지 정리가 아니라 모듈 분리를 골랐다
- 미해결: **resource ↔ project 접점**(가동률 분자를 얻을 서비스 계약이 없다 — 공용 결정 필요) · 골격 5개 영역의 로직 · SSE 라우트(토큰 마스킹과 한 묶음)
- 추가 완료(같은 세션): **서비스 계약 통합** — project 9→4·person 11→8. 근거는 실측(단일 메서드 6개·소비자 1개 8개·협력자 집합 동일)이고, 구현은 판단 하나당 클래스 하나로 계속 쪼갠 채 둔다
- 다음 작업: 접점 결정 → resource 채우기 / 또는 project A6-3·A8 먼저

### 2026-08-22 — 상태 전이를 상세 화면 전용 버튼으로 (화면 결정)
- 완료: 상태 행위를 **한 줄로 모았다**(`ProjectActions` — 상태마다 버튼 하나: 계약대기→`수주확정으로 →` · 수주확정→`진행중으로 →` · 진행중→`완료 처리` · 완료→`재개` · 유지보수중→없음). 전이는 **확인 카드**(`StatusAdvance`)를 거친다. **계약대기·수주확정에서는 진척률 섹션을 그리지 않는다**(그 단계엔 기록할 진척이 없고 A2-9로 서버도 거절한다 — 못 만지는 편집기가 할 일을 흐렸다). 헤더는 뱃지·정보 수정·삭제만 남겼다 — 다음 한 칸만 노출(계약대기→수주확정→진행중, 서버 `next()`와 같은 표), 확인 카드에 "되돌릴 수 없다"와 전이 후 효과(진행중이면 진척률 수정 가능)를 적었다. 정보 수정 폼에서 **status 선택을 제거** — 되돌릴 수 없는 변경이 "정보 저장"에 섞이지 않게 입구를 하나로 모았다. 전체 치환 PUT의 본문 조립은 `projectBody.ts` 한 곳(폼·전이 버튼이 공유)
- 사용자 결정 근거: "계약대기·수주확정은 수정 버튼 말고 정보 조회에서 바로, 변경 시 확인" — 서버 규칙(A5-1 순방향 한 칸)은 그대로이고 화면 배치만 바뀐다. PRD-pms 부록 A `/projects/:id` 행에 반영
- 검증: 타입 검사·번들 통과 · 전이 버튼이 보내는 본문 형태를 실서버에 무변경 PUT으로 확인(200 · version 불변 · 감사 행 없음 = 변경 없는 저장은 이력도 남기지 않는다)
- 부수 발견: dev 서버가 **낡은 변환 결과를 캐시**해 정보 수정 모달이 흰 화면이었다(디스크 소스·빌드는 정상 — import 추가 전 변환본이 남아 있었다). dev 서버 재시작으로 해소. 파일을 스크립트로 제자리 수정하면 Windows 파일 감시가 놓칠 수 있으니, 편집 후에는 dev 서버를 재시작한다

### 2026-08-22 — 화면 피드백 3건: 기간 규칙 · 진척률 진행중 제한 · 인력/조직 등록
- 완료: ①기간 규칙(종료일 > 시작일 — 프로젝트·배정 엔티티, 400 field=endDate. 화면도 `min` 속성 + 제출 전 문구) ②**A2-9 신설**: 진척률은 진행중에서만(409 `NOT_IN_PROGRESS`, 완료는 기존 A2-8 유지) — 화면은 그 외 상태에서 편집기를 잠그고 현재 상태를 알린다 ③E2-1 인력 등록(인원+계정 한 트랜잭션)·E3-1 조직 신설·`GET /api/grades`·`/api/permission-groups`(폼 선택지) + 인력 화면의 "비활성" 버튼을 **"삭제"로 개명**(동작은 soft 비활성 그대로)
- 검증: 테스트 262개 · verify.sh 전체 PASS · 실기동 실측(기간 역전·같은 날 400 / 계약대기 진척률 409 NOT_IN_PROGRESS / 조직 신설 201·팀장 403·두 번째 root 409 / 인력 등록 201 + **새 계정으로 로그인 200** / 이메일 중복 409 / 없는 조직 422)
- 부수 결함(실데이터 오염): 조직 id를 `max(id)+1`로 발급해 **삭제된 노드의 id가 재사용**됐다 — 사용자가 지운 MOIN개발팀(17) 자리에 새 노드가 들어가 그 팀의 비활성 인원 2명이 새 조직에 붙었다. 감사 로그(삭제→생성이 같은 entity_id)로 추적. **시퀀스 발급으로 전환**(V5·V6 — 역대 최고값 기준) + 회귀 테스트, 개발 DB 원상복구
- 미해결: A6-3·A8·감사 조회 뷰 · 인력·조직 **수정**(E2-2·E3-2) · A2-9의 eval·프롬프트 반영은 host 트랙(공용 미해결 이슈 등재)
- 다음 작업: A6-3 → A8 → 감사 조회 뷰. 인력 수정(E2-2)은 조직 이동 요구가 나오면 함께

### 2026-08-22 — 화면 피드백 7건 반영 (규칙 변경 2건 + 라우트 5개 신설)
- 완료: ①팀원 그룹 scope SELF→TEAM(시드 + Flyway V4 — 적재된 DB는 시드로 갱신되지 않으므로) ②프로젝트 삭제 권한 = PM ∪ "프로젝트 생성" 플래그 ③`GET /api/me`(플래그·scope) → 화면 버튼 노출 판정 ④`PUT /projects/{id}/pm`(A6-1: 승격·강등·managerId 한 트랜잭션, 미배정이면 배정 생성) ⑤`DELETE /projects/{id}`(A4)·`DELETE /people/{id}`(E2-3 soft 비활성)·`GET`+`DELETE /org-units`(E3-3 빈 노드만) ⑥진척률 UI를 2026-08-09 ①에 맞춰 1클릭 저장(100%만 확인 모달) ⑦개발 모드 화자 명부 누적(전환 후 되돌아갈 수 없던 결함)
- 검증: 테스트 236개(신규 단위 22·슬라이스 8·관통 3) · verify.sh 전체 PASS · 실기동 실측(`/api/me` 3역할 · 팀원 인력 목록 1→5명 · PM 교체 후 역할·managerId 이동 · 이미 PM 재지정 422 · 참여자 강등 후에도 생성 권한으로 삭제 204 · 팀장의 인력 비활성 403 · 본인·시스템 계정 422 · 인원 있는 노드 삭제 409 · 없는 경로 404)
- 부수 수정: 매핑 없는 경로가 500으로 나가던 것(전역 핸들러 catch-all이 "핸들러 없음"까지 삼킴) → 404 봉투 + 회귀 테스트
- 미해결: A6-3(역할 지정)·A8(권한 커스텀)·E2-1·E2-2(인력 등록·수정)·E3-1·E3-2(조직 신설·이름 변경)·감사 조회 뷰 · 팀원 scope 변경의 eval 기대값 반영은 host 트랙(공용 미해결 이슈 등재)
- 다음 작업: A6-3 → A8 → 감사 조회 뷰. 인력 등록·수정은 화면 요구가 나오면

### 2026-08-22 — 프론트엔드 실연동 (`frontend/` React+TS 재작성)
- 완료: 디자인만 이식하고(styles.css·팔레트·카드/테이블/확인카드 클래스) API·상태·화면은 현재 계약으로 새로 씀 — `types/api.ts`(서버 DTO 타입) · `api.ts`(세션·refresh 회전·§7 봉투를 `ApiError`로) · `store.tsx`(Result로 봉투 전달, STALE_VERSION 시 상세 재조회) · 화면 5종(로그인·홈·프로젝트 목록/상세·인력) + 조각 5종(진척률 2단계 편집기·배정 패널·생성/수정 모달·UI 프리미티브). 구 JS 앱(챗 패널·알림 벨·가동률·유지보수·설정)은 서버가 없어 삭제 — 없는 화면을 목업으로 두지 않는다
- 로그인 우회(사용자 지시): 세션이 두 갈래다 — `token`(로그인) · `caller`(화자 id만, 인증 OFF 전용). 로그인 화면의 `바로 시작`·personId 입력으로 진입하고, 상단 바 화자 셀렉트(+id 직접 입력)로 로그아웃 없이 전환한다. 401/404는 안내 문구와 함께 로그인 화면으로 되돌린다
- 검증: `npm run build`(tsc --noEmit + 번들) 통과 · vite 프록시 경유 실측(로그인→목록→생성→A5 전이→배정 추가→진척률 요약) · 인증 ON 재기동 실측(팀원 토큰=본인만·헤더 위조 무시·무토큰 401) · **로그인 없이 화자만으로 실측**(관리자 43명·팀장 팀 subtree 5명·팀원 1명 · 팀장 생성 201 · 팀원 생성 403 · 없는 화자 404) · 프로브 데이터 정리
- 미해결: `/api/me`가 없어 권한별 버튼 숨김 불가(403 문구로 안내) · 챗 BFF·알림(SSE)·가동률·유지보수·감사 조회 탭·인력 CRUD는 서버 대기 · 목록 서버 검색·`?phase=` 미구현이라 한 번에 받아 화면에서 거른다(임시) · Vitest 단위 테스트 없음(`npm test`는 현재 타입 검사)
- 다음 작업: A6 → A4 → A8, 감사 조회 뷰 2종. 서버 라우트가 늘 때마다 `frontend/README.md`의 "아직 없는 것" 표를 줄인다

### 2026-08-21 — 감사 기록 도입(기록만) + A5·A7·EPIC B
- 완료: `common/audit`(계약 `AuditTrail` + `AuditLog` 엔티티 + `audit_logs` Flyway V3, append-only를 매핑까지 못 박음) · `ProjectAuditRecorder`(변경 필드 diff·action 판정 단일 지점) · `AuditSourceResolver`(요청 경로로 WEB/MCP) · A5 `PUT /projects/{id}` · A7 `/complete`·`/reopen` · EPIC B 배정 CRUD 3라우트 · `ProjectActionPermission`(§4-2 기본 표 단일 지점, 진척률 판정도 이관). 전이·완료·재개·종료 규칙은 엔티티로. 검증: `bash scripts/verify.sh` 전체 PASS(host·mock·pms), pms 테스트 **212개**(신규 단위 46 + 웹 슬라이스 20 + Testcontainers PG 관통 7). 실기동 실측: V3가 시드된 개발 DB에 적용(v2→v3) · HTTP 관통(생성→전이 2회→배정 추가·수정·종료→100%→완료→재개) · 감사 9행 검증 · 거절 6종(409 역방향·완료 직접 전이 / 403 참여자 정보수정·배정 / 422 role=PM·PM 배정 종료 / 404 가시성 밖) · 프로브 데이터 정리 후 종료
- 부수 수정: 엔티티 변경 후 질의로 JPA가 flush 해 **수정 1회에 version +2**가 되던 것(실기동에서 1→3으로 발견) → 중복 검사 질의를 변경 전으로 이동 + 통합 테스트에 "수정 1회 = +1" 단정 추가
- 미해결: 감사 **조회** API 2종(G1-3 통합·G2-2 프로젝트별 — 권한 403·404 은닉 포함) · 도메인 이벤트(`ProjectCompleted`·`AssignmentClosed` — notification 모듈 없음) · 가동률 재계산(B1-3·C1-4 — resource 모듈 없음) · 알 수 없는 열거 값이 §7의 422가 아니라 400으로 나가는 기존 결함
- 다음 작업: A6(PM 교체·역할 지정) → A4(소프트 삭제) → A8(권한 커스텀) → 감사 조회 뷰 2종. A6를 먼저 하면 PM 배정 종료 거절(422)이 정상 경로로 풀린다

### 2026-08-20 — 컨벤션 소급 수정 5곳 (신설 관용구 규칙 위반 해소)

- 완료: 2026-08-20 컨벤션 개정(관용구 규칙 5건)의 소급 수정 — ①**호출자 식별 단일화**: `@CallerPersonId` 어노테이션(common 공개 API)+`HandlerMethodArgumentResolver`(common/internal/web) 신설, `MeController`·`PeopleController`의 `authentication.getName()` 수동 파싱 제거. common 배치 근거 = M1c 관리 API와 이후 project·resource 컨트롤러 전부가 쓰는 횡단 관심사(에러 봉투와 같은 자리) — ModularityTest 관통 ②**클레임 검증 디코더 부착**: `TokenClaimValidators`(aud=pms·token_type 검증자 팩토리, 패키지 프라이빗) 신설 — `NimbusTokenProvider` refresh 디코더에 검증자 조합 부착(디코드 후 수동 if 2개 제거)·`ApiTokenVerification`도 동일 팩토리로 통일(익명 검증자 중복 제거) ③`IdentitySeedLoader`에 `ObjectMapper` 빈 주입 ④`findAll().isEmpty()` → `count() > 0`(`PersonRepository` 포트+JPA 어댑터에 `count()` 추가) ⑤**traceId 상관 배선**: 봉투 생성 지점 전부(전역 핸들러 3경로·보안 체인 401)에서 traceId+코드를 서버 로그에 기록(500은 스택, 401은 요청 URI 동반 — 토큰 원문·개인정보 로그 금지 준수) + 신규 테스트 `ErrorTraceIdLogTest` 2건(응답 봉투의 traceId가 실제 로그 이벤트에 등장 — 두 생성 경로 각각, ListAppender). 부수: common package-info의 낡은 "ProblemDetail" 서술 → §7 에러 봉투로 정정(컨벤션 정합화 ⓐ와 동일 괴리). 검증: **verify.sh pms PASS** — 테스트 73개(신규 2, token_type 교차 오용 401·시드 멱등성 등 기존 회귀망 전량 초록)
- MCP 담당 인지(경계 플래그 — 코드 무수정): `mcp/internal/seed/SeedPeople.java`에 동일한 `new ObjectMapper()` 위반 존재 — `/mcp` 모듈 소유라 미수정. M1의 `PeopleQueryService` 승격 시 파일 제거로 자연 해소되는 경로도 있음
- 미해결: 없음
- 다음 작업: PMS-M1c(관리 API US-E1~E5)

### 2026-08-20 — 학습 세션: M0 코드 복습 + 리뷰 피드백 실측 → java-spring 컨벤션 개정

- 완료: ①M1 착수 전 복습 — pms 코드베이스 전수 독해 후 학습 아티팩트 "PMS M0 복습 노트" 작성(구현 현황 지도·요청 여정·SOLID/TDD 평가·M1 port 계약 예습·셀프 체크 10문항) ②"언어·프레임워크 이해도 부족해 보임" 리뷰 피드백의 실체를 코드 실측으로 특정 — **5곳 전부 프레임워크 제공 기능의 수동 재구현**: 컨트롤러별 `authentication.getName()` 파싱 중복(`MeController`·`PeopleController`) · `NimbusTokenProvider` 수동 클레임 검사(`ApiTokenVerification`의 검증자 조합과 스타일 혼재) · `IdentitySeedLoader`의 `new ObjectMapper()`와 `findAll().isEmpty()` · `ErrorResponse` traceId 무배선(로그 미기록으로 상관 불능). 언어(record·switch·compact constructor) 자체는 관용적 — 판정과 방어 근거는 아티팩트에 ③**java-spring 컨벤션 개정**(두 담당 동석 합의 — 공용 결정 기록): 관용구 규칙 5건 신설 + 불일치 4건 정합화 + §1 필드 주석 다듬기. 검토 중 발견한 문서·코드 괴리 — validation 400/422는 코드가 PRD 정합(문서가 낡음), Lombok·H2는 문서가 관행과 다름(실측 기준으로 정리) ④경계 준수: `pms-mcp-mock/`·`host/`·PRD 무수정, 공용 변경은 컨벤션 1건뿐(동석 합의 성립). 검증: **verify.sh pms --quick PASS**(코드 0줄 — 문서만 변경이라 컴파일 스코프)
- 미해결: **컨벤션 소급 수정 5곳**(현재 상태 참조 — 신설 규칙 위반 상태의 현행 코드)
- 다음 작업: 컨벤션 소급 수정(M1c 전 권장) → PMS-M1c(관리 API US-E1~E5)

### 2026-08-18 — 루트 M0 시드 적재(identity분): 인력 44명·조직 트리·기본 그룹·시스템 계정

- 완료: 세션 시작 결정(사용자 승인) — **M1c보다 시드 적재 선행**(게이트 M0 실측 전제 해소가 우선). ①`identity/internal/seed/IdentitySeedLoader`(ApplicationRunner) — 기동 시 Person이 비어 있으면 한 번만 적재(멱등): 직급 9종(계수 내림차순)·조직 트리 18노드(root 프로텐+부문 6+팀 11 — 대표는 root 직속·team==division 인원은 부문 노드 직속, M1b 판단 ② 정합)·기본 권한 그룹 4종(관리자 systemFixed·부문장/팀장 생성+계약·팀원 SELF — 2026-08-09 ⑦ 매핑)·인원 44(billable=false 3부문 10명)+시스템 계정 `admin@proten.co.kr`(system=true·billable=false)·User 45(초기 `proten1!` — BCrypt 해시 1회 재사용으로 기동 지연 회피) ②**id 정합 보증**: 생성 id≠시드 id면 기동 실패 — 후속 projects·maintenance 시드와 eval 기대값이 시드 인원 id(노도온 26 등)를 참조하므로 조용한 오연결 방지 ③`pms.seed.path` 프로퍼티(메인 yml 기본 `../reference/seed`, 빈 값=비활성 — 테스트 yml 미설정이라 기존 픽스처 무충돌). 검증: **verify.sh pms PASS** — 테스트 64개(신규 8 = 적재 규모·id 정합·트리 소속 3형·그룹 매핑 카운트·billable·시스템 계정·멱등성·로그인→가시성 관통, Testcontainers PG) + 실기동 스모크(compose PG+bootRun: 자동 적재 로그·DB 45/45/18/9/4·curl 대표 로그인 44명·팀원 SELF 본인 1명·무토큰 401) 후 정리
- 판단(ASSUMPTION 주석 병기): ①프로파일 없이 빈 DB 자동 적재 — 부록 B "compose up 후 자동 적재" 그대로, 프로퍼티 빈 값이 오프 스위치(배포 경로 외부화는 PMS-M6) ②시스템 계정 직급=대표이사 재사용 — 스키마상 필수이나 어떤 화면에도 미노출, 신설 직급은 직급 관리(E4) 목록 오염이라 미채택 ③projects·maintenance 시드는 해당 도메인 구현 시(PMS-M2·M4) 각 모듈이 적재 — 계획 승인에 포함
- MCP 담당 인지(접점 정보 — 계약 변경 아님): identity 시드가 실 DB에 적재됨 — `/mcp` 임시 시드 어댑터(`pms.mcp.seed-people-path`) → `PeopleQueryService`+로그인 JWT(jwks-uri) 전환 가능 상태
- 미해결: 없음
- 다음 작업: PMS-M1c(관리 API US-E1~E5). 게이트 M0 잔여는 사람 작업(LLM 조항 확인)+인증 3케이스 실측·사용자 승인

### 2026-08-18 — PMS-M1b: 가시성 필터 + 권한 그룹 판정 + 404 은닉 공통 기반

- 완료: ①**도메인(순수 유지 — DomainPurityTest 관통)**: `OrgTree`(전체 로드 후 메모리 탐색 — 임의 깊이 subtree·경로상 최상위 부문 계산, 순환 방어)·`PersonVisibility`(권한 그룹 scope 4단 해석의 유일 지점 — COMPANY 전체/DIVISION 부문 subtree/TEAM 소속 노드 subtree(E3-4)/SELF 본인, 본인은 항상 가시. 프로젝트 역할에 의한 확장은 PMS-M2에서 합집합으로 얹음) ②애플리케이션: `Requester`·`RequesterResolver`(토큰 personId→본인+그룹 해석, 부재·비활성=401 — MeQueryService 규칙 승계)·`PeopleQueryService`(목록=가시성 내 부분집합·시스템 계정·비활성 제외(2026-08-09 ④·E2-3), 단건=부재·가시성 밖·시스템·비활성 전부 **동형 404**) ③common `NotFoundException` — 404 은닉 공통 예외, 정본 문구 "해당 데이터 없음" 고정(이후 project·maintenance·MCP 매핑이 공용) ④웹 `GET /api/people`·`GET /api/people/{id}`(부록 A 인력 화면의 조회 절반 — CRUD는 M1c) ⑤**Testcontainers PG 도입**(M1a 판단 ③ 예약 이행): 그룹 4단 화자별 목록 부분집합 + 은닉 4케이스 동형 404 봉투를 실 PG로 관통. 검증: **verify.sh pms PASS** — 테스트 56개(신규 25 = OrgTree 6·PersonVisibility 5·PeopleQueryService 7·PG 통합 7)
- 판단(ASSUMPTION 주석 병기): ①subtree는 메모리 탐색(조직 노드 수십 개 규모 — 재귀 SQL 불요, 커지면 질의 하향 재검토) ②root 직속 인원의 DIVISION scope=전사로 넓힘(판정 불능보다 안전 — 해당 실데이터는 관리자 그룹뿐) ③`GET /api/people/{id}` 단건 GET 추가 — §7 라우트 표는 PUT/DELETE만 명시, 인력 상세 화면 대응 최소분(모듈 내부 라우트 — 협업 접점 아님)
- Boot 4.1 실측: BOM 관리 Testcontainers = **2.0.5(2.x)** — 아티팩트 `testcontainers-postgresql`·`testcontainers-junit-jupiter` 개명·`PostgreSQLContainer` 비제네릭·패키지 `org.testcontainers.postgresql` 이동(Maven Central 실물 확인 — 루트 규칙 6 준용). 통합 테스트 JVM 종료 시 "Unsuccessful: drop" 로그는 컨테이너가 컨텍스트 캐시보다 먼저 내려가는 순서 문제로 무해(테스트 주석 기재)
- MCP 담당 인지(접점 정보 — 계약 변경 아님): `PeopleQueryService`가 `find_person` 임시 시드 어댑터의 대체 후보 — 전환 시점·identity 공개 API 형상은 MCP 담당 몫
- 미해결: 없음
- 다음 작업: PMS-M1c(관리 API US-E1~E5) 또는 루트 M0 잔여 **시드 적재** 선행(identity분 가능해짐 — 게이트 M0 실측 전제) — 세션 시작 시 결정

### 2026-08-18 — PMS-M1a: identity 도메인 + 영속화 + 자체 로그인 JWT

- 완료: PMS-M1(identity+인증)을 4슬라이스로 분할(M1a 도메인·영속화·로그인 → M1b 가시성 필터·그룹 판정 → M1c 관리 API US-E1~E5 → M1d 내 계정 EPIC H — 사용자 승인). **M1a 구현**: ①순수 도메인 5종+저장소 포트(§0 규칙 `api→application→domain←infra` — 도메인은 record, JPA 매핑은 `infra/jpa` 엔티티가 별도 부담. 스캐폴드의 DomainPurityTest 공집합 → 실효 통과) ②`identity` 스키마 영속화 — PG 실기동에서 스키마·테이블 5종 자동 생성 확인 ③자체 로그인 `POST /api/auth/login`·`/refresh`(회전)·**`GET /api/auth/jwks`** — RS256, sub=personId(목업 B2-2 정합)·aud=pms·token_type으로 access/refresh 구분(교차 오용 401), 실패 사유(미존재·불일치·비활성 E2-3)는 전부 같은 401로 수렴(계정 존재 탐지 방지) ④common 모듈: §7 에러 봉투 + 전역 예외 핸들러(ApiException·400 VALIDATION_ERROR) — 보안 체인 401도 같은 봉투 ⑤`GET /api/me` 최소 구현(인증 관통용 personId·이름·email — H1-1 완성은 M1d). 검증: **verify.sh pms PASS**(테스트 17개 — AuthService 단위 6 + 관통 통합 9(무토큰 401 = 게이트 M0 인증 케이스 예행 포함) + 기존 경계 4) + 실기동 스모크(bootRun↔compose PG 7.1초, curl 401 봉투·JWKS 200) 후 정리. Boot 4.1 실측 2건: **Jackson 3(`tools.jackson`)** 전환·`@AutoConfigureMockMvc`는 `spring-boot-starter-webmvc-test`로 분리(빌드에 -Xlint:deprecation 추가)
- 판단(ASSUMPTION 주석 병기): ①스키마 관리 = **ddl-auto update**(M0 유보 해소 — 단순·표준. 시드 적재 후 Flyway 재검토) ②서명 키 = 기동 시 임시 생성(재기동 시 재로그인 — 운영 키 외부화는 배포 시) ③웹 슬라이스 테스트는 H2(인증 의미론 — 방언 무관), **방언 타는 질의가 생기는 M1b부터 Testcontainers PG**(conventions §8 취지 유지) ④refresh 회전은 무상태(서버측 취소 목록 없음 — 고통 확인 후 jti 추적 추가, 구현_노트 §1-3 PAT와 동일 보완 경로)
- **(랩업 중 통합) PR #9(/mcp 어댑터 승격 — host 트랙)와 리베이스 병합**: 원격 main에 /mcp 어댑터(7번째 모듈)+인증 체인이 먼저 머지되어 리베이스 — 충돌 2파일(build.gradle·application.yml) 해소(각 트랙 블록 병존, oauth2-resource-server 의존성 중복 제거). **JwtDecoder 빈 충돌을 pms 측에서 해소**: `/mcp`의 `McpJwtDecoderConfig`가 JwtDecoder 빈을 소유(withDefaults 타입 조회)하므로, REST 체인 디코더는 빈이 아닌 보유 컴포넌트(`ApiTokenVerification`)로 바꾸고 체인에 명시 지정 — MCP 담당 코드 무수정. 통합 후 verify.sh pms 재PASS(양 트랙 테스트 전체). **MCP 담당 인지 필요 2건**(코드 리뷰 요청): ①`pms.auth.*` prefix를 양측이 공유하게 됨(mcp: jwks-uri·hs256-secret / pms: access-ttl·refresh-ttl — 프로퍼티명 비충돌) ②main yml의 MCP 소유 블록에 jwks-uri 사용 가능 값 주석 추가(`http://localhost:8080/api/auth/jwks` — 전환 시점은 MCP 담당 몫)
- 미해결: 없음
- 다음 작업: PMS-M1b(가시성 필터 + 권한 그룹 판정 — VisibilityScope 4단·TEAM subtree(E3-4)·404 은닉 공통 기반)

### 2026-08-17 — PMS-M0 스캐폴드 (`pms/` 신설 — 모듈 6종 확정 + 경계 테스트)

- 완료: ①`pms/` Gradle 프로젝트 신설 — Boot 4.1·**Modulith 2.1.0**(2026-06 GA — Maven Central 실해석으로 좌표 확인)·Java 25 툴체인, host·목업과 동일 관례(베이스 패키지 `kr.proten.pms`). 의존성 web·data-jpa·validation·modulith-starter-core + PG(런타임)/H2(테스트) — 이벤트 발행 레지스트리(starter-jpa)는 §8 이벤트 도입 시(PMS-M3+) 추가로 유보 ②**모듈 목록 확정**(공용 결정 기록, 사용자 승인 — PRD-pms §3이 PMS-M0로 위임한 결정): BC 6종(identity·project·resource·maintenance·notification·common)만 생성, chat BFF·mcpconfig 미생성 유보(M1 재론), `/mcp` 어댑터 모듈은 MCP 담당이 목업 `mcp/`·`port/` 승격 시 추가(자리 선점 안 함) ③경계 테스트 4건 초록 — ModularityTest(`verify()` + 모듈 6종 감지 확인)·DomainPurityTest(`..domain..` Spring/JPA 의존 0 — 스캐폴드 시점 공집합 통과, PMS-M1부터 실효)·컨텍스트 스모크(H2). Modulith 2.1에서 `ApplicationModule.getName()` 제거를 빌드 실패로 발견 → 공식 javadoc 확인 후 `getIdentifier()`로 교체(루트 CLAUDE.md 규칙 6 준용) ④docker-compose(postgres:17 단독 — 앱·Nginx 편입은 PMS-M6) ⑤문서 반영: PRD-pms §3 확정 표기 · ROADMAP M0 체크 · 루트 CLAUDE.md Commands(pms 실행 명령 — compose·bootRun) · conventions §5 위임 문구 해소 + 예시 패키지 `com.proten.pms` 오기 → `kr.proten.pms` 정정. 검증: **verify.sh 전 스코프 PASS**(host·mock·pms 6단계) + **실기동 확인**(사용자 요청 — compose PG 기동 → bootRun 7.9초 기동·Hikari PG 연결·8080 응답 후 정리)
- 미해결: 없음
- 다음 작업: PMS-M1(identity + 인증 — 조직 트리·직급·권한 그룹·Person/User·자체 로그인 JWT·가시성 필터. 루트 M0 잔여 항목들의 전제)

### 2026-08-14 — 이슈→계약 링크 기준 확정(사이트명 포함) + ③④ 합의 성립 후속 플래그 정리

- 완료: ①공용 미해결 이슈(2026-08-12 host 등재) 해소 — **이슈→계약 링크 기준 = 계약명·계약사·사이트명 3종 확정**(사용자 승인, 공용 결정 기록). 근거: 시드 실측 '한국거래소' 이슈 7건의 유일 후보 계약 101(지수방법론)은 사이트명으로만 일치 — 계약명·계약사 기준만이면 이슈 14건 전량 무연결(eval C류 성립 불가 + 챗 이력 조회 공회전). ④ 결정과 동일 원리(사용자는 고객사=사이트명으로 부른다) — 검색 keyword와 적재 링크가 같은 3종 기준 공유. 부록 B "이름 일치분만" 모호 문구 명시화 ②**PRD-pms v2.7 확인 대기 플래그 5곳 해소**(헤더·v2.7 절·C1-6·D4-1·12장) — 2026-08-12 양측 합의 성립 반영, C1-6·D4-1 구현 금지 해제(구현은 PMS-M1) ③공용 PROGRESS(결정 기록 1행·미해결·현재 상태)·ROADMAP eval 항목 플래그 정리. **경계 준수**: PRD-host·eval-cases·`pms-mcp-mock/` 무수정(host 소유 — 결정 기록에 "host 추가 반영 불요" 명시). 검증: verify.sh pms SKIP(코드 전 단계) 정상
- 미해결: 없음(pms 측 확인 요청·대기 후보 소진)
- 다음 작업: 게이트 M-1 대기(잔여 B2-3은 host 트랙) — PMS-M0 스캐폴드는 통과 후

### 2026-08-11 — B2-1 카탈로그 공백 2건(③④) pms 측 결정 + PRD-pms v2.7

- 완료: host 트랙이 B2-1(2026-08-10)에서 실증만 해둔 카탈로그 공백 2건의 **확장안을 구체화하고 pms 측 결정을 확정**(공용 결정 기록 2행 — 양측 합의는 MCP 담당 확인 후 성립). **③ 전사 scope**: scope 열거에 `COMPANY` 추가(A안) + `UtilizationEntry`에 team·division 동봉 + 권한 밖 `COMPANY`는 404 은닉. `orgUnitId` 전환(B안)은 조직 id 확보 경로가 없어 판단 ④와 동형 문제를 유발하므로 미채택. **④ 유지보수 id**: `search_maintenance` 신설(7종→8종, keyword = 계약명·계약사·**사이트명**) + 웹 D4-1에 동일 keyword. **결정 과정에서 발견 2건**(B2-1 §5가 놓친 부분): ①ⓑ안(프로젝트 상세에 계약 id 동봉)은 시드 계약 **105건 전부 sourceProjectId=null**이라 현시점 커버 0건 — "부분해"가 아니라 무용 ②`UtilizationEntry`에 소속이 없어 `COMPANY`만 추가해선 R3-1 발화("부문별로")가 여전히 미완결 ③검색 키에 사이트명이 없으면 45사이트 계약에서 고객사명으로 도달 불가(웹 D4-1도 같은 한계 공유). 반영: **PRD-pms v2.7**(헤더·v2.7 변경 요약 절·C1-6 신설·D4-1 확장·§7 라우트 2곳·12장 확인 대기 등재) · 공용 PROGRESS 결정 기록 2행 + 현재 상태 · ROADMAP M-1 카탈로그 공백 항목. **경계 준수**: PRD-host·eval-cases·`pms-mcp-mock/`은 host 소유라 무수정 — 카탈로그 8종화 반영은 host 트랙 몫. 검증: verify.sh pms SKIP(코드 전 단계) 정상
- 미해결: **③④ MCP 담당 확인 대기**(카탈로그 변경 — 상위 PRD §6 양측 합의). 합의 전까지 C1-6·D4-1 keyword 구현 금지. host 측 잔여 = 카탈로그·description·시스템 프롬프트·eval 한 세트 + eval A-01 확장·C-01 재실험
- 다음 작업: 양측 합의 성립 → 확인 대기 플래그 해소. 게이트 M-1 대기(PMS-M0 스캐폴드는 통과 후)

### 2026-08-10 — 유저_시나리오 §10 웹 여정 정비 (v1.2 — WS-07 신설 + 정합화)

- 완료: 2026-08-06 미해결분(유지보수 웹 여정 추가 검토) 해소 — **WS-07 신설**(4단계, 전부 시드 실데이터 앵커): ①직접 등록(입구 2) — 박건랑(팀장 그룹, "계약 관리" 플래그)이 OEM 계약(가온아이 "그룹웨어 구축") sourceProjectId 없이 등록(D2-1)·실무 노도온(팀원 그룹) 시도는 403(D2-3) ②이슈 등록→기본 배정=사이트 engineerId(D3-1 — 가온아이 45사이트 계약, 구 게시판 "23건 중 할당 1건" 실측이 존재 이유) ③처리 흐름·append-only(D3-2~D3-4 — 신규 예정 사이트 engineerId=null이 미배정 필터 시연 겸용) ④전사 조회(D4-1~D4-3) — WS-05 404 은닉과 의도된 대조 명시. 이관 입구는 WS-01 6단계가 기커버(명화공업 앵커) — 입구 2개를 두 여정이 분담. **정합화 5곳**(이후 결정과 어긋난 서술): WS-01 1단계 orgRole 게이트→그룹 플래그(결정 ⑦)·4단계 "보정 초과·후보"→기본 판정+전세아 133% 확정치(2026-08-10 재정의)·6단계 명화공업 앵커+WS-07 교차 참조·WS-03/05 ADMIN→관리자 그룹 플래그 언어(E2-4·A2-7 현행 정합)·각주 시드 공백 유예 해소. 헤더 상태 초안→**확정**(게이트 P 통과 사실 정정)·v1.2. **경계 준수**: §1~§9(챗·eval — host 재량) 무수정 — §2 "오버부킹 후보"·§8 eval 기대값도 같은 시드 확정치로 갱신 가능해졌으나 host 소유라 헤더 개정 행에 플래그만. 검증: verify.sh pms SKIP(코드 전 단계) 정상
- 미해결: host 측 문서 반영(whoami 그룹명·overbooked 의미·가동률 기대값 + 유저_시나리오 §2·§8 동종 갱신 — host 트랙 소유, 공용 PROGRESS 미해결 기재)
- 다음 작업: 게이트 M-1 대기 — PMS-M0 스캐폴드는 게이트 M-1 통과 후. 대기 중 후보 없음

### 2026-08-10 — 가동률 의미 재정의 + 시드 M/M·billable 확정 (PRD-pms v2.6)

- 완료: 시드 M/M 규칙 논의 중 사용자가 상위 문제 제기(계약 M/M 배분 ≠ 실투입 — "차장 2/사원 1인데 개발은 사원이 다 함") → **가동률 의미 재정의 결정**(사용자 승인, 공용 결정 기록 — MCP 확인 대기): ①배정 M/M=실투입 계획(계약 관점은 contractMm에만) ②오버부킹·집계 정본=기본 가동률 ③보정=Σ(MM×coeff)÷가용 단가 가중 보조 지표(÷→× 뒤집음 — 구 산식은 M/M이 단가 기준일 때만 성립) ④"PM 가동률 제외" 하드 룰은 검토 후 **미채택**(A6-7 기본 M/M=0+실투입 의미로 충족 — 실무형 PM 사각 방지). 반영: 상위 PRD §3 용어 표 재작성 · PRD-pms **v2.6**(C1-1~C1-3 산식·기대값(기본120/보정144)·B1-5 신설·부록 A 배정 패널/가동률 화면·부록 B·12장). **시드 확정 2건**(원래 이 세션 목표): 월별 M/M 부여 규칙 = 실무자(PM 외 참여자, 없으면 PM 본인)에게 contractMm÷개월수÷실무자수 — 시뮬레이션으로 2026-08 오버부킹 3명(남민준 190%·손윤린 182%·전세아 133%) 확인, 게이트 M-1 핵심 시나리오(가동률·오버부킹) 시연 가능 · billable=false = 프로텐·AX사업기획부·관리•마케팅부 3부문 10명(진행중 34건 실측: 세 부문 수행 0). 검증: verify.sh pms SKIP(코드 전 단계) 정상
- **(같은 날 후속) MCP 확인 + 유지보수 시드 변환**: ①가동률 재정의 **MCP 담당 확인 완료**(동석 리뷰 — 결정 기록·문서 플래그 일괄 해소) ②사용자가 유지보수 시트(3섹션 표) + 구 이슈 게시판 화면 제공 → `reference/seed/maintenance.json` 생성: 계약 105건(2026 계약 57·신규 예정 21·미체결·종료 27)·사이트 157개(가온아이 45 정합)·이슈 14건(게시판 목록 전사, type 분류) — 마스킹 없음(2026-08-06 확정), 비날짜 계약일은 contractDateNote 보존, 원문 모순은 원문 유지+주석. engineerId 규칙 = CS사업팀 실무 3명(노도온·한은율·송수람) 2026 계약 사이트 라운드로빈(37/36/36), 예정·종료는 미배정. 이슈 작성자 매핑 남진식→노도온·배성수→송수람. **이관 시연 대상 = 명화공업 확정**(시트 신규 예정 + projects.json 수주확정 — WS-01 앵커 정합). JSON 파싱 검증 통과. **시드 적재 정책 전량 해소**(부록 B·12장)
- 미해결: host 측 문서 반영(whoami 그룹명·overbooked 의미·가동률 기대값 — host 트랙 소유), 유저_시나리오 유지보수 WS 추가 검토(2026-08-06분)
- 다음 작업: 게이트 M-1 대기 — PMS-M0 스캐폴드는 게이트 M-1 통과 후

### 2026-08-09 — 게이트 P 리뷰 결정 8건 소급 등재 + PRD 반영 (PRD-pms v2.5)

- 완료: 게이트 P 통과 후 트랙 파일과 공용 기록의 불일치 발견 — 리뷰에서 결정된 기획 결정 후보 8건이 게이트 결정 기록에 누락. 사용자 확인(**8건 전부 채택 — 프로토타입 동작대로**, ④시스템 관리자 계정·⑦권한 그룹 일반화 공용 변경 포함 **MCP 합의 = 리뷰 동석 성립**) 후: ①공용 PROGRESS 결정 기록 소급 등재(기존 결정과의 대체 관계 명시 — ①은 2026-08-02 "웹=MCP 동일 2단계" 부분 변경, ⑤는 2026-08-06 "편집 탭 승격 안 함" 대체, ⑦은 2026-08-03 "기능 플래그 미채택" 대체) ②상위 PRD §4 재작성 — 판정식 `orgPerm(user.group, …)`·§4-3 그룹 플래그 표 4종·관리자 그룹 시스템 고정·시스템 관리자 계정·조직 임의 깊이 트리·팀 가시성 subtree ③PRD-pms **v2.5** — `OrgUnit` 트리·`PermissionGroup`·`Person.system`·engagement 3종(OFFSITE 폐지)·US-A2 웹 완화 주석·E2-5·**US-E3~E5 신설**·H1-1 whoami 그룹명·§7 라우트(org-units·grades·permission-groups)+에러 코드(IN_USE·IMMUTABLE_GROUP·IMMUTABLE_ACCOUNT)·부록 A 설정 3탭·부록 B 적재 4건 추가·12장 정리. 검증: verify.sh pms SKIP(코드 전 단계) 정상
- 미해결: host 측 문서 반영(PRD-host·eval — whoami 응답 orgRole→그룹명, host 트랙 소유), 시드 적재 잔여(월별 M/M·billable 팀 목록·유지보수 시트→JSON+engineerId)
- 다음 작업: 시드 적재 정책 잔여 결정(PMS-M1 전) — PMS-M0 스캐폴드는 게이트 M-1 통과 후

### 2026-08-09 — 프로토타입 피드백 3차 반영 (권한 관리 UI 시안 정합) + 디자인 개편 + 세션 종료

- 완료: ①설정 탭을 구 화면 구조로 재편 — [사용자 관리]·[조직 관리]·[감사 로그] 3탭, **조직 관리 탭 = 조직 구조 트리(좌) + 직급 관리·권한 관리(우) 단일 화면**(시안 재현). 권한 관리 카드는 시안의 행 구성(그룹 뱃지 · 권한 설명 · n명 · **[권한 ▾] 펼침 — 가시성 select + 기능 토글 즉시 반영** · [수정] · 인원 0일 때만 [삭제]) + 우상단 [+ 권한]. 관리자 행은 버튼 비활성(시스템 고정 — 자기 잠금 방지 유지) ②**디자인 개편(pms 담당 직접)** — 시안 G: 화이트 베이스·토스풍 블루·라운드 카드, Pretendard(CDN)·SVG 아이콘(알림 벨·챗 버튼) ③루트 CLAUDE.md 레이아웃에 `prototype/` 등재. 검증: verify.sh pms SKIP(코드 전 단계) 정상 · 프로토타입 tsc·vite build 통과(디자인 개편 포함) · Playwright 스크린샷 시안 대조 콘솔 에러 0. **세션 종료 — 브랜치 `feat/p-screen-prototype`에 커밋**(원격 push·PR은 화면 리뷰 후)
- 다음 작업: 두 담당 화면 리뷰(MCP 담당 공유 — 특히 결정 후보 ④⑦) → 기획 결정 후보 8건 확정·결정 기록 → 게이트 P 승인

### 2026-08-09 — 프로토타입 피드백 2차 반영 (3건 — 권한 그룹·조직 트리 재구성)

- 완료: ①수행형태에서 **'외부(OFFSITE)' 제거** — 원격·상주·부분상주 3종, 시드 OFFSITE 32건은 로드 시 원격으로 흡수(적재 변환 규칙 확정 필요) ②**권한 그룹 관리 화면 신설**(구 프로토타입 조직 관리 탭 참조) — orgRole을 편집 가능한 권한 그룹(가시성 scope 4단 + 기능 플래그: 프로젝트 생성·계약 관리·전 프로젝트 관리(PM 간주)·사용자/조직/권한 관리)으로 일반화, 그룹 신설/수정/삭제(인원 있으면 거절)·**관리자 그룹은 시스템 고정**(자기 잠금 방지)·사용자별 그룹 부여는 사용자 탭(관리자 부여 가능) — 판정·가시성·404 은닉이 전부 그룹 정의를 따르도록 코어 리팩터링 ③**조직 구조 트리**(첨부 시안 재현) — 회사(root)→부문→팀→임의 깊이, 노드별 인원·프로젝트 수 + [+하위/이름 변경/삭제], 개명 시 소속 인원·프로젝트 team/division 동기화, 인원·프로젝트·하위 있으면 삭제 거절, 팀 가시성은 **하위 조직 포함(subtree)** 으로 확장. 직급 관리는 시드 9종 유지(시안의 '사원'은 시드에 없음 — 미추가). 검증: tsc·build 통과 + Playwright 스모크(수정 폼 옵션 3종 확인·트리 하위 추가·그룹 신설·사용자 부여 목록) 콘솔 에러 0
- **기획 결정 후보 추가**: ⑥engagement에서 OFFSITE 폐지·기존 데이터 원격 흡수(시드 변환 규칙) ⑦**orgRole → 편집 가능한 권한 그룹 일반화**(2026-08-03 확정 "orgRole 커스텀 Out of Scope"·"기능 플래그 미채택"과 정면 상충 — 상위 PRD §4 공용 변경이라 **MCP 담당 합의 필수**. whoami 응답·가시성 판정·시드 적재 영향) ⑧조직 임의 깊이 트리(현행 도메인은 Division·Team 2단 고정 — identity 모델 변경)
- 다음 작업: 피드백 추가 라운드 → 기획 결정 후보(1차 5건 + 2차 3건) 확정 → 게이트 P

### 2026-08-09 — 프로토타입 피드백 1차 반영 (7건) + 기획 결정 후보 도출

- 완료: pms 담당 피드백 7건 반영 — ⓪**AI 어시스턴트 패널**(최종 UI 미리보기 목업: whoami·내 프로젝트·가동률·진척률 수정 **확인 카드→확정/취소**·대상 모호 재질문·403/404 거절 전달 — 실제 에이전트는 host 앱, M1 챗 BFF 연동) ①진척률 수정 **100% 저장만 2단계**, 그 외 값 1클릭 저장 ②정보 수정 폼 전체 필드(고객사·제품군·수행형태·계약MM·기간·상태) ③수행형태 한국어(원격·상주·부분상주·**외부(OFFSITE 임시)**) ④권한 패널 **배치 저장** — 토글은 draft, '변경 저장' 시 커밋+감사 1건(A8-2 PUT 계약 형태와 정합) ⑤사이드바 유지보수 계약/이슈 활성 분리(NavLink end) ⑥**시스템 관리자 계정**(admin@proten.co.kr, 삭제·수정 불가, 인력/가동률/배정 목록 제외) 추가 — orgRole 자체는 가시성·생성 권한·ADMIN 치환에 사용 중이라 유지 ⑦설정에 **조직(팀)·직급 관리 탭**(신설·개명·삭제 — 소속/사용 인원 있으면 거절, 계수 변경 시 보정 가동률 즉시 반영). 검증: tsc·build 통과 + Playwright 스모크 10장면(챗 확인 카드 확정·권한 draft→저장→감사 1행·시스템 계정) 콘솔 에러 0
- **기획 결정 후보 (PRD 반영 필요 — 프로토타입이 현행 문서와 다르게 동작하는 지점)**: ①웹 진척률 2단계 완화(현행 US-A2·2026-08-02 "웹=MCP 동일 2단계" 결정과 상충 — 웹 UI만 완화하고 서비스·MCP는 2단계 유지하는 안, 결정 기록 필요) ②권한 조정 감사 단위 = 저장 1건(US-A8 정합 — AC 문구만 확인) ③engagement 한국어 명칭 확정(OFFSITE 32건 — '외부'는 임시) ④**ADMIN = 회사 고유 시스템 계정**(상위 PRD §4 "ADMIN(대표)" 정의 변경 — 공용 문서·시드 적재·감사 actor 영향, MCP 합의 필요. 대표 개인 ADMIN 유지 여부 포함) ⑤팀·직급 관리 기능 채택(2026-08-06 "설정 편집 탭 승격 안 함" 결정 재론 — US-E3 후보)
- 다음 작업: 피드백 추가 라운드 → 기획 결정 후보 확정(결정 기록) → 게이트 P 승인

### 2026-08-09 — 화면 프로토타입 구성 (게이트 P 리뷰 보조 — "기획한 기능이 맞는가" 검증)

- 완료: `prototype/` 신설(React 19+TS+Vite, 브랜치 `feat/p-screen-prototype`) — 백엔드 없이 목업 스토어가 API 의미론을 재현. 부록 A 라우트 전부: 로그인(email+proten1!·데모 계정)·홈·프로젝트 목록(**phase 탭**·이관 대기 뱃지)·등록(PM 필수·역할 선택)·상세 4탭(개요=**진척률 2단계/완료·재개/이관**, 배정=PM 교체·PL 지정·M/M, **권한 패널**=토글 매트릭스+고정 셀 잠금+커스텀 뱃지+기본값 복원, **이력 탭**=projectId 필터 뷰)·가동률(기본/보정·**billable 집계 포함/제외**·과부하)·인력·유지보수 3층(계약 목록/상세=사이트 담당 엔지니어 정본·이슈=기본 배정/미배정 필터/append-only 코멘트)·설정(ADMIN — 사용자 CRUD·통합 감사로그)·알림 뱃지. 권한·가시성은 합집합 판정+404 은닉 그대로, 헤더 **사용자 전환**으로 44명 검증. 데이터: 시드 그대로 적재, 공백(월별 M/M·billable 팀 목록·유지보수 계약)은 화면 검증용 가정(파일 주석에 명시 — 시드 정책 확정 시 교체). 기존 `frontend/`는 참고용 보존(예전 기획 반영본 — 사용자 지시). 검증: `tsc`·`vite build` 통과 + Playwright 스모크 13화면(2단계 모달·404 은닉·ADMIN 전환 포함) 콘솔 에러 0. ROADMAP P단계에 항목 등재(pms 트랙 재량 — 카탈로그·접점 무변경이라 공용 결정 불요)
- 미해결: 두 담당 화면 리뷰 → 기획 확인 사항 도출(게이트 P 승인 입력). 커밋·PR은 /wrap-up에서
- 다음 작업: 화면 리뷰 → 게이트 P 승인 → PMS-M0 스캐폴드

### 2026-08-06 — 공용 변경 4건 MCP 담당 확인 완료 (동석 리뷰)
- 완료: 완료/재개·billable·권한 커스텀·유지보수 재설계 4건 **확인 완료** — 공용 결정 기록 등재(host 관점 영향 4건 명시: `update_progress` 거동 변화·billable 모집단·커스텀 403 거절 전달·`list_maintenance_logs` projectId 단순화 불가+`?phase=`). 상위 PRD·PRD-pms·PROGRESS의 "확인 대기" 플래그 일괄 해소. verify.sh pms SKIP(코드 이전 단계) 정상
- 미해결: 게이트 P 승인(사람 — 리뷰 포인트: D4-3 전사 조회·이슈 알림), 시드 적재 잔여 3건, host 측 문서(PRD-host·eval) 반영은 host 트랙 소유
- 다음 작업: 게이트 P 승인 → PMS-M0 스캐폴드

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
