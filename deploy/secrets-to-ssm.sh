#!/usr/bin/env bash
# Security plan, phase 2: copies the SECRETS of .env.prod into AWS SSM Parameter Store (SecureString, encrypted with KMS),
# under /math-lms/prod/<NAME>. Run once on the server (or again after a rotation); docs/SECRETS.md explains the whole move.
#
#   ./deploy/secrets-to-ssm.sh            # dry run: lists what would be written, never the values
#   ./deploy/secrets-to-ssm.sh --apply    # writes (overwrites) the parameters
#
# Needs the `aws` CLI and, on the instance role, ssm:PutParameter on arn:aws:ssm:<region>:<account>:parameter/math-lms/prod/*.
# Values never appear on a command line (ps would show them): each one goes through a private temporary JSON file.
# Only the names below are secrets; the other settings (ports, feature switches, models) stay in .env.prod.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.prod}"
PREFIX="${SSM_PREFIX:-/math-lms/prod}"
APPLY="${1:-}"

SECRETS=(POSTGRES_PASSWORD REDIS_PASSWORD JWT_SECRET EMAIL_TOKEN_SECRET TOTP_ENC_KEY TOTP_ENC_KEY_PREVIOUS
  GOOGLE_CLIENT_SECRET SMTP_PASS METRICS_TOKEN GRAFANA_PASSWORD OPENAI_API_KEY TUTOR_SERVICE_TOKEN)

fail() { echo "secrets-to-ssm: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || fail "$ENV_FILE not found"
command -v aws >/dev/null || fail "the 'aws' command is missing"
setting() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | sed -E "s/^['\"]//; s/['\"]$//" || true; }

TMP="$(mktemp -d)"
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT

written=0
for name in "${SECRETS[@]}"; do
  value="$(setting "$name")"
  if [ -z "$value" ]; then
    echo "  skip  $name (empty in $ENV_FILE)"
    continue
  fi
  if [ "$APPLY" != "--apply" ]; then
    echo "  would write $PREFIX/$name (${#value} characters)"
    continue
  fi
  python3 - "$PREFIX/$name" "$value" > "$TMP/p.json" <<'PY'
import json, sys
print(json.dumps({"Name": sys.argv[1], "Value": sys.argv[2], "Type": "SecureString", "Overwrite": True}))
PY
  chmod 600 "$TMP/p.json"
  aws ssm put-parameter --cli-input-json "file://$TMP/p.json" --output text --query Version > /dev/null
  rm -f "$TMP/p.json"
  echo "  wrote $PREFIX/$name"
  written=$((written + 1))
done

if [ "$APPLY" != "--apply" ]; then
  echo "Dry run. Run again with --apply to write the parameters."
else
  echo "$written parameter(s) written under $PREFIX. Next: docs/SECRETS.md, step 'Remove the secrets from .env.prod'."
fi
