# Git Collaboration Workflow (2 humans + AI agents)

> Model: **GitHub Flow** (main + short-lived branches). Git Flow (develop/release
> branches) is overkill for a 2-person project.
> Referenced from the root `CLAUDE.md` — AI agents follow the same rules as humans.

## 1. Branch rules

- **main is always green**: no direct commits to main. Exception — minor
  documentation (`docs:`) and harness (`chore:`) changes may be committed directly.
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
- **Merge conditions**: CI green + **1 review approval from the other person**.
  Until CI lands (`.github/workflows/ci.yml` — reserved, added now that the
  remote exists), attach a green `bash scripts/verify.sh` run to the PR instead.
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
- **Progress records**: session logs live in per-track files
  (`docs/PROGRESS-host.md` / `docs/PROGRESS-pms.md`), so they don't conflict.
  In the shared `docs/PROGRESS.md`, decision-log entries are appended and both
  are preserved; for shared "current state", the most recent session wins.

## 4. Forbidden

- No force push to main (settings.json blocks the agent's `git push --force`).
- Force push to a personal branch only with `--force-with-lease`, and only when
  you are certain the other person is not using that branch.
- Never weaken or delete tests to get past review/CI — fix the code instead.
