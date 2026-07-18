---
description: Start the next task — check PROGRESS, then present a plan (--loop is ralph.sh autonomous mode)
---

Proceed in this order:

1. **Branch check**: `git branch --show-current`. If this is code work and you are on main, create a work branch per `docs/conventions/git-workflow.md` (doc/harness-only sessions may stay on main).
2. Read `docs/PROGRESS.md` — current state, next task, task queue.
3. Check the milestone's checklist and gate in `docs/ROADMAP.md`.
4. Read the relevant sections of the source documents (implementation work → the matching Step in `PMS_MCP_구현_가이드.md`; requirements → the PRD). For designs spanning two or more modules, use the `architect` agent first.
5. **Before writing code**, present a work plan (files to change, approach, verification method) and get approval.
6. Self-check the plan against the 6 structural principles in CLAUDE.md.

$ARGUMENTS

## Loop mode (`--loop` — for ralph.sh only)

If $ARGUMENTS contains `--loop`, skip the approval wait in step 5 and complete **exactly one** task — the first unchecked item in the task queue:

1. Pick the first `- [ ]` item in the `docs/PROGRESS.md` task queue. Skip items marked `(blocked: ...)` and report why.
2. Implement with TDD (Red→Green→Refactor, following conventions).
3. Confirm `bash scripts/verify.sh` passes. For boundary/domain/security changes, have the `boundary-reviewer` agent review the diff.
4. Persist: mark the queue item `- [x]` + a one-line note. Follow-up work discovered along the way is **only added** as new `- [ ]` items (no sneaky execution — scope-creep guard). `git add -A && git commit` (following commit conventions).
5. Learn: if you found a repo-specific gotcha or repeated mistake, add one line to the CLAUDE.md 'Lessons Learned' section (same commit).
6. Report in 2–3 sentences and **STOP** — never chain into the next task (ralph.sh starts the next iteration in a fresh session).
