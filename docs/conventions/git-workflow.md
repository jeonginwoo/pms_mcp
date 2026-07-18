# Git Collaboration Workflow (2 humans + AI agents)

> Model: **GitHub Flow** (main + short-lived branches). Git Flow (develop/release branches) is overkill for a 2-person, vibe-coding-scale project.
> This file is injected every session via CLAUDE.md — AI agents follow the same rules as humans.

## 1. Branch rules

- **main is always green**: no direct commits to main. Exception — minor documentation (`docs:`) and harness (`chore:`) changes may be committed directly.
- **1 task = 1 branch = 1 PR**: one ROADMAP checklist item (or a split of one) per branch. Branch lifetime must not exceed 1–2 days — a longer-lived branch is a sign the task wasn't split enough.
- **Naming**: `<type>/<slug>`. Types: `feat`/`fix`/`chore`/`docs`; milestone work includes the milestone in the slug — e.g. `feat/m1-get-utilization`, `fix/m1-visibility-404`.
- **Starting procedure**: `git switch main && git pull --rebase && git switch -c feat/...`
- **AI agents (Claude) never do code work on main.** Check the branch at session start; if on main, branch first. `ralph.sh` also runs only on a work branch.

## 2. PR & merge rules

- **Merge method: squash merge** — collapse vibe-coding's small WIP commits so main keeps "1 task = 1 commit". The squash commit message follows the commit conventions (`M1: ...` / `chore:` / `docs:`).
- **Merge conditions**: CI (`.github/workflows/ci.yml`) green + **1 review approval from the other person**. PRs that only touch docs/harness may be self-merged once CI is green.
- Before opening a PR, sync with `git pull --rebase origin main`. Delete the branch after merging.
- To reduce review load, attach the `boundary-reviewer` agent verdict and the `/verify` result to the PR description.

## 3. Conflict prevention (dividing work between 2 people)

- **Unit of division = Modulith module**: two people never touch the same module's `internal/` at the same time. When picking tasks, defaulting to different modules is the rule.
- **Shared files** (CLAUDE.md, conventions, module public APIs, PROGRESS.md): tell the other person before changing them.
- **PROGRESS.md conflicts**: session-log entries are appended and both are preserved; for "current state", the most recent session wins.

## 4. Forbidden

- No force push to main (settings.json blocks the agent's `git push --force`).
- Force push to a personal branch only with `--force-with-lease`, and only when you are certain the other person is not using that branch.
- Never weaken or delete tests to get past review/CI — fix the code instead.
