# Git Collaboration Workflow (2 humans + AI agents)

> Model: **GitHub Flow** (main + short-lived branches). Git Flow (develop/release
> branches) is overkill for a 2-person project.
> Referenced from the root `CLAUDE.md` — AI agents follow the same rules as humans.

## 1. Branch rules

- **Session start = sync**: run `git pull --rebase` before reading state files
  (PROGRESS/ROADMAP) or planning — the other dev may have pushed since your
  last session. Planning against a stale main surfaces as a rejected push at
  the worst possible time (observed 2026-08-03). `/next` enforces this.
- **main is always green**: no direct commits to main. Exception — minor
  documentation (`docs:`) and harness (`chore:`) changes may be committed directly.
  When pushing such direct commits, if the push is rejected: `git pull --rebase`,
  re-check the incoming commits touched no shared files of yours, then push.
- **1 task = 1 branch = 1 PR**: one ROADMAP checklist item (or a split of one)
  per branch. Branch lifetime must not exceed 1–2 days — a longer-lived branch
  is a sign the task wasn't split enough.
- **Naming**: `<type>/<slug>`. Types: `feat`/`fix`/`chore`/`docs`; milestone work
  includes the milestone in the slug — e.g. `feat/m1-get-utilization`,
  `fix/m1-visibility-404`.
- **Starting procedure**: `git switch main && git pull --rebase && git switch -c feat/...`
- **AI agents (Claude) never do code work on main.** Check the branch at session
  start; if on main, branch first. Automation loops (when added) also run only
  on a work branch.

## 2. PR & merge rules

- **Merge method: squash merge** — collapse small WIP commits so main keeps
  "1 task = 1 commit". The squash commit message follows the commit conventions
  (`M1: ...` / `chore:` / `docs:`).
- **Merge conditions**: CI green (`.github/workflows/ci.yml`, since 2026-08-02)
  + **1 review approval from the other person**.
  PRs that only touch docs/harness may be self-merged once green.
- Before opening a PR, sync with `git pull --rebase origin main`. Delete the
  branch after merging.
- To reduce review load, attach the `reviewer` agent verdict and the
  `bash scripts/verify.sh` result to the PR description.

## 3. Conflict prevention (dividing work between 2 people)

- **First unit of division = ownership area**: `host/` (MCP dev) vs `pms/`
  (PMS dev) — see root `CLAUDE.md` Ownership. Inside `pms/`, the `/mcp` adapter
  module belongs to the MCP dev; the seam is the application service API.
- **Second unit = Modulith module**: two people never touch the same module's
  `internal/` at the same time. When picking tasks, defaulting to different
  modules is the rule.
- **Shared files** (root/scoped CLAUDE.md, conventions, module public APIs,
  shared `docs/PROGRESS.md`): tell the other person before changing them.
- **One promotion, one owner** (2026-08-23, learned the hard way). Promoting a
  contract to a module root is *one* task even though the ROADMAP records it twice —
  once in the pms track ("promote the domain contract") and once in the host track
  ("wire the port"). Reading it as two, both devs wrote
  `maintenance.MaintenanceLookupService` on the same day in different shapes; the
  merge was an add/add conflict and one full contract + impl + tests was thrown
  away. So: **before creating a file in a module root, name who creates it** — the
  shared decision-log entry comes first and it settles the owner, not just the
  design. A ROADMAP item that spans both tracks says which track owns the file.
- **Progress records**: session logs live in per-track files
  (`docs/PROGRESS-host.md` / `docs/PROGRESS-pms.md`), so they don't conflict.
  In the shared `docs/PROGRESS.md`, decision-log entries are appended and both
  are preserved; for shared "current state", the most recent session wins.


## 5. Stacked PRs (learned the hard way, 2026-08-25)

Stacking a branch on another *open* PR is sometimes right — same EPIC, overlapping files, the
second branch's reasoning cites the first. #43 (D1 on D3) worked that way. But the base branch
is consumed the moment its own PR merges, and **merging the stacked PR after that lands it
nowhere**: #46 (EPIC H) was based on `feat/m1-sse`; #45 merged that branch into main first, then
#46 merged into the now-dead `feat/m1-sse`, so EPIC H never reached main. Nothing failed loudly —
GitHub reported both as merged and `main` was green without the work.

Rules:

- **Retarget the stacked PR to `main` the moment its base PR merges**, before merging it. The
  base PR's merge is the trigger; do not wait until the stacked PR's own review finishes.
- **Prefer not to stack.** Branch from `main` and accept the conflict — resolving one
  `NotificationSubscriber` or one docs section is cheaper than an orphaned merge.
- After merging anything, check `git log --oneline main | grep <the commit subject>`. A PR marked
  merged is not evidence that main has the code.

## 4. Forbidden

- No force push to main (settings.json blocks the agent's `git push --force`).
- Force push to a personal branch only with `--force-with-lease`, and only when
  you are certain the other person is not using that branch.
- Never weaken or delete tests to get past review/CI — fix the code instead.
