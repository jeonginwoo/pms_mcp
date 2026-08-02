#!/usr/bin/env bash
# Stop hook: block ending the session while the build is broken.
# exit 0 = allow stop. exit 2 = block stop; stderr is fed back to Claude
# so it can fix the problem itself (block + feedback).
#
# Guards: only act when source files were actually touched — doc-only or
# chat-only sessions must never be blocked. Verification is scoped to the
# app(s) actually touched (host/ vs pms/+frontend/) so one dev's broken
# build never blocks the other's session. verify.sh itself skips stages
# whose toolchain doesn't exist yet, so this passes silently pre-code.

files=$(git status --porcelain 2>/dev/null | grep -E '\.(java|kt|ts|tsx|js)$')
[ -z "$files" ] && exit 0

scopes=""
echo "$files" | grep -q  'host/'            && scopes="$scopes host"
echo "$files" | grep -qE '(pms|frontend)/'  && scopes="$scopes pms"
# touched source outside both app areas — verify everything
[ -z "$scopes" ] && scopes="host pms"

for scope in $scopes; do
  if ! bash scripts/verify.sh "$scope" --quick >/dev/null 2>&1; then
    echo "verify $scope --quick is failing. Fix it before ending the session — read only the failing part of build/last-verify.log." >&2
    exit 2
  fi
done
exit 0
