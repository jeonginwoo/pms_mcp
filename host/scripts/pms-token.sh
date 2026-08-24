#!/usr/bin/env bash
# Mint a real PMS login access token (port 8080) for through-line runs and evals.
#
# /mcp always requires an access token, regardless of pms.auth.enabled (principle 4).
# Usage:
#   bash host/scripts/pms-token.sh 18                  # by person id
#   bash host/scripts/pms-token.sh pro0017@proten.co.kr
#   bash host/scripts/pms-token.sh 18 --refresh        # print the refresh token too
#
# The login email is read from the seed of record (reference/seed/seed_org_proten.sql,
# users table) instead of being duplicated here. The seeded initial password is the
# one fixed in PRD-pms appendix B and is already tracked in that same seed file.
#
# pms generates a fresh signing key on every boot (AuthKeyConfig), so tokens minted
# before a restart stop working — re-run this after restarting pms.
set -euo pipefail

BASE_URL="${PMS_BASE_URL:-http://localhost:8080}"
SEED="$(dirname "$0")/../../reference/seed/seed_org_proten.sql"
PASSWORD="proten1!"

usage() { echo "usage: $0 <personId|email> [--refresh]" >&2; exit 2; }
[ $# -ge 1 ] || usage
WHO="$1"
WANT_REFRESH="${2:-}"

if [[ "$WHO" == *@* ]]; then
  EMAIL="$WHO"
else
  [ -f "$SEED" ] || { echo "seed file not found: $SEED" >&2; exit 1; }
  # users seed row: (id, person_id, 'email', ...) — require the '@' so rows of other
  # tables that happen to share the shape (e.g. org_units) cannot match.
  EMAIL="$(grep -oE "\([0-9]+, *$WHO, *'[^']+@[^']+'" "$SEED" | tail -1 \
           | grep -oE "'[^']+'" | tr -d "'" || true)"
  [ -n "$EMAIL" ] || { echo "no login email for person id $WHO in $SEED" >&2; exit 1; }
fi

RESP="$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"

pick() { printf '%s' "$RESP" | grep -oE "\"$1\":\"[^\"]+\"" | head -1 | cut -d'"' -f4 || true; }

ACCESS="$(pick accessToken)"
if [ -z "$ACCESS" ]; then
  echo "login failed ($EMAIL) — response: $RESP" >&2
  exit 1
fi

if [ "$WANT_REFRESH" = "--refresh" ]; then
  echo "email:   $EMAIL"
  echo "access:  $ACCESS"
  echo "refresh: $(pick refreshToken)"
else
  echo "$ACCESS"
fi
