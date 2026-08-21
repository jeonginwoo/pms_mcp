# pms/ — PMS app (rebuilt 2026-08-21)

Scope: the rebuilt PMS Spring Boot app — domain modules, use-case services,
persistence, and (once auth lands) the web API.
Root `CLAUDE.md` invariants apply on top of this file.

The previous implementation lives in `pms-old/` as **read-only reference** — it
holds the gate-M0 output (`/mcp` adapter, login/JWT/JWKS auth chain, identity
seed loader) that the rebuild deliberately left out. `verify.sh`/CI only look at
`pms/gradlew`, so `pms-old/` is not verified.

## Structure (2026-08-21 decisions — shared decision log)

- **One domain, one module.** Current modules: `person` · `project` · `common`.
  `resource` · `maintenance` · `notification` and the `/mcp` adapter module are
  added by their owner when that work starts — no empty modules ahead of time.
- **Layout per domain module**, three layers in one direction:

  ```
  person/
  ├── controller/            REST controllers + MCP adapters (empty until auth)
  ├── service/               use-case interfaces = the module's contract
  │   ├── impl/                implementations + internal collaborators
  │   ├── dto/                 inputs/outputs the contract exchanges
  │   └── entity/              JPA entities, VOs, enums
  └── repository/            Spring Data repositories
  ```

  `common/` holds cross-cutting concerns only — `config/`, `exception/`, and
  `audit/` (which nests the same three layers: `audit/service{,/impl,/dto,/entity}`
  + `audit/repository`, so the layer rules apply there unchanged).
- **`XxxService` interface in `service/`, `XxxServiceImpl` in `service/impl/`.**
  The **JPA entity is the domain model** — no separate pure-domain twin — so
  invariants live on the entity (no setters, intent-revealing methods, rules in
  factories) and the entity must not depend on `dto/`.
- **What crosses a module boundary** is only what is marked `@NamedInterface`:
  person's `service` + `service/dto`, common's `exception`, `config`, and
  `audit/service` + `audit/service/dto`. `repository/`, `service/entity/`, and
  `service/impl/` are closed, so **entities and repositories cannot leave their
  module** and links are by id. That is why `AuditAction`/`AuditSource` sit next
  to the `AuditTrail` interface rather than in `audit/service/entity/` — the
  recording module must not reach into common's persistence model.
- **Schema is owned by Flyway**: `src/main/resources/db/migration/V__*.sql`,
  `ddl-auto=none`, single schema. Never edit an applied migration — add a new
  version. Entities do not create the schema.
- Enforced by tests: `ModularityTest` (module boundaries + module list),
  `LayerRuleTest` (7 rules — layer direction, contract vs implementation,
  persistence only in `entity`/`repository`, web only in `controller` + common,
  entity independent of dto). When one breaks, fix the structure, not the test.

## What is not here yet

- **Auth is built but switched off** — see the Auth section below. With
  `pms.auth.enabled=false` (the default) the caller arrives as the
  `X-Caller-Person-Id` header and is trusted, so **this app must not be exposed**.
- **`/mcp` adapter** (MCP dev). Its port contracts attach to the person/project
  service layer — that is what `pms-old`'s temporary seed adapter stood in for.
  Until it lands, every audit row is `source=WEB` (see Audit below).
- **Audit read views** — `GET /api/audit` (G1-3) and
  `GET /api/projects/{id}/audit` (G2-2), with their permission flag and 404
  concealment. Rows are already accumulating, so this is additive.
- **Domain events** — A7-1 `ProjectCompleted`, B2-1 `AssignmentClosed` and the
  rest have no consumer yet (no notification module), so they are not published.
- **Utilization recalculation** (B1-3 · C1-4) — no resource module yet.
- project ACs **A6-3** (role assignment) · **A8** (per-project permission matrix),
  people/org create·update (E2-1·E2-2·E3-1·E3-2), and the `?phase=` list filter.

## Ownership inside this app

- **PMS dev**: domain modules, services, web/API, persistence, seed loading.
- **MCP dev**: the `/mcp` adapter module when it is promoted.
- The seam between the two is the service-layer API — changing it is a
  cross-boundary contract change: record it in the shared decision log in
  `docs/PROGRESS.md`, agreed by both devs.

## Boundary rules (root invariants, restated where they bite here)

- The `/mcp` adapter calls services only — direct repository access from the
  adapter is forbidden (principle 3). Visibility, permissions, and 404
  concealment live in the service layer so they bind web and MCP callers alike.
- The MCP server is an embedded adapter — never a separate process (principle 2).
- Auth: user-token passthrough + audience restriction (principle 4).
- Write path: `update_progress` only, with the 2-step confirmation
  (confirmed=false → summary → confirmed=true). No destructive tools
  (principle 5). `ProgressUpdateService` already implements that protocol.

## Auth (built, switched off — `pms.auth.enabled`)

Login, JWT issuance, and the protected chain all exist; the switch decides whether
they are enforced (2026-08-21 decision: build it, use it later).

```
false (default)   common/config OpenSecurityConfig      permit-all chain
                  HeaderCallerIdentityResolver          caller = X-Caller-Person-Id header
true              person/controller ApiSecurityConfig   Bearer required, 401 envelope
                  TokenCallerIdentityResolver           caller = token subject (= personId)
```

`POST /api/auth/login` · `POST /api/auth/refresh` · `GET /api/auth/jwks` are open in
both modes, so tokens can be checked before flipping the switch. Tokens are RS256,
`aud=pms`, `sub=personId`, `token_type` separating access (1h) from refresh (14d) —
cross-use is rejected by validators attached to the decoders.

Turning it on means setting the property, **not** deleting `OpenSecurityConfig` —
delete that and every request 401s, because the security starter defaults to
deny-all. `AuthEnabledIntegrationTest` is the proof the on-path works.

Seeded accounts: login id = the original staff email, initial password `proten1!`
(one shared BCrypt hash; regenerate with `./gradlew printPasswordHash`).

## Seed data

`PersonSeedLoader` runs `reference/seed/seed_org_proten.sql` at startup **only when
`people` is empty** and `pms.seed.path` is set (main `application.yml` sets it;
the test `application.yml` blanks it, because 43 seeded people break fixtures
that assert exact team sizes). Loads the real Proten org: 1 company root + 6
divisions + 10 teams · 9 grades with §appendix-B coefficients · the 4 default
permission groups (팀원 = TEAM scope since 2026-08-22 — see V4 migration) · 43 people + 1 system account · 44 login accounts. After loading
it fails startup if any person points at a missing org unit, grade, or group.

The gate is "any seeded section is empty", not just "people is empty" — when a new
section was added (accounts), an already-seeded database would otherwise never
receive it. Add a line to `missingSection()` when a section is added.

The SQL file is the single source — it is not a Flyway migration on purpose, so
that tests can opt out. Re-running is safe (`ON CONFLICT DO NOTHING`, and the
loader skips a non-empty table).

**Because of that `DO NOTHING`, editing a seeded value does not reach a database
that already has the row.** When reference data has to be *corrected* (not added),
change the seed **and** add a migration that converges existing databases —
`V4__member_group_team_scope.sql` is the worked example.

## Endpoints

```
POST /api/auth/login          email + password -> token pair     (open in both modes)
POST /api/auth/refresh        rotate the pair
GET  /api/auth/jwks           public keys (the /mcp decoder consumes this)

GET  /api/me                  caller identity + group flags — the front-end gates UI on this
GET  /api/people              visible people (43 seeded, system account hidden)
GET  /api/people/{id}          404 conceals both absence and out-of-visibility
DELETE /api/people/{id}       204, soft deactivate — "사용자/조직/권한 관리" flag (E2-3)
GET  /api/org-units           tree + counts + deletable, same flag
DELETE /api/org-units/{id}    204, empty nodes only — 409 IN_USE otherwise (E3-3)

POST /api/people              201 + person — creates the login account too (E2-1)
POST /api/org-units           201 + node — arbitrary depth, one company root (E3-1)
GET  /api/grades              form choices for the admin screen (same flag)
GET  /api/permission-groups   form choices for the admin screen (same flag)

POST /api/projects            201 + detail                       (A1)
GET  /api/projects            §7 page envelope, ?page&size&sort   (A3-1)
GET  /api/projects/{id}        detail + assignments + derived phase (A3-2·A3-3)
PUT  /api/projects/{id}         edit info + one forward transition (A5)
PUT  /api/projects/{id}/progress   two-step: confirmed=false → true (A2)
                                   진행중 only — else 409 NOT_IN_PROGRESS (A2-9)
PUT  /api/projects/{id}/pm          PM handover, creates the assignment if needed (A6-1)
DELETE /api/projects/{id}           204, soft delete — PM or the "프로젝트 생성" flag (A4)
POST /api/projects/{id}/complete   {version} — needs 진행중 + 100%   (A7-1)
POST /api/projects/{id}/reopen     {version} — 완료 → 진행중, progress=90 (A7-3)

POST   /api/projects/{id}/assignments   201 + assignment view      (B1-1)
PUT    /api/assignments/{id}            period + monthly M/M       (B1-4)
DELETE /api/assignments/{id}            204, status=CLOSED (row kept) (B2-1)
```

No assignment list route: the project detail already carries them (A3-3).
Not routed yet, on purpose: A6-3 (`/roles`), A8 (`/permissions`), people/org
create·update (E2-1·E2-2·E3-1·E3-2), the audit read views, and `?phase=`.

## Audit (recording only — 2026-08-21 decision)

`common/audit` records every project-scoped change as one append-only row in
`audit_logs` (Flyway V3). One table is the whole store: the integrated log (G1-3)
and the per-project history (G2-2) are two *read views* of the same rows, which is
why `projectId` is a filter column filled even when `entityId` is an assignment.

- **append-only is structural**: `AuditTrail` has no update/delete method, the
  entity has no setters and every column is `updatable = false`, and there is no
  `@Version`. Don't add a write path — G1-2 forbids it.
- **`ProjectAuditRecorder` decides what and how**: snapshot before the change,
  diff after, store only changed fields; `status` changed → `STATE_CHANGE`
  (§5 transitions only), otherwise `UPDATE`, soft close → `DELETE`. No change,
  no row. Call sites never pick the action themselves.
- **`source`** comes from the request path (`/mcp` → MCP, else WEB), so the MCP
  adapter needs no audit wiring — verify that when it lands.
- Rows join the caller's transaction: a rolled-back change leaves no history.

## Project permissions

`ProjectActionPermission` holds the §4-2 default matrix in one place —
`EDIT_INFO` = PM·PL, `ASSIGN` = PM, `PROGRESS`/`COMPLETE_REOPEN` = every assigned
role. Delete is separate (`requireDelete`): PM **or** the "프로젝트 생성" flag
(2026-08-22 decision extending §4-2 fixed row). The "전 프로젝트 관리" flag substitution (admins count as PM everywhere) is
already handled by `ProjectRoleResolver`. When US-A8 arrives, merge the per-project
overrides here — call sites stay unchanged.

## Workflow

- Conventions: `docs/conventions/java-spring.md` (backend) ·
  `docs/conventions/react-ts.md` (frontend/) ·
  `docs/conventions/git-workflow.md` (branches/PRs).
- Verify: `bash scripts/verify.sh pms`. **Docker is required** — there is no H2
  fallback; every DB-backed test runs on Testcontainers PostgreSQL through the
  shared `PostgresTestBase` singleton container (`PersonSeedLoadIntegrationTest`
  runs its own, since it is the one context with seeding on).
- Session records: `docs/PROGRESS-pms.md` (cross-boundary decisions go to the
  shared decision log in `docs/PROGRESS.md`).
