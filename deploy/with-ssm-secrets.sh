#!/usr/bin/env bash
# Security plan, phase 2: runs a command with the production secrets read from AWS SSM Parameter Store
# (/math-lms/prod/*, SecureString) as environment variables, so they never sit in a file on the server's disk.
# docker compose gives the environment priority over --env-file, so .env.prod keeps only the non-secret settings.
#
#   ./deploy/with-ssm-secrets.sh docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build
#
# Needs the `aws` CLI and, on the instance role, ssm:GetParametersByPath on .../parameter/math-lms/prod
# (plus kms:Decrypt if the parameters use your own KMS key instead of the default aws/ssm one).
# Fails - and runs nothing - when SSM cannot be read or a required secret is missing: never starts with half the keys.
set -euo pipefail

PREFIX="${SSM_PREFIX:-/math-lms/prod}"
REQUIRED=(POSTGRES_PASSWORD REDIS_PASSWORD JWT_SECRET EMAIL_TOKEN_SECRET TOTP_ENC_KEY)

[ "$#" -gt 0 ] || { echo "usage: $0 <command...>" >&2; exit 2; }
command -v aws >/dev/null || { echo "with-ssm-secrets: the 'aws' command is missing" >&2; exit 1; }

# Name<TAB>Value per line. The secrets are generated hex/base64 strings: no tabs or newlines inside.
lines="$(aws ssm get-parameters-by-path --path "$PREFIX" --recursive --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text)" || { echo "with-ssm-secrets: cannot read $PREFIX" >&2; exit 1; }

loaded=()
while IFS=$'\t' read -r name value; do
  [ -n "$name" ] || continue
  key="${name##*/}"
  [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "with-ssm-secrets: skipping odd name $name" >&2; continue; }
  export "$key=$value"
  loaded+=("$key")
done <<< "$lines"

for key in "${REQUIRED[@]}"; do
  [ -n "${!key:-}" ] || { echo "with-ssm-secrets: $PREFIX/$key is missing - nothing started" >&2; exit 1; }
done
echo "with-ssm-secrets: ${#loaded[@]} secret(s) from $PREFIX: ${loaded[*]}" >&2

exec "$@"
