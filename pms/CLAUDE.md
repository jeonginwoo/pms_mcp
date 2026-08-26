# pms/ — PMS app (rebuilt 2026-08-21)

Scope: the rebuilt PMS Spring Boot app — domain modules, use-case services,
persistence, and (once auth lands) the web API.
Root `CLAUDE.md` invariants apply on top of this file.

The previous implementation lives in `pms-old/` as **read-only reference**. What
the rebuild left out has since been rebuilt here: the auth chain (2026-08-22) and
the `/mcp` adapter (2026-08-23, on a different promotion design — shared decision
log). `verify.sh`/CI only look at `pms/gradlew`, so `pms-old/` is not verified.

## Structure (2026-08-21 decisions — shared decision log)

- **One domain, one module.** Current modules (8): `person` · `auth` · `project` ·
  `resource` · `notification` · `maintenance` (domain) + `audit` (cross-cutting) +
  `mcp` (inbound adapter) — and `common`, which is wiring, not a module.
  `audit` moved out of `common` on 2026-08-22: it is cross-cutting in *use*, but it
  owns an entity, a repository and use cases of its own, so it does not belong in
  the layer every module depends on. What that surfaced: `AuditSourceResolver` was
  reading the servlet request directly, which `LayerRuleTest` forbids outside
  `controller/` and `common/`. The fix was to move the *reading* down to
  `common/config/RequestPathResolver` and leave audit only the mapping to WEB/MCP —
  not to relax the rule. `mcp` joined on 2026-08-23 as the 7th module (MCP dev —
  see the `/mcp` section below) and `maintenance` as the 8th on the same day
  (EPIC D — reads first, writes from 2026-08-24).
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
  | person (cross-module) | `PersonDirectoryService` · `OrgVisibilityService` · `OrgPermissionService` · `PersonLookupService` · `WorkforceDirectoryService` | **different consumers** — these earn the split |
  | project (cross-module) | `AssignmentDirectoryService` · `ProjectLookupService` · `ProgressCommandService` · **`HandoverPort`** | one more consumer set (resource · `/mcp`) — 2026-08-23. `HandoverPort` is the odd one out: an **inverted** port that project *defines and calls*, implemented by maintenance (D1, 2026-08-25) |
  | resource (cross-module) | `UtilizationLookupService` | `/mcp` only — the web takes `?orgUnitId=`, chat takes a `UtilizationScope` (2026-08-24) |
  | maintenance | `MaintenanceQueryService` · `IssueQueryService` · `ContractCommandService` · `IssueCommandService` | **three axes, not two** — reads are company-wide and take no caller (D4-3); contract writes are gated on the "계약 관리" flag (D2-3); **issue writes take a caller but have no gate** (US-D3 = every logged-in user, 2026-08-24). Same read/write split, same reason, as audit's `AuditTrail` / `AuditQueryService` |

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
  is internal, so the files sitting *directly* in a module directory **are** the
  contract it offers, and that list is the boundary — measured 2026-08-26:
  `person` 13 · `project` 13 · `audit` 6 · `maintenance` 7 · `resource` 5 ·
  **`notification` 0** (`ls <module>/*.java` is the measurement; four of these numbers
  had once gone stale while the file still said "measured", so re-measure rather than
  trust the line).
  **`auth`, `mcp` and `notification` have empty roots**: nothing of theirs crosses.
  `mcp` is the extreme case — it only *consumes*, so deleting it leaves the app running.
  **`notification` joined them on 2026-08-26**, and that is the interesting one: it began
  with five root types because a 2026-08-22 review believed other modules called
  `notify`. Fixing the §8 event direction on 2026-08-24 made that false — subscribers
  import the *publisher's* event, never the reverse — so the five had **zero** external
  importers and belonged in `service/`. The rule cuts both ways: a type sits in the root
  because something outside actually imports it, not because it feels like an API.
  Entities and repositories therefore cannot leave a module, and links are by id.
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

**No scaffolds remain (2026-08-24).** `NotImplementedException` is thrown in
**zero** places — EPIC E writes and EPIC F's F1 removed the last thirteen on the same
day, which satisfies the DoD line "0 sites throwing 501" (PRD-pms §11). The pattern is
worth remembering because it will be used again: a scaffold carried the route, the
contract, the entity, the migration **and the reasoning**, with a `TODO(<AC>)` naming
exactly what was missing, so filling it in was impl body + audit + AC tests. The 501 code
stays absent from the §7 error table on purpose — when the logic lands, the throw site
disappears, and it now has.

**Not started**

- **Auth is switched off** — see the Auth section below. With
  `pms.auth.enabled=false` (the default) the caller arrives as the
  `X-Caller-Person-Id` header and is trusted, so **this app must not be exposed**.
- **2 of the 8 MCP tools** — `get_utilization` · `list_overbooked` still return the
  FR-AI-26 `503` (measured 2026-08-24). Their blockers are gone: EPIC C landed
  2026-08-23 and `resource.UtilizationLookupService` landed 2026-08-24, so what
  remains is adapter wiring (MCP dev). The other six are live, and `update_progress`
  being among them means audit rows now carry `source=MCP` as well as `WEB`.
- **EPIC F's remainder is the SSE route alone (F1-4)** — its `?access_token=` auth and
  access-log masking are one unit; opening the route first leaks tokens into logs. The
  **schedulers landed 2026-08-25** (`@EnableScheduling` is on; `ProjectReminderScheduler` sweeps
  daily at 06:00 for F2 deadline D-7 and F3 completion-overdue). Still-registered gap: **a
  status transition also creates overbooking** (계약대기 → 진행중 pulls that person's assignment
  into the numerator) and §8 has no event for it — that one is a `resource` trigger, unrelated
  to these schedulers.
- **EPIC D is closed (2026-08-25).** Reads (D4, D3-4), contract/site writes (D2), issue
  register/handle/comment (D3) and **handover (D1)** are all live. D1 settled the last open
  question — the **module direction is an inverted port**: `project.HandoverPort` is defined by
  project and implemented by maintenance, so the edge runs **maintenance → project**
  (user decision, shared decision log). What made that a real choice rather than a forced one:
  the two modules were measured to be **siblings that knew nothing of each other**, so no cycle
  compelled a direction. The tiebreaker was where the *latent* dependency already pointed —
  maintenance stores `sourceProjectId` and D4-2 wants the source-project link, so drawing
  project → maintenance would have become a cycle the day maintenance needed a project.
  Two traps worth remembering: the adapter must **not** reuse `ContractCommandService`
  (its `ContractWriteGuard` would stop a PM without the 계약-관리 flag from handing over
  their own project), and the port needs the **caller** — an audit actor guessed from the
  site engineer puts someone who did nothing into an append-only log.
**Nothing from the M1 pms track is left.** A8 (per-project permission matrix) landed
2026-08-26 and closed EPIC A; `?phase=` landed 2026-08-25. What remains in this app is the
registered-gap list in PRD-pms §12 (sales-stage PM removal, person deletion, `billable`
toggle, per-node project counts), none of which is a
ROADMAP checklist item yet.

## Ownership inside this app

- **PMS dev**: domain modules, services, web/API, persistence, seed loading.
- **MCP dev**: the `mcp` adapter module (promoted 2026-08-23) and the wiring of
  each tool onto a domain contract.
- **The read contract a domain publishes at its module root is written by the PMS
  dev**, and *which* dev writes it is settled in the shared decision log **before
  the file lands** (git-workflow §3 "One promotion, one owner"). This line used to
  read as if the MCP dev owned those root files; that ambiguity is what let
  `MaintenanceLookupService` be written twice on 2026-08-23 in two shapes, and one
  full contract + impl + tests was thrown away. Corrected 2026-08-24 to match what
  actually happened for `person`, `project`, `maintenance` and `resource`.
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
  (principle 5). `ProjectLifecycleService.updateProgress` already implements that
  protocol (renamed from `ProgressUpdateService` when the project contracts were
  consolidated on 2026-08-22 — the `/mcp` write tool binds to this name).

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

## `/mcp` adapter (MCP dev — re-promoted 2026-08-23)

The embedded MCP server (principle 2 — never a separate process). Everything lives
under `mcp/internal/`: the 8 `@McpTool` methods, the 9 response records
(`internal/dto/`), `ToolError`, `CallerContext`, and the security chain. The module
root is **empty on purpose** — nothing of `mcp` crosses a boundary, because the
dependency runs one way: **`mcp` → the domain modules' roots**. Delete `mcp/` and
the app still runs; what is left behind is the module list in `ModularityTest`, two
lines in `build.gradle`, and any promoted contract that then has no consumer.

- **A domain joins the catalog by publishing a read contract at its module root**,
  not by hosting tool code. `person/PersonLookupService` (+`PersonIdentity`) is the
  worked example; `project`, `resource` and `maintenance` follow the same shape when
  their turn comes. That widening is a cross-boundary act — shared decision log.
- **The tool response is not the screen response.** `MeView` carries four permission
  flags and `whoami` must not return effective permissions (2026-08-03 decision), and
  the tool DTOs split `orgUnit` into `team` + `division`. So the adapter maps; it
  never forwards a screen DTO (구현_노트 §5).
- **Visibility is never re-implemented here.** `PersonLookupServiceImpl` calls
  `PersonService` so chat and screen answer identically. The adapter only maps.
- **Exception → tool error lives in one place**: `ToolError.from(ApiException)`, whose
  `switch` has **no `default`** — adding an `ErrorCode` breaks this compile on
  purpose, so someone decides what the model should be told. Tools call domain
  services through `ToolCalls.translating(...)`, so no tool has a `try`/`catch`
  (conventions §4). Spring AOP was deliberately *not* used: proxying a tool bean can
  make the MCP annotation scan miss it, and that failure looks like a tool silently
  vanishing from the catalog.
- **Token verification reuses auth's `accessTokenDecoder`** — the policy is identical
  (audience=pms + token_type=access). Do **not** add a second `JwtDecoder` bean:
  `ApiSecurityConfig` injects that one by parameter name, so a second bean makes
  type injection ambiguous and the MCP change breaks web auth.
- **`/mcp` always requires a token**, even with `pms.auth.enabled=false` — the chain
  is `@Order(1)` on `securityMatcher("/mcp/**")`, so the permit-all web chain never
  reaches it (principle 4).
- **`spring.ai.mcp.server.protocol: STREAMABLE` must be written out**, in
  `src/main/resources/application.yml` **and** in the test one (the test file shadows
  main entirely). The field default is STREAMABLE but the autoconfiguration condition
  is `matchIfMissing = false`, so leaving it out silently enables SSE and `POST /mcp`
  becomes 404.
- Unwired tools stay in the catalog and answer the FR-AI-26 `503`. Removing them would
  make the model conclude the capability does not exist and route around it.

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
GET  /api/me/account          name (person) + email·phone (auth), joined over AccountPort (H1-1)
PUT  /api/me/profile          name + email + phone in **one transaction** (H1-2). Duplicate check
                              excludes me — otherwise changing only the phone 409s on my own email
PUT  /api/me/password         current + new (≥8). Lives in **auth**, not person: password never
                              crosses a module boundary (H1-3)
GET  /api/people              visible people (43 seeded, system account hidden) — returns
                              `PersonSummary`: display names **plus** orgUnitId/gradeId/
                              groupId/**billable**/version, because §7 has no person-detail
                              route and this list is what the E2-2 edit form is filled from.
                              The group **name** is deliberately absent — see that DTO's javadoc The module-root
                              `PersonRef` stays narrow — `/mcp` shares it (구현_노트 §5)
GET  /api/people/{id}          404 conceals both absence and out-of-visibility
DELETE /api/people/{id}       200 {success:true}, soft deactivate — "사용자/조직/권한 관리" flag (E2-3)
GET  /api/org-units           tree + counts + deletable, same flag
DELETE /api/org-units/{id}    200 {success:true}, empty nodes only — 409 IN_USE otherwise (E3-3)

POST /api/people              201 + person — creates the login account too (E2-1)
POST /api/org-units           201 + node — arbitrary depth, one company root (E3-1)
GET  /api/grades              `GradeDetail[]` — name, coeff, memberCount, version (same flag)
GET  /api/permission-groups   `PermissionGroupDetail[]` — scope, 4 flags, systemFixed,
                              memberCount, version (same flag). Both used to return
                              `{id,name}`; the admin screens (E4/E5) could not be driven
                              from that, and §7 gives them no detail route (2026-08-24)

--- all live since 2026-08-24 (these were the last 501 scaffolds) ---
PUT  /api/people/{id}              edit name·org·grade·group·**billable** (E2-2) — {version}
                                   required, 409 STALE_VERSION (the check was missing until
                                   2026-08-24). `billable` joined 2026-08-26: 부록 B says the
                                   flag is per-person editable in operation and there was no
                                   write path at all. `capacity` stays out — the monthly
                                   `Capacity` overrides it, so it is a different judgement
PUT  /api/people/{id}/org-unit     move only (E1-1) — 200 carries a **warning** when the
                                   person has live assignments (E1-2, not an error)
PUT  /api/org-units/{id}           rename (E3-2) — duplicate names under one parent are
                                   deliberately allowed (no AC forbids it)
POST/PUT/DELETE /api/grades[/{id}]              grade CRUD (E4) — 409 IN_USE while used
POST/PUT/DELETE /api/permission-groups[/{id}]   group CRUD (E5) — 422 on the fixed group
GET  /api/utilization?month=&personId=&orgUnitId=&overbooked=   (EPIC C)
GET  /api/notifications  ·  PATCH /api/notifications/{id}/read  (F1-3)
GET  /api/notifications/stream     SSE (F1-4). **The only route authenticated by query
                                   param** — EventSource cannot send headers, so
                                   `?access_token=` carries the JWT (auth on) or the personId
                                   (auth off). **Reconnect recovery is a list re-fetch**, not
                                   server replay — `Last-Event-ID` was removed 2026-08-25
                                   (see the SSE section below)
GET/PUT /api/me/notif-prefs        per-type on/off (H1-4) — the controller lives in
                                   notification, not person: the data is notification's
GET  /api/audit                    integrated log, manage flag — 403 is real (G1-3)
GET  /api/projects/{id}/audit      per-project, visibility — 404 is real (G2-2)

POST /api/projects            201 + detail                       (A1)
GET  /api/projects            §7 page envelope, ?page&size&sort   (A3-1)
                              **?phase=SALES|SOLUTION** — derived filter (§5, 2026-08-25).
                              Optional, no permission gate (tabs are public); the filter is
                              applied **after** the visibility judgement, so it can only narrow.
                              An unknown value 400s through Spring's enum conversion — the
                              controller must not re-check it, or the same judgement lives twice
GET  /api/projects/{id}        detail + assignments + derived phase (A3-2·A3-3)
PUT  /api/projects/{id}         edit info + one forward transition (A5)
PUT  /api/projects/{id}/progress   two-step: confirmed=false → true (A2)
                                   진행중 only — else 409 NOT_IN_PROGRESS (A2-9)
PUT  /api/projects/{id}/pm          PM handover, creates the assignment if needed (A6-1)
DELETE /api/projects/{id}           200 {success:true}, soft delete — PM or the "프로젝트 생성" flag (A4)
POST /api/projects/{id}/complete   {version} — needs 진행중 + 100%   (A7-1)
POST /api/projects/{id}/reopen     {version} — 완료 → 진행중, progress=90 (A7-3)
GET  /api/projects/{id}/permissions   role×action matrix: merged value + `editable` per cell
                                   (A8-1). **Read is visibility-wide** — the screen has to draw
                                   the locks; only the write is PM-only
PUT  /api/projects/{id}/permissions   {overrides[], version} — **full replacement** (A8-2).
                                   `overrides: []` restores all defaults, which is why there is
                                   no separate restore route. PM only (A8-3) · a fixed cell
                                   anywhere in the list 422s and changes nothing (A8-4) ·
                                   409 on stale `Project.version` (A8-7)
POST /api/projects/{id}/handover   201 — {계약 필수 정보 + sites[1..n], version} (D1-1).
                                   Contract+Site creation and 완료→유지보수중 are **one
                                   transaction**; 409/400 leave the project 완료 and create no
                                   contract (D1-2·D1-3). PM only (`ProjectAction.HANDOVER`)

POST   /api/projects/{id}/assignments   201 + assignment view      (B1-1)
PUT    /api/assignments/{id}            period + monthly M/M       (B1-4)
DELETE /api/assignments/{id}            200 {success:true}, status=CLOSED (row kept) (B2-1)

GET  /api/maintenance/contracts        ?status&contractor&endedBefore&keyword  (D4-1)
GET  /api/maintenance/contracts/{id}   detail + sites + contacts + issue counts (D4-2)
GET  /api/maintenance/issues           ?status&assigneeId&siteId&contractId    (D3-4)
--- writes live since 2026-08-24 (D2) ---
POST /api/maintenance/contracts            201 — direct registration, no sourceProjectId (D2-1)
PUT  /api/maintenance/contracts/{id}       {version} in the body (D2-2). **No DELETE**:
                                           ending a contract is status=종료
POST /api/maintenance/contracts/{id}/sites 201 + site view, contacts embedded (D2-4)
PUT  /api/maintenance/sites/{id}           {version}; the contacts list is a full
                                           replacement, not a merge (§7 PUT) (D2-4)
--- issue writes live since 2026-08-24 (D3) ---
POST  /api/maintenance/issues              201 — {siteId, type, title} only; the server sets
                                           status=접수, receivedAt=today and the assignee
                                           from the site's engineerId (D3-1), then publishes
                                           `MaintenanceIssueRegistered`
PATCH /api/maintenance/issues/{id}         {version} + status and/or assigneeId (D3-2).
                                           PATCH, not PUT: an omitted field means "leave it",
                                           so **unassigning cannot be expressed** (no AC asks)
POST  /api/maintenance/issues/{id}/comments  201 — **append-only** (D3-3). No version: adding
                                           a comment does not modify the issue
```

Maintenance **reads** take no caller (company-wide, D4-3). **Contract writes** take one and are
gated on the "계약 관리" flag, so `ContractWriteGuard` runs before anything else
(`MaintenanceWriteAuthorizationTest` locks all four routes). **Issue writes take a caller but
have no gate at all** (2026-08-24, D3): US-D3 is `[로그인 사용자 전체]`, so `IssueCommandService`
is the first write contract in this app with no permission guard — the caller id is there for
the *record* (audit actor, comment author), not for a judgement. Reusing `ContractWriteGuard`
here would be wrong, not merely redundant: a 팀원 must be able to file the issue they will work.
**Handover writes take a caller and are gated on the project side** (D1, `ProjectAction.HANDOVER`
= PM only), so `HandoverAdapter` must not re-gate — reusing `ContractCommandService` there would
stop a PM without the 계약-관리 flag from handing over their own project. The caller still crosses
the port, for the audit actor and the event's `handedOverBy`, never for a judgement.

No assignment list route: the project detail already carries them (A3-3).
**Every EPIC-A route now exists** — the last one (`/permissions`) landed 2026-08-26.


## `/api/me/*` belongs to whoever owns the data (settled 2026-08-25, EPIC H)

Four routes hang off `/api/me`, and they live in **three different modules**:

| route | module | why |
|---|---|---|
| `GET /api/me` · `GET /api/me/account` · `PUT /api/me/profile` | person | the name is person's; contact comes over `AccountPort` |
| `PUT /api/me/password` | auth | verifying the current password and hashing the new one are auth's, start to finish |
| `GET`·`PUT /api/me/notif-prefs` | notification | the mutes table is notification's (H1-4, the first case of this rule) |

- **The password never crosses a boundary.** Widening `AccountPort` to carry it would make
  person know about hashes and current-password checks for no reason. The controller sits in
  auth and that is the whole story.
- **Profile is the one that spans two modules** (name in `people`, email/phone in `users`), so
  it goes through the port and both writes share one transaction.
- **Duplicate-email has two questions, not one.** Registration (E2-1) asks "does anyone use
  this?"; profile edit (H1-2) asks "does anyone *else*?". Using the first for the second means
  a user changing only their phone number gets a 409 on their own email — hence
  `emailTakenByOther`.
- **`Person.rename` exists so profile edit cannot touch org/grade/group.** Reusing
  `update(name, orgUnitId, gradeId, groupId, billable)` (the E2-2 admin path) would open a route for
  someone to change their own permission group; passing today's values back is no safer,
  because a new field would then be silently reset by a profile save.

## Optimistic locking on writes (read this before adding a write path)

Two failures showed up together on 2026-08-24 and both are easy to reintroduce.

- **Take the version, then actually check it.** `PUT /api/people/{id}`, `PUT /api/grades/{id}`
  and `PUT /api/permission-groups/{id}` all *accepted* a `version` and never compared it,
  so the last write won silently — while their command DTOs' javadoc had promised
  `409 STALE_VERSION` from the start. Entities now carry `requireVersion(expected)`
  (`Project`, `ProjectAssignment`, `MaintenanceContract`, `MaintenanceSite`, `MaintenanceIssue`, `Person`,
  `Grade`, `PermissionGroup`) and the use case calls it right after loading.
- **Return the flushed version.** A response built inside the same transaction carries the
  *pre-increment* `@Version`, so the client saves, gets `version` back, edits again — and is
  rejected by the lock it never violated. `saveAndFlush` before building the response; project
  already did this and the comment there says why.
- Corollary (conventions §4): **query before you mutate.** A query on a dirty session flushes
  first, so one use case increments `@Version` twice — `moveOrgUnit` was asking for the
  assignment count *after* moving the person.

## Audit (recording and reading both live)

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
  adapter needs no audit wiring. Confirmed end-to-end on 2026-08-23 once
  `update_progress` was wired: the row written through the tool carries `source=MCP`.
- Rows join the caller's transaction: a rolled-back change leaves no history.
- **Two views, two modules, one table** (2026-08-22): `AuditQueryService` in the audit module
  is a plain read with **no permission logic** — it cannot have any, because the
  two views judge differently (manage-org flag vs project visibility) and audit
  may not depend on person or project (that is a cycle). person's
  `AuditViewService` (G1-3) and project's `ProjectQueryService.listAudit` (G2-2) wrap it.
  Both views landed on 2026-08-23 and the screens on 2026-08-24. Their checks were
  implemented **before** the fetch was (the scaffold enforced 403/404 while still
  throwing 501) — that ordering is the point: a permission hole is not something to
  add later.

- **Snapshots round-trip through JSON, so numbers do not come back as `Long`** (measured
  2026-08-24). An id stored in a snapshot reads back as `Integer`, and an assertion written
  as `containsEntry("assigneeId", 902L)` fails against `902`. Compare numerically.
- **An append-only record does not get an audit row.** Issue comments (D3-3) are already
  immutable facts carrying author and timestamp; recording them again would put the same
  fact in two tables, and audit answers "what changed" — the issue did not change.

- **Seeding leaves no audit rows** (measured 2026-08-24): `audit_logs` is empty on a
  freshly seeded database, so the audit screens show nothing until someone writes.
  An older record claimed seeded rows exist as `source=WEB`; that is registered as
  needing confirmation in PRD-pms §12.

## Domain events (new 2026-08-24 — read this before adding one)

Three publishers: `project` publishes `AssignmentChanged`, `ProjectLifecycleChanged` and
`ProjectReminderDue`; `resource` republishes `OverbookingDetected`; `maintenance` publishes
`MaintenanceIssueRegistered` (D3) and `MaintenanceHandedOver` (D1). `notification` subscribes
to all of them and stores.

- **Schedulers publish too — they do not create notifications.** `ProjectReminderScheduler`
  (F2/F3, 2026-08-25) finds the due projects and publishes `ProjectReminderDue`; the subscriber
  stores. Letting a scheduler call `notify` directly would make "no module outside notification
  calls notify" stop being a rule. It lives in `project` because "which projects are D-7" is a
  *project* judgement over project data — putting it in notification would move that judgement
  and force project to open two caller-less queries (`ProjectLookupService` filters by
  visibility, which a scheduler has none of).
- **A capability is not a wiring.** Until 2026-08-25 `ProjectCompleted`/`ProjectReopened` had
  **zero publish sites** and `withdrawUnread` (F3-3 recall) had **no production caller**, so
  reopening a project left its overdue notification in place — while the method was implemented
  *and* unit-tested. Both are now published via `ProjectLifecycleChanged`. When §8 lists an
  event, grep for its publish site before believing the status table.

- **Publish even when nobody will be notified.** `MaintenanceIssueRegistered` fires with a
  null `assigneeId` when the site has no engineer; the *subscriber* decides there is nobody
  to tell. If the publisher skipped the event, the publisher would know who subscribes —
  the exact coupling the edge direction exists to prevent. Same shape as notification
  filtering `AssignmentChanged` down to `Kind.ASSIGNED`.
- **Carry what the message needs.** The event ships `title` and `siteName` so the subscriber
  never queries back; a lookup there would create the `notification → maintenance` edge this
  design avoids.

- **Edges point from subscriber to publisher, always.** The subscriber imports the event
  type from the publisher's module root. The reverse (publisher calls the subscriber) is
  what PRD-pms §3 used to imply, and combined with §8 it was a cycle — corrected on
  2026-08-24, shared decision log. Concretely: **no module outside `notification` calls
  `NotificationService.notify`**; notification's own listener does.
- **Publish inside the transaction, after the audit row.** `@ApplicationModuleListener`
  runs after commit, so a rollback also erases the event — no notification for a change
  that did not happen.
- **Every assignment write publishes, including project creation.** `create()` saves
  assignments directly rather than going through `AssignmentService`, and forgetting it
  there means "nobody learns that a new project overloaded someone" (found by test).
  The audit row for creation is still one (A1-1); the events are per assignment.
- **No publication registry** (`spring-modulith-starter-jpa`) — only
  `spring-modulith-events-api` for the annotation. If the app dies right after commit
  the event is lost; that is accepted because overbooking is also visible on the
  utilization screen. `build.gradle` carries the trigger for revisiting.
- Async needs `@EnableAsync` (on `PmsApplication`), and tests must **await** — asserting
  immediately fails on timing, not on behaviour (`NotificationFlowIntegrationTest` uses
  awaitility).
- **A status transition also creates overbooking** (계약대기 → 진행중 pulls the
  assignment into the numerator) and §8 has no event for it. Registered gap.



## SSE notification stream (new 2026-08-25 — F1-4)

`GET /api/notifications/stream`. Everything for it lives in
`notification/controller/stream/` — controller, emitter registry, pusher, caller resolver.

- **Query-param auth is scoped to this one route.** EventSource cannot set headers, so the
  token rides in `?access_token=`. Widening common's `CallerIdentityResolver` to read the query
  would let **every** route be called that way, and then the token lands in access logs
  everywhere. `StreamCallerResolver` lives beside the stream instead, with two impls chosen by
  `pms.auth.enabled` — JWT when on, personId when off (same trust model as the header).
  `ApiSecurityConfig` must `permitAll` this path: the resource-server filter reads the
  `Authorization` header and there isn't one, so it would 401 every EventSource.
- **Masking is the deployment's job, not the app's** (구현_노트 §6): the Nginx log format has to
  hide `access_token`. What the app owes is never logging it *itself* — that is why no exception
  message in this package contains the token.
- **The emitter is a web type, and that decided the design.** `LayerRuleTest` keeps
  `org.springframework.web..` out of `service`, but storing happens in `service`, and
  service → controller is forbidden. So the service publishes an in-module event
  (`service/dto/NotificationStored`) and `NotificationPusher` (controller side) pushes on
  `AFTER_COMMIT`. Neither layer imports the other.
- **A failed push is swallowed.** Raising it would roll back the storing transaction — the
  notification would vanish *because* it could not be delivered, which is the opposite of F1-4's
  "미연결이면 재연결·재조회 시 반영". The table is the record; the stream is a convenience.
- **Recovery is a list re-fetch, not server replay.** `Last-Event-ID` replay was built and then
  removed on 2026-08-25: the replayed rows broadcast to **all** of that person's connections
  (rewinding another tab's cursor), a truncated replay had no way to say so, and an exception
  mid-replay leaked the emitter. F1-4 asks for "미연결이면 재연결·재조회 시 반영", and the client
  re-fetching the list on every connect **is** that sentence. The client also reconnects itself
  rather than letting the browser do it — the browser reuses the URL, and the token frozen in it
  expires (1h) before the server's own emitter timeout matters, after which `EventSource` fails
  permanently on the non-200.
- **One JVM only** (ASSUMPTION): emitters live in this instance's memory. A second instance
  cannot reach connections held by the first. Fine for the single-instance premise (§3); the day
  it scales, a broker replaces `NotificationStream` and nothing else.

## Schedulers (new 2026-08-25 — F2/F3)

`@EnableScheduling` is on `PmsApplication`. There is exactly one scheduled job:
`project/service/impl/ProjectReminderScheduler.sweep()` at `cron = "0 0 6 * * *"`.

- **One wake-up, two sweeps.** Deadline-near (F2-1) and completion-overdue (F3-1) run in the
  same method on purpose — splitting them into two crons means they can drift, and then "today's
  reminders came half-way".
- **`@Transactional` is required for the publish to be seen.** `@ApplicationModuleListener` runs
  after commit, so a scheduled method without a transaction publishes into nothing.
- **Idempotency lives in the dedupe key, not in the scheduler.** F2-2/F3-2 are enforced by the
  `uq_notification_dedupe` constraint (V7). The two keys differ deliberately: deadline carries
  the **run date** (so it re-reminds daily as the deadline approaches), overdue carries the
  **100%-reached date** (so one stuck cycle reminds once). Reopen *clears* that date and the
  next 100% stamps a new one — which is exactly F3-2's "new cycle".
- **`hundred_reached_at` (V15) is what F3 measures from.** `null` means "not at 100% now" — the
  entity clears it whenever progress drops, so no separate flag exists. The **seed loader passes
  `null`** on purpose: stamping load time would make every seeded 100% project fire a week later.

## Derived values read in two directions (`ProjectPhase`, 2026-08-25)

PRD-pms §5 makes phase a **derived** grouping of status and forbids duplicating the source.
The `?phase=` list filter is what showed that "the source" has to be read **both ways**:
the single-project response goes status → phase, the list filter goes phase → status set.

- **The table is the `default`-less `switch` in `ProjectPhase.of`, and `statuses()` is built
  from it** at class-init. Writing the table twice — once per direction — reproduces the very
  duplication §5 bans, except in code rather than in a column, and then adding a status and
  fixing only one side fails nothing.
- **Do not move the table into the enum constants' arguments.** That reads nicely and loses
  the compile-time forcing: a new `ProjectStatus` then falls silently out of *both* groups
  (treated like 유지보수중) with nothing failing. Same reason `ToolError.from` has no `default`.
  A test cannot replace this — an assertion derived from the implementation
  (`of(s).statuses()` contains `s`) is a tautology. The test holds the §5 table **by hand**.
- **The list DTO carries the derived value too.** Leaving it out means the screen drawing the
  tabs keeps a third copy of the table.

## Project permissions

`ProjectActionPermission` is the single judgement point. Delete is separate
(`requireDelete`): PM **or** the "프로젝트 생성" flag (2026-08-22 decision extending the §4-2
fixed row). The "전 프로젝트 관리" flag substitution (admins count as PM everywhere) is already
handled by `ProjectRoleResolver`.

**Since US-A8 (2026-08-26) the table does not live in that class.** Three pieces, each read
from more than one direction — that is the whole reason they are separate:

- `ProjectPermissionRules` (entity pkg) — the §4-2 **defaults** and the **fixed-cell rule**.
  Editable cells are {PL, 참여자} × {EDIT_INFO, ASSIGN, PROGRESS, COMPLETE_REOPEN}, **eight**.
  PM's whole column and the `HANDOVER` row are fixed (§4-2: the role that edits the matrix must
  not be able to lock itself out, and handover is the irreversible-action safeguard). 조회/삭제
  are fixed too but are not `ProjectAction` values at all — visibility owns one, `requireDelete`
  the other.
- `EffectiveProjectPermissions` (entity pkg) — the **merge**, defaults + stored overrides.
  It exists because the merge has two readers: the write judgement and the A8-1 response. Split
  them and the screen draws a cell as allowed while the server 403s it.
- `ProjectPermissionMatrixResolver` (impl) — loads the overrides **once** and hands over the
  value object. Do not re-query per cell; a matrix is 15 cells.

Two things that are easy to get wrong here:

- **Only deviations are stored.** "No row = the §4-2 default" is the invariant (A8-2). Writing
  defaults as rows means a later change to §4-2 leaves stored rows holding the old default.
- **The lock query must run before the visibility check.** The matrix lives in its own table, so
  `projects` is never dirtied and the optimistic lock would guard nothing — hence
  `findWithVersionBumpById` (`OPTIMISTIC_FORCE_INCREMENT`). But if `requireVisible` loads the
  project first, the lock query meets an **already-managed instance** and the lock mode is not
  applied: measured 2026-08-26, version stayed 0 across saves. Reordering is safe — a missing
  project 404s at the lock query, an invisible one at the very next line.

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
