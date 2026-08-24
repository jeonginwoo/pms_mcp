#!/usr/bin/env bash
# eval 회차 실행 — DB 초기화 → pms 기동 → 36케이스 실행 → pms 정리.
#
#   bash host/scripts/eval-run.sh                     # 전량 (DB 초기화 포함)
#   bash host/scripts/eval-run.sh --only A-05,H-01    # 장치 확인용 부분 실행
#   bash host/scripts/eval-run.sh --no-reset --only A-05
#                                                     # 이미 떠 있는 pms에 그대로
#
# **DB 초기화가 이 스크립트의 존재 이유다.** 쓰기 케이스가 8건이라 초기화 없이
# 재실행하면 첫 회와 달라진다 — 2026-08-24 실측이 프로젝트 347을 5%→20%로 바꿔 놓은
# 것이 그 실증이다. 러너도 시작 시점에 audit_logs가 0행인지 확인해 오염된 DB를 거절한다.
#
# 실 LLM 호출이 일어난다: ANTHROPIC_API_KEY 또는 host/application-local.yml이 필요하다.
# 결과는 docs/evals/results/<회차>/ 에 쌓인다(transcript.jsonl · summary.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$ROOT/pms/docker-compose.yml"
PMS_LOG="$ROOT/pms/build/eval-pms.log"
RESET=1
ONLY=""

while [ $# -gt 0 ]; do
  case "$1" in
    --only) ONLY="${2:?--only 뒤에 케이스 id가 필요하다}"; shift 2 ;;
    --no-reset) RESET=0; shift ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

pms_up() { curl -sS -o /dev/null -m 2 "http://localhost:8080/api/auth/login" -X POST \
             -H 'Content-Type: application/json' -d '{}' 2>/dev/null; }
psql_q()  { docker compose -f "$COMPOSE" exec -T postgres \
             psql -U pms -d pms -tAc "$1" 2>/dev/null || true; }

# 앱은 Java 25로 돈다(toolchain). 셸의 `java`는 다른 버전일 수 있으므로 Gradle이
# 아는 툴체인에서 25를 찾아 쓴다 — 경로를 스크립트에 박으면 다른 기계에서 죽는다.
# EVAL_JAVA로 직접 지정할 수도 있다.
java25() {
  if [ -n "${EVAL_JAVA:-}" ]; then echo "$EVAL_JAVA"; return; fi
  if java -version 2>&1 | head -1 | grep -qE '"2[5-9]|"[3-9][0-9]'; then echo java; return; fi
  local home
  home="$( (cd "$ROOT/pms" && ./gradlew -q javaToolchains 2>/dev/null) \
    | tr -d '\r' \
    | awk '/Location:/   { loc=$0; sub(/.*Location:[ \t]*/,"",loc); sub(/[ \t]+$/,"",loc) }
           /Language Version:/ { v=$0; sub(/.*Language Version:[ \t]*/,"",v); sub(/[ \t]+$/,"",v) }
           /Is JDK:/     { j=$0; sub(/.*Is JDK:[ \t]*/,"",j); sub(/[ \t]+$/,"",j)
                           if (v=="25" && j=="true") { print loc; exit } }' )"
  [ -n "$home" ] || { echo "[eval] Java 25를 찾지 못했다 — EVAL_JAVA로 지정하라" >&2; exit 1; }
  echo "${home//\\//}/bin/java"
}

PMS_PID=""
cleanup() {
  if [ -n "$PMS_PID" ]; then
    echo "[eval] pms 정리 (pid $PMS_PID)"
    kill "$PMS_PID" 2>/dev/null || true
    wait "$PMS_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if [ "$RESET" = 1 ]; then
  if pms_up; then
    echo "[eval] 8080에 이미 pms가 떠 있다. DB를 초기화하려면 먼저 내려라" >&2
    echo "       (그대로 쓰려면: --no-reset)" >&2
    exit 1
  fi

  echo "[eval] DB 초기화 — compose 볼륨 삭제 후 재기동"
  docker compose -f "$COMPOSE" down -v
  docker compose -f "$COMPOSE" up -d

  echo -n "[eval] postgres 대기"
  for _ in $(seq 60); do
    [ "$(psql_q 'select 1')" = "1" ] && break
    echo -n .; sleep 1
  done
  echo
  [ "$(psql_q 'select 1')" = "1" ] || { echo "[eval] postgres가 뜨지 않았다" >&2; exit 1; }

  # bootRun이 아니라 jar로 띄운다 — 백그라운드 pid가 실제 자바 프로세스여야
  # 스크립트가 끝날 때 확실히 정리된다(래퍼 pid를 죽이면 앱이 남는다).
  echo "[eval] pms 빌드"
  (cd "$ROOT/pms" && ./gradlew -q bootJar)
  JAR="$(ls -t "$ROOT"/pms/build/libs/*.jar | grep -v -- '-plain.jar' | head -1)"
  JAVA="$(java25)"
  echo "[eval] pms 기동 — 로그: $PMS_LOG"
  mkdir -p "$(dirname "$PMS_LOG")"
  # 로그는 반드시 파일로 — 파이프는 종료까지 버퍼링돼 아무것도 보이지 않는다.
  # 작업 디렉터리는 pms/ 여야 한다: 시드 로더가 `../reference/seed/…`를 상대 경로로 읽는다
  # (bootRun이 그렇게 돌기 때문이다). exec으로 서브셸을 자바로 갈아끼워 $!가 실제 자바 pid가 된다.
  ( cd "$ROOT/pms" && exec "$JAVA" -jar "$JAR" > "$PMS_LOG" 2>&1 ) &
  PMS_PID=$!

  echo -n "[eval] 시드 적재 대기"
  for _ in $(seq 180); do
    kill -0 "$PMS_PID" 2>/dev/null || { echo; echo "[eval] pms가 죽었다 — $PMS_LOG 확인" >&2; exit 1; }
    # HTTP가 뜬 뒤에도 시드 로더가 돌고 있을 수 있어 건수로 판정한다
    [ "$(psql_q 'select count(*) from projects')" = "382" ] && break
    echo -n .; sleep 2
  done
  echo
  SEEDED="$(psql_q 'select count(*) from projects')"
  [ "$SEEDED" = "382" ] || { echo "[eval] 시드 적재 실패 (projects=$SEEDED) — $PMS_LOG 확인" >&2; exit 1; }
else
  pms_up || { echo "[eval] 8080에 pms가 없다 — 먼저 띄워라" >&2; exit 1; }
  echo "[eval] --no-reset: 떠 있는 pms를 그대로 쓴다 (DB 상태는 러너가 검사한다)"
fi

echo "[eval] 러너 실행${ONLY:+ (케이스: $ONLY)}"
cd "$ROOT/host"
if [ -n "$ONLY" ]; then
  ./gradlew test --tests '*EvalRunnerIT*' -Deval.run=true -Deval.only="$ONLY" --rerun-tasks
else
  ./gradlew test --tests '*EvalRunnerIT*' -Deval.run=true --rerun-tasks
fi

echo "[eval] 완료 — docs/evals/results/ 확인"
