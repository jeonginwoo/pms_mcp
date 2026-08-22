# pms/ — PMS app (rebuilt 2026-08-21)

Scope: the rebuilt PMS Spring Boot app — domain modules, use-case services,
persistence, and (once auth lands) the web API.
Root `CLAUDE.md` invariants apply on top of this file.

The previous implementation lives in `pms-old/` as **read-only reference** — it
holds the gate-M0 output (`/mcp` adapter, login/JWT/JWKS auth chain, identity
seed loader) that the rebuild deliberately left out. `verify.sh`/CI only look at
`pms/gradlew`, so `pms-old/` is not verified.

## Structure (2026-08-21 decisions — shared decision log)

- **One domain, one module.** Current modules: `person` · `auth` · `project` ·
  `resource` · `notification` (domain) + `audit` · `common` (cross-cutting).
  `audit` moved out of `common` on 2026-08-22: it is cross-cutting in *use*, but it
  owns an entity, a repository and use cases of its own, so it does not belong in
  the layer every module depends on. What that surfaced: `AuditSourceResolver` was
  reading the servlet request directly, which `LayerRuleTest` forbids outside
  `controller/` and `common/`. The fix was to move the *reading* down to
  `common/config/RequestPathResolver` and leave audit only the mapping to WEB/MCP —
  not to relax the rule. `maintenance` and the `/mcp` adapter
  module are still added by their owner when that work starts.
  `resource` and `notification` were scaffolded ahead of their logic on
  2026-08-22 by explicit user decision — that suspends the former "no empty
  modules ahead of time" rule **for those two only**; it still holds for
  `maintenance` and for anything else.
- **`auth` split out of `person` (2026-08-22)**: accounts, passwords, JWT and
  the protected chain live in `auth/`. The dependency runs **auth → person**
  (login asks `PersonDirectoryService` whether the person is active); the other
  direction goes through an inverted port, because a direct call both ways is a
  module cycle that `ModularityTest` rejects — **`kr.proten.pms.person.AccountPort`
  is defined by person and implemented by auth**, so person never imports auth
  and the initial password / hashing stay inside auth.
- **Layout per domain module**, three layers in one direction:

  ```
  person/
  ├── controller/            REST controllers + MCP adapters
  │   └── dto/                 request·response records (2026-08-22)
  ├── service/               use-case interfaces = the module contract
  │   ├── impl/                implementations + internal collaborators
  │   │   └── scope/             a strategy family with its own package
  │   ├── dto/                 inputs/outputs the contract exchanges
  │   ├── spi/                 ports this module needs FROM others
  │   └── entity/              JPA entities, VOs, enums
  └── repository/            Spring Data repositories
  ```

  `common/` holds shared wiring only — `config/` (caller identity, request path),
  `exception/` (ErrorCode + the exception types), `web/` (ApiResponse, ApiError,
  PageResponse, and the handler that turns one into the other).

  **Split a folder when kinds mix, not when the count grows.** Request and response
  records go to `controller/dto/`; a strategy family or a self-contained concept
  gets its own package under `impl/` — `impl/scope/` (visibility strategies),
  `impl/requester/` (caller → person+group), `impl/token/` (JWT issue/verify).
  A startup job is not a use case, so the seed loader sits in `person/seed/`.
  Folders with many files of *one* kind (`service/dto/`, `service/entity/`) are
  lists and read fine — leave them alone.
- **One contract per domain concern, not per use case** (2026-08-22). A contract
  earns its own interface when it has **a distinct consumer** or **a distinct
  judgement axis** — not merely because it is a distinct method. The rebuild had
  sliced project into 9 interfaces, 6 of them single-method, 8 of them consumed
  only by `ProjectController`, all of them depending on the same five
  collaborators: the segregation removed no coupling. Now:

  | module | contracts | axis |
  |---|---|---|
  | project | `ProjectQueryService` · `ProjectCommandService` · `ProjectLifecycleService` · `AssignmentService` | visibility read · CRUD · §5 state machine · assignment |
  | person | `PersonService` · `OrgUnitService` · `GradeService` · `PermissionGroupService` · `AuditViewService` | one per managed resource |
  | person (cross-module) | `PersonDirectoryService` · `OrgVisibilityService` · `OrgPermissionService` | **different consumers** — these earn the split |

  Implementations stay decomposed where the work is: `ProjectActionPermission`,
  `ProjectVisibilityService`, `ProjectAuditRecorder`, `ProjectViewFactory`,
  `AssignmentFactory`, `PersonRefFactory`, the `OrgScopeResolver` family — one
  judgement per class, so a use case reads as orchestration (conventions §7).
- **`XxxService` interface in `service/`, `XxxServiceImpl` in `service/impl/`.**
  The **JPA entity is the domain model** — no separate pure-domain twin — so
  invariants live on the entity (no setters, intent-revealing methods, rules in
  factories) and the entity must not depend on `dto/`.
- **A module's public API is its root package** (Modulith's default arrangement —
  adopted 2026-08-22). Every sub-package (`controller/`, `service/`, `repository/`)
  is internal, so the files sitting *directly* in `person/` and `audit/` **are** the
  contract those modules offer — 7 and 6 files, and that list is the boundary.
  `project` · `auth` · `resource` · `notification` have empty roots: nothing of
  theirs crosses. Entities and repositories therefore cannot leave a module, and
  links are by id.
  - There are **no `package-info.java` files**. They only ever carried
    `@NamedInterface`, which was needed because the contracts sat in a sub-package
    instead of the root — the framework default removes the need entirely. Before
    adding one back, move the type to the module root instead.
  - Putting a type in a module root is a deliberate act: it is the only way to widen
    the boundary, and `git diff` shows it as a new file at the top of the module.
- **A scaffold still enforces its authorization** (2026-08-22, from the Codex review).
  A caller without the flag must get **403, not 501** — a 501 tells them the route
  exists and is coming, and the 403 would only appear later when the logic lands.
  What is missing is the logic, not the permission. EPIC E write scaffolds run
  `requireManageOrg` before throwing `NotImplementedException`, and
  `ScaffoldAuthorizationTest` locks both branches (403 without the flag, 501 with it).
- **`common` is not a module** — it is shared wiring (error model, response
  envelope, caller identity) that every module uses and that has no domain to
  encapsulate. Making it a module only records "everything depends on common" over
  and over. `ModularityTest` passes it as the ignore predicate to
  `ApplicationModules.of(…)`.
- **Errors**: `ErrorCode` (common/exception) is the single definition of the §7
  table — the code **and** its HTTP status. Throw an `ApiException` subtype with a
  code; never a string literal. Every response body is `ApiResponse` —
  `{success, data}` or `{success, error}` — including 200-with-no-data for
  deletes; `GET /api/auth/jwks` is the one exception (RFC 7517 shape).
- **Schema is owned by Flyway**: `src/main/resources/db/migration/V__*.sql`,
  `ddl-auto=none`, single schema. Never edit an applied migration — add a new
  version. Entities do not create the schema.
- Enforced by tests: `ModularityTest` (module boundaries + module list),
  `LayerRuleTest` (7 rules — layer direction, contract vs implementation,
  persistence only in `entity`/`repository`, web only in `controller` + common,
  entity independent of dto). When one breaks, fix the structure, not the test.

## What is not here yet

**Scaffolded but empty (2026-08-22)** — the route, contract, entity, migration
and the *reasoning* are in place; the use-case body throws
`NotImplementedException` → **501 `NOT_IMPLEMENTED`**. Every one of them carries
a `TODO(<AC>)` naming exactly what is missing. 501 rather than 500 so a caller
can tell "not built yet" from "broken", and the code is deliberately absent from
the §7 error table: when the logic lands, the throw site disappears.

- **resource** — `GET /api/utilization` (EPIC C). `Capacity` is the per-month
  override; the default stays `Person.capacity`. **Open seam**: the numerator
  needs project to expose "assignment M/M per person per month". Today project
  publishes only `ProjectQueryService` (read) and `AssignmentService` (write),
  and reaching the assignment entity across the boundary is what `ModularityTest`
  forbids — so this needs an application-service API addition, which is a
  cross-boundary decision (shared decision log), not a unilateral edit.
- **notification** — `GET /api/notifications`, `PATCH /{id}/read` (EPIC F).
  Idempotency is already structural: `(recipient_id, dedupe_key)` is unique in
  V7. The SSE route is deliberately not opened yet — it authenticates via
  `?access_token=` (§7) and the access-log masking is part of the same unit.
- **EPIC E writes** — `PUT /api/people/{id}` (E2-2), `PUT /api/people/{id}/org-unit`
  (E1-1), `PUT /api/org-units/{id}` (E3-2), grade CRUD (E4), permission-group
  CRUD (E5).
- **Audit read views** — `GET /api/audit` (G1-3) and `GET /api/projects/{id}/audit`
  (G2-2). **Their permission and visibility checks are real** — only the data
  fetch is missing (see Audit below).

**Not started**

- **Auth is switched off** — see the Auth section below. With
  `pms.auth.enabled=false` (the default) the caller arrives as the
  `X-Caller-Person-Id` header and is trusted, so **this app must not be exposed**.
- **`/mcp` adapter** (MCP dev). Its port contracts attach to the person/project
  service layer — that is what `pms-old`'s temporary seed adapter stood in for.
  Until it lands, every audit row is `source=WEB` (see Audit below).
- **Domain events** — A7-1 `ProjectCompleted`, B2-1 `AssignmentClosed` and the
  rest are still not published: the notification module exists now, but nothing
  consumes them until its logic lands.
- project ACs **A6-3** (role assignment) · **A8** (per-project permission matrix),
  and the `?phase=` list filter.

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
true              auth/controller ApiSecurityConfig     Bearer required, 401 envelope
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
receive it. Add a line to `missingSection()` when a section is added. The accounts
count comes through `AccountPort` since the auth split — the loader stays in person
but no longer touches auth's repository.

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
DELETE /api/people/{id}       200 {success:true}, soft deactivate — "사용자/조직/권한 관리" flag (E2-3)
GET  /api/org-units           tree + counts + deletable, same flag
DELETE /api/org-units/{id}    200 {success:true}, empty nodes only — 409 IN_USE otherwise (E3-3)

POST /api/people              201 + person — creates the login account too (E2-1)
POST /api/org-units           201 + node — arbitrary depth, one company root (E3-1)
GET  /api/grades              form choices for the admin screen (same flag)
GET  /api/permission-groups   form choices for the admin screen (same flag)

--- scaffolded, 501 until the logic lands (2026-08-22) ---
PUT  /api/people/{id}              edit name·org·grade·group (E2-2)
PUT  /api/people/{id}/org-unit     move only (E1-1 — allowed with live assignments)
PUT  /api/org-units/{id}           rename (E3-2)
POST/PUT/DELETE /api/grades[/{id}]              grade CRUD (E4)
POST/PUT/DELETE /api/permission-groups[/{id}]   group CRUD (E5)
GET  /api/utilization?month=&personId=&orgUnitId=&overbooked=   (EPIC C)
GET  /api/notifications  ·  PATCH /api/notifications/{id}/read  (F1-3)
GET  /api/audit                    integrated log, manage flag — 403 is real (G1-3)
GET  /api/projects/{id}/audit      per-project, visibility — 404 is already real (G2-2)

POST /api/projects            201 + detail                       (A1)
GET  /api/projects            §7 page envelope, ?page&size&sort   (A3-1)
GET  /api/projects/{id}        detail + assignments + derived phase (A3-2·A3-3)
PUT  /api/projects/{id}         edit info + one forward transition (A5)
PUT  /api/projects/{id}/progress   two-step: confirmed=false → true (A2)
                                   진행중 only — else 409 NOT_IN_PROGRESS (A2-9)
PUT  /api/projects/{id}/pm          PM handover, creates the assignment if needed (A6-1)
DELETE /api/projects/{id}           200 {success:true}, soft delete — PM or the "프로젝트 생성" flag (A4)
POST /api/projects/{id}/complete   {version} — needs 진행중 + 100%   (A7-1)
POST /api/projects/{id}/reopen     {version} — 완료 → 진행중, progress=90 (A7-3)

POST   /api/projects/{id}/assignments   201 + assignment view      (B1-1)
PUT    /api/assignments/{id}            period + monthly M/M       (B1-4)
DELETE /api/assignments/{id}            200 {success:true}, status=CLOSED (row kept) (B2-1)
```

No assignment list route: the project detail already carries them (A3-3).
Not routed yet, on purpose: A6-3 (`/roles`), A8 (`/permissions`), `?phase=`, and
the SSE stream `GET /api/notifications/stream` (its `?access_token=` auth and the
access-log masking are one unit — opening the route first leaks tokens into logs).

## Audit (recording real · reading scaffolded)

`audit` records every project-scoped change as one append-only row in
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
- **Two views, two modules, one table** (2026-08-22): `AuditQueryService` in the audit module
  is a plain read with **no permission logic** — it cannot have any, because the
  two views judge differently (manage-org flag vs project visibility) and audit
  may not depend on person or project (that is a cycle). person's
  `AuditViewService` (G1-3) and project's `ProjectQueryService.listAudit` (G2-2) wrap it.
  Their **checks are implemented; only the fetch throws 501** — a 403/404 hole is
  not something to add later, and having the guard means the "no leak to a caller
  without the flag" property is under test from now on.

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
