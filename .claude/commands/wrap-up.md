---
description: End a session — verify, record, commit
---

End the current session.

1. Run `bash scripts/verify.sh` (scope to `host` or `pms` when only one side
   was touched). If it fails, fix it first — never wrap up red.
2. Update this track's file (`docs/PROGRESS-host.md` or `docs/PROGRESS-pms.md`):
   - 현재 상태 (다음 작업)
   - 세션 로그 entry (형식 template, newest on top)
   Cross-boundary decisions (tool catalog, service API, stage/gate changes)
   → shared `docs/PROGRESS.md` 결정 기록, always with 근거.
3. Update `docs/ROADMAP.md` checkboxes — gate items only with user approval.
4. If CLAUDE.md drifted from reality during this session (commands, stack,
   structure), fix it now, in the same commit.
5. Commit. Milestone work: `M1: implement get_utilization tool` format;
   otherwise `docs:` / `chore:` prefix.
