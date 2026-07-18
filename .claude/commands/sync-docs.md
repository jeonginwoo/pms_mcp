---
description: Sync documents after a decision change — CLAUDE.md first, remove drift
---

A design or convention decision changed: $ARGUMENTS

1. Find every place the old decision is recorded: `CLAUDE.md`, `docs/conventions/*`, `기술_선택_근거.md`, `PMS_MCP_구현_가이드.md`, `PMS_AI기능_PRD.md`, `docs/ROADMAP.md`, `docs/evals/eval-cases.md`.
2. **Update CLAUDE.md first** — it is the operative document and rank 1 in the hierarchy of truth the agent reads every session. If an outdated document physically cannot be fixed (external reference material, etc.), state "what supersedes what" in CLAUDE.md.
3. Update the remaining documents, preserving each document's existing format (including a dated revision note).
4. Add date / decision / rationale to the `docs/PROGRESS.md` decision-log table.
5. If the decision touches AI tools or prompts: **tool name, tool description, system prompt, and eval set are four-in-one** — update all four together and include a full eval re-run in the plan.
6. Commit: `docs: <decision> 반영`.
