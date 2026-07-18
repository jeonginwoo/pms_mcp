#!/usr/bin/env bash
# Ralph 루프 — docs/PROGRESS.md '작업 큐'의 남은 작업(- [ ])을 새 세션으로 하나씩 처리
# 사용: bash scripts/ralph.sh [최대반복수]   (기본 3, 반복당 /next --loop 1개 작업)
#
# 전제 (하나라도 없으면 돌리지 말 것 — 루프는 쓰레기를 양산한다):
#  (a) 작업 큐 항목이 커밋 단위로 잘게 쪼개져 있다
#  (b) 각 항목이 이미 계획이 합의된, 승인 없이 진행해도 되는 작업이다
#  (c) scripts/verify.sh --quick이 동작한다
#  (d) 사람이 로그를 지켜보고 있다 — 무인 실행 금지
#
# 원리: 매 반복 = 새 세션(깨끗한 컨텍스트). 이전 상태는 전부 파일
#       (docs/PROGRESS.md·git log)에서 복원되므로, 상태를 대화에만
#       남기면 다음 반복이 이어받지 못한다. 작업 브랜치에서만 돌릴 것.
set -u
cd "$(dirname "$0")/.."
MAX=${1:-3}

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "main" ]; then
  echo "main 브랜치에서는 돌릴 수 없습니다. 작업 브랜치를 만들고 실행하세요. (docs/conventions/git-workflow.md)"
  exit 1
fi

for i in $(seq 1 "$MAX"); do
  if ! grep -q '^- \[ \]' docs/PROGRESS.md; then
    echo "🎉 작업 큐 비어 있음 — 종료"
    break
  fi

  echo "──── Ralph iteration $i/$MAX ($BRANCH) ────"
  claude -p "/next --loop" --permission-mode acceptEdits || { echo "반복 $i 실패 — 중단"; exit 1; }

  bash scripts/verify.sh --quick || { echo "검증 실패 — 중단(수동 확인 필요)"; exit 1; }
done
