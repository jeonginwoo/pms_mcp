#!/usr/bin/env bash
# Guarded full verification: runs only the stages whose tools exist, so the
# harness works at every growth stage of the project (pre-code → M3).
# Full logs are offloaded to build/last-verify.log — the console prints only
# PASS/FAIL per step. On FAIL read only the failing part (grep/tail).
# Usage: bash scripts/verify.sh [--quick]   (--quick: compile only)
set -u

QUICK="${1:-}"
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

# Backend — appears at M0
if [ -f gradlew ]; then
  step "backend compile" ./gradlew --console=plain -q compileJava compileTestJava
  if [ "$QUICK" != "--quick" ]; then
    step "backend tests" ./gradlew --console=plain -q test
  fi
fi

# Frontend — reconnects at M1 (skipped until node_modules exists)
if [ "$QUICK" != "--quick" ] && [ -f frontend/package.json ] && [ -d frontend/node_modules ]; then
  if grep -q '"test"' frontend/package.json; then
    step "frontend tests" npm --prefix frontend test
  fi
fi

if [ "$ran" -eq 0 ]; then
  echo "SKIP  nothing to verify yet (pre-code stage)"
fi

exit $fail
