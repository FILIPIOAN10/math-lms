#!/usr/bin/env bash
# Backup of the production data (Phase 6.7; encrypted + off-server since 2026-10-25): the PostgreSQL database
# (pg_dump, custom format) and the students' solution photos (the `uploads` volume). Those are the only things that
# cannot be rebuilt: grades, accounts and the photos of minors - so a backup is never written unencrypted, and never
# kept only on this server.
#
#   ./deploy/backup.sh [backup_dir]            # default ./backups, keeps 7 days locally (KEEP_DAYS=14 to change)
#
# Needs, in .env.prod (or the environment):
#   BACKUP_AGE_RECIPIENT  the age PUBLIC key ("age1..."). Its private key lives only with you (password manager +
#                         the restore-test secret), never on this server: a stolen server or bucket reads nothing.
#   BACKUP_S3_BUCKET      a bucket of its own (not the files bucket), versioned, with a lifecycle rule (docs/DEPLOY.md §4)
# and the `age` and `aws` commands (deploy/server-setup.sh installs them). Without them it refuses to run.
#
# Cron (daily 03:15):  15 3 * * *  cd /opt/math-lms && ./deploy/backup.sh /var/backups/math-lms >> /var/log/math-lms-backup.log 2>&1
# Restores: deploy/restore.sh (live) and deploy/restore-test.sh (a throwaway copy, monthly in GitHub Actions).
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${ENV_FILE:-.env.prod}"
BACKUP_DIR="${1:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file $ENV_FILE"
STAMP="$(date +%Y%m%d-%H%M%S)"

fail() { echo "[$(date -Is)] BACKUP FAILED: $*" >&2; exit 1; }
setting() { [ -f "$ENV_FILE" ] && grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d "\"'" || true; }

RECIPIENT="${BACKUP_AGE_RECIPIENT:-$(setting BACKUP_AGE_RECIPIENT)}"
BUCKET="${BACKUP_S3_BUCKET:-$(setting BACKUP_S3_BUCKET)}"
[ -n "$RECIPIENT" ] || fail "BACKUP_AGE_RECIPIENT is not set: refusing to write an unencrypted backup of minors' data"
[ -n "$BUCKET" ] || fail "BACKUP_S3_BUCKET is not set: a backup kept only on this disk dies with the disk"
command -v age >/dev/null || fail "the 'age' command is missing (apt-get install age)"
command -v aws >/dev/null || fail "the 'aws' command is missing (apt-get install awscli)"

mkdir -p "$BACKUP_DIR"
DB_FILE="$BACKUP_DIR/db-$STAMP.dump.age"
UPLOADS_FILE="$BACKUP_DIR/uploads-$STAMP.tar.gz.age"
CHECK="$(mktemp -d)"
trap 'rm -rf "$CHECK"' EXIT

# Each stream is checked while it passes (the server cannot decrypt afterwards: it has no private key) and encrypted
# before it reaches the disk. A backup you never checked is a hope, not a backup. Each checker drains what it does not
# read itself (pg_restore --list stops after the table of contents), so tee never writes into a closed pipe.
echo "[$(date -Is)] database -> $DB_FILE"
# POSTGRES_USER / POSTGRES_DB are already set inside the container (inside `sh -c`, so no host shell rewrites them).
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  | tee >({ $COMPOSE exec -T postgres pg_restore --list > /dev/null 2> "$CHECK/db.err"; echo $? > "$CHECK/db.rc"
           cat > /dev/null; }) \
  | age -r "$RECIPIENT" > "$DB_FILE"

echo "[$(date -Is)] uploads  -> $UPLOADS_FILE"
$COMPOSE exec -T backend sh -c 'tar czf - -C /app uploads' \
  | tee >({ tar tz > /dev/null 2> "$CHECK/up.err"; echo $? > "$CHECK/up.rc"; cat > /dev/null; }) \
  | age -r "$RECIPIENT" > "$UPLOADS_FILE"

for check in db up; do # the checkers run beside the pipes: wait for their verdict
  for _ in $(seq 1 120); do [ -s "$CHECK/$check.rc" ] && break; sleep 1; done
  [ "$(cat "$CHECK/$check.rc" 2>/dev/null)" = "0" ] || fail "$check stream unreadable: $(cat "$CHECK/$check.err" 2>/dev/null)"
done
echo "[$(date -Is)] verified and encrypted: $(du -h "$DB_FILE" | cut -f1) database, $(du -h "$UPLOADS_FILE" | cut -f1) photos"

# Off the server; server-side encryption on top of age. The bucket's lifecycle rule decides how long S3 keeps them.
for file in "$DB_FILE" "$UPLOADS_FILE"; do
  aws s3 cp "$file" "s3://$BUCKET/backups/$(basename "$file")" --sse AES256 --only-show-errors
  local_size="$(wc -c < "$file" | tr -d ' ')"
  remote_size="$(aws s3api head-object --bucket "$BUCKET" --key "backups/$(basename "$file")" --query ContentLength --output text)"
  [ "$local_size" = "$remote_size" ] || fail "upload of $(basename "$file") is incomplete ($remote_size of $local_size bytes)"
done
echo "[$(date -Is)] copied to s3://$BUCKET/backups/"

find "$BACKUP_DIR" \( -name 'db-*.dump.age' -o -name 'uploads-*.tar.gz.age' \) -mtime +"$KEEP_DAYS" -print -delete
# Backups made before encryption (plain .dump / .tar.gz) are personal data in clear: say so every day until a human
# deletes them (never deleted here - they may be the only copy of an older day).
plain="$(find "$BACKUP_DIR" \( -name 'db-*.dump' -o -name 'uploads-*.tar.gz' \) | wc -l | tr -d ' ')"
[ "$plain" = "0" ] || echo "[$(date -Is)] WARNING: $plain unencrypted old backup file(s) in $BACKUP_DIR - delete them once an encrypted one is in S3"
