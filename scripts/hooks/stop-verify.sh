#!/usr/bin/env bash
# Claude Code Stop 훅 — Java 변경이 있으면 빠른 컴파일 검증 후에만 세션 종료 허용
# exit 2 = 종료 차단 + stderr가 모델에게 전달되어 스스로 수정하게 함
# 방어적 설계: gradlew가 아직 없으면(초기화 전) 조용히 통과한다.
cd "$(dirname "$0")/../.." || exit 0

[ -f ./gradlew ] || compgen -G "./*/gradlew" >/dev/null || exit 0

CHANGED=$(git status --porcelain 2>/dev/null | grep -c '\.java$')
[ "$CHANGED" -eq 0 ] && exit 0

LOG="${TMPDIR:-/tmp}/stop-verify.log"
bash scripts/verify.sh --quick >"$LOG" 2>&1 && exit 0

echo "컴파일 실패 상태로 세션을 종료할 수 없습니다. 오류를 수정하세요:" >&2
tail -30 "$LOG" >&2
exit 2
