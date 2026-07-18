---
description: Wrap up the session — update PROGRESS + commit + branch handling
---

Wrap up the session:

1. Update `docs/PROGRESS.md`:
   - Bring the "현재 상태" (current state) section up to date (milestone, next task, blockers)
   - Add a session-log entry (completed / verified / unresolved / next task)
   - If design decisions were made this session, add them to the decision-log table
   - Reflect the task queue: completed checks (`- [x]`) and discovered follow-ups (`- [ ]`)
2. Check off completed items in `docs/ROADMAP.md`.
3. If this session revealed that CLAUDE.md is wrong (changed commands, structural drift, ...), update CLAUDE.md too. If you found a repo-specific gotcha or repeated mistake, add one line to the CLAUDE.md 'Lessons Learned' section.
4. Commit: `git add -A && git commit` (milestone work `M<n>: ...`, otherwise `chore:`/`docs:`) — review `git diff --staged` before committing.
5. **Branch handling** (docs/conventions/git-workflow.md):
   - On a work branch: push and offer to create a PR (`gh pr create`). Merge happens after the other person's review approval + CI green, via squash.
   - On main: only minor doc/harness changes are allowed here — if code changes are mixed in, offer to move them to a branch.
