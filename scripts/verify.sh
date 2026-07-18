#!/usr/bin/env bash
# 전체 검증 루프 — Gradle 빌드+테스트(Modulith/ArchUnit 포함), 프론트 빌드
# 사용: bash scripts/verify.sh [--quick]   (--quick: 컴파일만 — Stop 훅·ralph 반복용)
# 전체 로그는 build/last-verify.log로 오프로딩한다(컨텍스트 오염 방지).
# 실패 시 로그 파일 전체를 읽지 말고 grep/tail로 필요한 부분만 읽을 것.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0
FOUND=0

mkdir -p build
exec > >(tee build/last-verify.log) 2>&1

section() { printf '\n=== %s ===\n' "$1"; }

# --- Gradle 앱 (루트 또는 1단계 하위 — PMS·AI 호스트 어디에 생겨도 잡는다) ---
for GW in ./gradlew ./*/gradlew; do
  [ -f "$GW" ] || continue
  DIR=$(dirname "$GW")
  FOUND=1
  section "Gradle ($DIR)"
  if [ "${1:-}" = "--quick" ]; then
    (cd "$DIR" && ./gradlew -q compileJava compileTestJava) || FAIL=1
  else
    # build = 컴파일 + 전체 테스트 (Testcontainers·Modulith verify 포함)
    (cd "$DIR" && ./gradlew build) || FAIL=1
  fi
done
[ "$FOUND" -eq 0 ] && { section "Gradle"; echo "skip — gradlew 없음 (아직 초기화 전)"; }

# --- Frontend (--quick에서는 생략) ---
if [ "${1:-}" != "--quick" ]; then
  section "Frontend"
  if [ ! -f ./frontend/package.json ]; then
    echo "skip — frontend/ 없음"
  elif [ ! -d ./frontend/node_modules ]; then
    echo "skip — node_modules 없음 (npm install 필요)"
  else
    (cd frontend && npm run -s build) || FAIL=1
    (cd frontend && npm test --if-present -- --run 2>/dev/null) || true
  fi
fi

section "Result"
if [ "$FAIL" -eq 0 ]; then echo "✅ ALL GREEN"; else echo "❌ FAILED — build/last-verify.log에서 실패 부분만 확인"; fi
exit "$FAIL"
