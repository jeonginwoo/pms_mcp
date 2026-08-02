# pms/ — PMS app + embedded /mcp adapter

Scope: the rebuilt PMS Spring Boot app — domain, application services,
persistence, web API, and the embedded `/mcp` MCP-server adapter.
Root `CLAUDE.md` invariants apply on top of this file.

## Ownership inside this app

- **PMS dev**: domain, application services, web/API, persistence, seed loading.
- **MCP dev**: the `/mcp` adapter module only.
- The seam between the two is the application service API — changing it is a
  cross-boundary contract change: record it in the shared decision log in
  `docs/PROGRESS.md`, agreed by both devs.

## Boundary rules (enforced by Modulith/ArchUnit tests from M0)

- The `/mcp` adapter calls application services only — direct repository access
  from the adapter is forbidden (principle 3). Visibility, permissions, and
  404 concealment live in the application layer so they bind web and MCP
  callers alike.
- The MCP server is this embedded adapter — never a separate process (principle 2).
- Auth: user-token passthrough + audience restriction (principle 4).
- Write path: `update_progress` only, with the 2-step confirmation
  (confirmed=false → summary → confirmed=true). No destructive tools (principle 5).

## Workflow

- Verify: `bash scripts/verify.sh pms`
- Session records: `docs/PROGRESS-pms.md` (cross-boundary decisions go to the
  shared decision log in `docs/PROGRESS.md`).
