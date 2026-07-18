#!/usr/bin/env bash
# Ralph loop — works the remaining task-queue items (- [ ]) in docs/PROGRESS.md, one fresh session each
# Usage: bash scripts/ralph.sh [max-iterations]   (default 3; one /next --loop task per iteration)
#
# Preconditions (do NOT run if any is missing — the loop will mass-produce garbage):
#  (a) queue items are split into commit-sized tasks
#  (b) each item is pre-agreed work that needs no further approval
#  (c) scripts/verify.sh --quick works
#  (d) a human is watching the logs — unattended runs are forbidden
#
# Principle: every iteration = a fresh session (clean context). Previous state is
#            restored entirely from files (docs/PROGRESS.md, git log) — state left
#            only in a conversation cannot be picked up by the next iteration.
#            Run on a work branch only.
set -u
cd "$(dirname "$0")/.."
MAX=${1:-3}

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" = "main" ]; then
  echo "Refusing to run on main. Create a work branch first (docs/conventions/git-workflow.md)."
  exit 1
fi

for i in $(seq 1 "$MAX"); do
  if ! grep -q '^- \[ \]' docs/PROGRESS.md; then
    echo "🎉 Task queue empty — done"
    break
  fi

  echo "──── Ralph iteration $i/$MAX ($BRANCH) ────"
  claude -p "/next --loop" --permission-mode acceptEdits || { echo "Iteration $i failed — stopping"; exit 1; }

  bash scripts/verify.sh --quick || { echo "Verification failed — stopping (manual check needed)"; exit 1; }
done
