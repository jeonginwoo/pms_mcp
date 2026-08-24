#!/usr/bin/env bash
# eval 회차 채점 — 규칙층 + LLM-Judge로 판정하고 채점 기록을 남긴다.
#
#   bash host/scripts/eval-grade.sh                  # 가장 최근 회차
#   bash host/scripts/eval-grade.sh 20260824-2143    # 회차 지정
#   bash host/scripts/eval-grade.sh --rules-only     # Judge 없이 규칙층만 (LLM 비용 0)
#
# **실행과 분리돼 있다**: 입력은 회차 기록(transcript.jsonl)뿐이라 pms도 DB도 필요
# 없고, 채점 규칙을 고친 뒤 같은 회차를 다시 매길 수 있다. 회차를 다시 태워야만
# 채점을 고칠 수 있으면 회차 하나가 곧 비용이라 규칙을 못 고치게 된다.
#
# Judge는 claude-opus-5로 돈다(기준 모델 claude-sonnet-5와 다른 모델 — 자기채점 편향
# 회피). 실 LLM 호출이 일어난다: ANTHROPIC_API_KEY 또는 host/application-local.yml 필요.
# 결과는 그 회차 디렉터리에 쌓인다(grades.jsonl · grade.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN_ID=""
JUDGE=true

while [ $# -gt 0 ]; do
  case "$1" in
    --rules-only) JUDGE=false; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    -*) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
    *) RUN_ID="$1"; shift ;;
  esac
done

echo "[grade] 채점${RUN_ID:+ (회차: $RUN_ID)}${JUDGE:+}"
cd "$ROOT/host"
./gradlew test --tests '*EvalGraderIT*' \
  -Deval.grade=true \
  -Deval.judge="$JUDGE" \
  ${RUN_ID:+-Deval.run.id="$RUN_ID"} \
  --rerun-tasks

echo "[grade] 완료 — 회차 디렉터리의 grade.md 확인"
