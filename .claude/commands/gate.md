---
description: Milestone gate check — eval + checklist + recorded human approval
---

Check whether the current milestone's gate can be passed. **Gates are approved by a human — never declare a pass yourself.**

1. Check the gate conditions for the current milestone in `docs/ROADMAP.md` and verify every checklist item is complete. If anything is incomplete, stop the gate check and report.
2. Run the verification required for the gate:
   - **M-1 gate**: confirm scenario S-1~S-3 flows against the mock + tool signatures finalized. Confirm `docs/evals/eval-cases.md` inputs/expected values were updated from the mock results.
   - **G1 (M1)**: run the 30 cases in `docs/evals/eval-cases.md` → produce a result table (case ID / actual output summary / pass / failure code). Confirm zero critical failures (F1~F4) and pass rate ≥90%.
   - **G2 (M2)**: G1 conditions + 2 weeks of read-only operation elapsed + re-run the write cases (D-01~03), zero F3.
3. Final `./gradlew test` run — never cross a gate in a failing state.
4. Present the results to the user as a table and **explicitly request approval**.
5. Once approved, record it in `docs/PROGRESS.md`:
   - Add "GN gate passed — approved (date, pass rate, zero critical)" to the decision log
   - Advance the milestone in the current-state section
6. Commit: `git add -A && git commit -m "M<n>: G<n> 게이트 통과"`

$ARGUMENTS
