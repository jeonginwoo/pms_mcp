# `reference/seed/` — 시드(데모) 데이터

적재 규칙의 원본은 `docs/PRD-pms.md` 부록 B다. 이 파일은 **어느 파일이 정본인지**만 못박는다.

## 파일

| 파일 | 정본? | 무엇을 | 누가 읽나 |
|------|-------|--------|-----------|
| `seed_org_proten.sql` | ✅ **인원 정본** | (주)프로텐 **실제** 조직·직급·권한 그룹·인원 44명 + 계정 | `person/seed/PersonSeedLoader` |
| `projects.json` | ✅ **프로젝트 정본** | 프로젝트 382건 + `assigneeIds` | `project/seed/ProjectSeedLoader` |
| `maintenance.json` | ✅ | 유지보수 계약 105·사이트 157·연락처·이슈 14 (2026 시트 전량 전사) | `maintenance/seed/MaintenanceSeedLoader` |
| `people.json` | ❌ **쓰지 않는다** | 구 **익명** 인원 명부 44명 | 없음 |

## ⚠ `people.json`은 인원 정본이 아니다

`people.json`은 2026-07-31 자산 이관 때 들어온 **익명 명부**다(이름·이메일이 생성값 —
`손윤린`/`lrfmaoy17@`). 구 `pms/`의 `IdentitySeedLoader`가 이것을 읽었으나,
2026-08-22 재구축에서 **실제 명부 `seed_org_proten.sql`로 전환**했고 지금 앱은 그쪽만
적재한다. 두 파일은 **같은 id에 다른 사람이 들어 있고 이름이 하나도 겹치지 않는다**.

- 인원 이름·이메일·직급·소속을 물을 곳은 **`seed_org_proten.sql`뿐이다**
- `projects.json`의 `managerId`·`assigneeIds`는 **id로만** 연결되고 그 id는 양쪽에서
  같은 사람을 가리키지 않는다 — id는 실제 명부 기준으로 읽는다
- **기획 문서(부록 B·`docs/evals/eval-cases.md`·`docs/유저_시나리오.md`)의 인물 이름은
  익명 명부 기준으로 작성됐다.** 그 이름으로 DB를 조회하면 아무것도 나오지 않는다 —
  eval 채점에 쓰기 전에 실제 명부로 재매핑해야 한다(2026-08-23 등재)

지우지 않고 두는 이유: 위 문서들이 그 이름을 194곳에서 참조하고 있어, 재매핑을 하려면
"어느 익명 인물이 어느 id였나"를 되짚을 원본이 필요하다.

## 적재 시 보정 (원본 파일은 수정하지 않는다)

시트를 다시 내려받아도 규칙이 살아남게 하려고, 원본과 도메인이 어긋나는 곳은 로더가
보정한다. 근거는 부록 B.

| 대상 | 보정 | 근거 |
|------|------|------|
| `projects.json` `engagement=OFFSITE` 32건 | → `REMOTE` | OFFSITE 폐지 (2026-08-09 ③⑥) |
| `projects.json` `status=완료`인데 `progress<100` 13건 | → `progress=100` | 완료의 전제가 100% (AC A7-2 · 2026-08-23) |
| `maintenance.json` 계약 `status` `자동연장`·`갱신` 2건 | → `유지` | 모델·MCP 도구가 4종 (2026-08-23) |
| `maintenance.json` 계약 #72 `endDate="2027-11-31"` | → `2027-11-30` | 11월은 30일까지 — 그 달 말일 (2026-08-23) |
| `maintenance.json` 계약 레벨 `serverSpec` | → 사이트로 내림 | 값이 사이트 하나를 가리킨다("태광그룹- …") (2026-08-23) |

## 적재 켜는 법

`pms.seed.path`가 비어 있으면 적재하지 않는다(테스트는 자체 픽스처를 쓴다).
빈 DB로 앱을 띄우면 `application.yml`의 기본값(`../reference/seed`)으로 자동 적재된다.

```bash
docker compose -f pms/docker-compose.yml up -d
(cd pms && ./gradlew bootRun)     # 기동 로그에 적재 건수가 찍힌다
```
