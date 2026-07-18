#!/usr/bin/env bash
# Claude Code Stop hook — when Java files changed, allow session end only after a quick compile check
# exit 2 = block the stop + stderr is fed back to the model so it fixes the problem itself
# Defensive design: if gradlew doesn't exist yet (pre-init), pass silently.
cd "$(dirname "$0")/../.." || exit 0

[ -f ./gradlew ] || compgen -G "./*/gradlew" >/dev/null || exit 0

CHANGED=$(git status --porcelain 2>/dev/null | grep -c '\.java$')
[ "$CHANGED" -eq 0 ] && exit 0

LOG="${TMPDIR:-/tmp}/stop-verify.log"
bash scripts/verify.sh --quick >"$LOG" 2>&1 && exit 0

echo "Cannot end the session in a failing-compile state. Fix the errors:" >&2
tail -30 "$LOG" >&2
exit 2
