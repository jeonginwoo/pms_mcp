#!/usr/bin/env bash
# Guarded scoped verification: runs only the stages whose tools exist, so the
# harness works at every growth stage of the project (pre-code → M3).
# Full logs are offloaded to build/last-verify.log — the console prints only
# PASS/FAIL per step. On FAIL read only the failing part (grep/tail).
# Usage: bash scripts/verify.sh [host|pms] [--quick]
#   no scope = verify everything that exists; host/pms = that app only
#   --quick  = compile only
set -u

SCOPE="all"
QUICK=""
for arg in "$@"; do
  case "$arg" in
    host|pms) SCOPE="$arg" ;;
    --quick)  QUICK="--quick" ;;
    *) echo "unknown arg: $arg (usage: verify.sh [host|pms] [--quick])" >&2; exit 1 ;;
  esac
done

LOG="build/last-verify.log"
mkdir -p build
: > "$LOG"

fail=0
ran=0

step() {
  local name="$1"; shift
  ran=1
  echo "== $name ==" >> "$LOG"
  if "$@" >> "$LOG" 2>&1; then
    echo "PASS  $name"
  else
    echo "FAIL  $name  (see $LOG)"
    fail=1
  fi
}

gradle_in() {
  local dir="$1"; shift
  ( cd "$dir" && ./gradlew --console=plain -q "$@" )
}

# Backend apps — appear at M0 (host/ and pms/, each its own Gradle project)
backend() {
  local dir="$1"
  if [ -f "$dir/gradlew" ]; then
    step "$dir compile" gradle_in "$dir" compileJava compileTestJava
    if [ "$QUICK" != "--quick" ]; then
      step "$dir tests" gradle_in "$dir" test
    fi
  fi
}

[ "$SCOPE" != "pms" ]  && backend host
[ "$SCOPE" != "pms" ]  && backend pms-mcp-mock   # M-1 mock MCP server (host track)
[ "$SCOPE" != "host" ] && backend pms

# Frontend (pms scope) — reconnects at M1 (skipped until node_modules exists)
if [ "$SCOPE" != "host" ] && [ "$QUICK" != "--quick" ] \
   && [ -f frontend/package.json ] && [ -d frontend/node_modules ]; then
  if grep -q '"test"' frontend/package.json; then
    step "frontend tests" npm --prefix frontend test
  fi
fi

if [ "$ran" -eq 0 ]; then
  echo "SKIP  nothing to verify yet (scope: $SCOPE, pre-code stage)"
fi

exit $fail
