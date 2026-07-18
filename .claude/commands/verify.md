---
description: Verify the current work — tests + principle checks
---

Verify the work just done:

1. Run `bash scripts/verify.sh` — Gradle build + full tests (including Modulith/ArchUnit boundary tests) and the frontend build in one pass. The full log is offloaded to `build/last-verify.log`, so **on failure do not read the whole log — grep/tail only the parts you need.** If it fails, fix and re-run — no completion claims until it passes. (If Gradle/frontend don't exist yet — e.g. the M-1 mock stage — the script reports them as skipped.)
2. If `frontend/` changed, confirm Vitest ran; if the confirmation-card flow (FR-AI-04) was touched, confirm a component test exists.
3. If an MCP tool was created or modified, run the Inspector/curl verification steps, or present the verification commands to the user if you cannot run them.
4. If any of tool name / tool description / system prompt changed, remind: **four-in-one — the four (plus the eval set) are one unit; no merge before a full eval re-run** (gates G1/G2, `docs/evals/eval-cases.md`).
5. Check this change against the 6 items of implementation-guide chapter 13 "commonly missed points" and report the results as a table:
   - Tools never call repositories directly (application services only)
   - User-token passthrough, not a service account
   - The agent loop lives in the AI host
   - No unnecessary write tools exposed
   - Tool output (DB text) is never treated as instructions
   - Spring AI 2.0 API signatures confirmed against the docs
6. Skim the diff (`git diff`) and confirm there are no unintended changes.
