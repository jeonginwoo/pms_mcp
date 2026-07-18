#!/usr/bin/env bash
# Full verification loop — Gradle build + tests (incl. Modulith/ArchUnit), frontend build
# Usage: bash scripts/verify.sh [--quick]   (--quick: compile only — for the Stop hook / ralph iterations)
# The full log is offloaded to build/last-verify.log (context-pollution guard).
# On failure, do NOT read the whole log file — grep/tail only the parts you need.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0
FOUND=0

mkdir -p build
exec > >(tee build/last-verify.log) 2>&1

section() { printf '\n=== %s ===\n' "$1"; }

# --- Gradle apps (root or one level down — catches the PMS and the AI host wherever they appear) ---
for GW in ./gradlew ./*/gradlew; do
  [ -f "$GW" ] || continue
  DIR=$(dirname "$GW")
  FOUND=1
  section "Gradle ($DIR)"
  if [ "${1:-}" = "--quick" ]; then
    (cd "$DIR" && ./gradlew -q compileJava compileTestJava) || FAIL=1
  else
    # build = compile + full tests (incl. Testcontainers, Modulith verify)
    (cd "$DIR" && ./gradlew build) || FAIL=1
  fi
done
[ "$FOUND" -eq 0 ] && { section "Gradle"; echo "skip — no gradlew (not initialized yet)"; }

# --- Frontend (skipped in --quick mode) ---
if [ "${1:-}" != "--quick" ]; then
  section "Frontend"
  if [ ! -f ./frontend/package.json ]; then
    echo "skip — no frontend/"
  elif [ ! -d ./frontend/node_modules ]; then
    echo "skip — no node_modules (run npm install)"
  else
    (cd frontend && npm run -s build) || FAIL=1
    (cd frontend && npm test --if-present -- --run 2>/dev/null) || true
  fi
fi

section "Result"
if [ "$FAIL" -eq 0 ]; then echo "✅ ALL GREEN"; else echo "❌ FAILED — check only the failing parts of build/last-verify.log"; fi
exit "$FAIL"
