#!/usr/bin/env bash
# Proves the newest backup can actually be restored - without touching production. Downloads the latest encrypted
# pair from S3 (or takes two local files), decrypts it with the private key, restores the database into a throwaway
# Postgres container and lists the photo archive, then checks the result looks like the real thing. Exit code 0 = the
# backup is good. Runs monthly in GitHub Actions (.github/workflows/restore-test.yml); run it by hand any time:
#
#   AGE_IDENTITY=~/keys/math-lms-backup.txt BACKUP_S3_BUCKET=math-lms-backups ./deploy/restore-test.sh
#   AGE_IDENTITY=... ./deploy/restore-test.sh db-….dump.age uploads-….tar.gz.age
#
# Needs docker, age and (for the S3 mode) aws. Nothing here reaches the live server.
set -euo pipefail

: "${AGE_IDENTITY:?set AGE_IDENTITY to the file holding the age PRIVATE key}"
PG_IMAGE="${PG_IMAGE:-pgvector/pgvector:pg16}"   # the same image production runs
WORK="$(mktemp -d)"
CONTAINER="mathlms-restore-test-$$"
cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT
fail() { echo "RESTORE TEST FAILED: $*" >&2; exit 1; }

if [ $# -ge 2 ]; then
  DB_FILE="$1"; UPLOADS_FILE="$2"
else
  : "${BACKUP_S3_BUCKET:?set BACKUP_S3_BUCKET, or pass the two backup files}"
  latest() { aws s3 ls "s3://$BACKUP_S3_BUCKET/backups/" | awk '{print $4}' | grep "^$1-" | sort | tail -1; }
  DB_NAME="$(latest db)"; UPLOADS_NAME="$(latest uploads)"
  [ -n "$DB_NAME" ] && [ -n "$UPLOADS_NAME" ] || fail "no backups found in s3://$BACKUP_S3_BUCKET/backups/"
  aws s3 cp "s3://$BACKUP_S3_BUCKET/backups/$DB_NAME" "$WORK/$DB_NAME" --only-show-errors
  aws s3 cp "s3://$BACKUP_S3_BUCKET/backups/$UPLOADS_NAME" "$WORK/$UPLOADS_NAME" --only-show-errors
  DB_FILE="$WORK/$DB_NAME"; UPLOADS_FILE="$WORK/$UPLOADS_NAME"
fi
echo "testing $(basename "$DB_FILE") + $(basename "$UPLOADS_FILE")"

# A backup older than two days means the nightly job stopped: that is a failure too.
stamp="$(basename "$DB_FILE" | sed -E 's/^db-([0-9]{8})-.*/\1/')"
age_days=$(( ( $(date +%s) - $(date -d "$stamp" +%s) ) / 86400 ))
[ "$age_days" -le 2 ] || fail "the newest backup is $age_days days old - is the cron job running?"

echo "photos: decrypt + list"
photos="$(age -d -i "$AGE_IDENTITY" "$UPLOADS_FILE" | tar tz | grep -vc '/$')"   || fail "the photo archive could not be decrypted or read (wrong key? empty archive?)"
echo "  $photos file(s) in the archive"

echo "database: restore into a throwaway $PG_IMAGE"
docker run -d --name "$CONTAINER" -e POSTGRES_PASSWORD=restore -e POSTGRES_DB=restore "$PG_IMAGE" >/dev/null
for _ in $(seq 1 60); do docker exec "$CONTAINER" pg_isready -U postgres -d restore >/dev/null 2>&1 && break; sleep 1; done
age -d -i "$AGE_IDENTITY" "$DB_FILE" \
  | docker exec -i "$CONTAINER" pg_restore -U postgres -d restore --no-owner --exit-on-error \
  || fail "pg_restore failed"

query() { docker exec "$CONTAINER" psql -U postgres -d restore -tAc "$1"; }
users="$(query 'select count(*) from users')"
attempts="$(query 'select count(*) from quiz_attempts')"
version="$(query 'select max(version::int) from flyway_schema_history where success')"
echo "  users=$users attempts=$attempts schema=V$version"
[ "$users" -gt 0 ] || fail "no users in the restored database"
[ "$version" -ge 40 ] || fail "schema V$version is older than the app expects"

echo "RESTORE TEST OK"
