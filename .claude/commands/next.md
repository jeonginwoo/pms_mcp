---
description: Start a session — restore state, plan before work
---

Start a work session.

(Track already known? Use `/next-pms` or `/next-mcp` instead — they fix the
track and skip the "which track" question, then follow this same procedure.)

0. Sync first: `git pull --rebase` (with a clean tree) **before reading any
   state files** — the other dev may have pushed since the last session
   (`docs/conventions/git-workflow.md` §1). If the tree is dirty, surface that
   to the user instead of pulling over it.
1. Read `docs/PROGRESS.md` (shared state + decision log), then the track file
   for this session's area — `docs/PROGRESS-host.md` or `docs/PROGRESS-pms.md`.
   Ask which track if it isn't obvious from the request.
2. Read `docs/ROADMAP.md` — confirm where the task sits. Never skip a gate;
   gates pass only with explicit user approval, recorded in the PROGRESS
   decision log. Claude never declares a gate passed on its own.
3. Present a plan and wait for user approval before writing code or documents.
4. Unit of work: one ROADMAP checklist item. Split it if it is big.
