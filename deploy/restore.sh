#!/usr/bin/env bash
# Restores a backup made by deploy/backup.sh (Phase 6.7). DESTRUCTIVE: it replaces the CURRENT database
# and the CURRENT photos with the backup. The app is stopped while restoring and started again afterwards.
#
#   AGE_IDENTITY=~/keys/math-lms-backup.txt #     ./deploy/restore.sh backups/db-20261025-031500.dump.age backups/uploads-20261025-031500.tar.gz.age [--yes]
#
# Encrypted backups (.age, since 2026-10-25) need the age PRIVATE key: copy it to the server only for the restore and
# delete it afterwards. Old unencrypted .dump/.tar.gz files still restore as before. Practise first on a throwaway
# copy with deploy/restore-test.sh.
set -euo pipefail

cd "$(dirname "$0")/.."
DB_FILE="${1:?usage: restore.sh <db-*.dump> <uploads-*.tar.gz> [--yes]}"
UPLOADS_FILE="${2:?usage: restore.sh <db-*.dump> <uploads-*.tar.gz> [--yes]}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file ${ENV_FILE:-.env.prod}"

[ -s "$DB_FILE" ] || { echo "missing or empty: $DB_FILE" >&2; exit 1; }
[ -s "$UPLOADS_FILE" ] || { echo "missing or empty: $UPLOADS_FILE" >&2; exit 1; }

# Streams a backup file as it was written: decrypted when it is an .age file.
plain() {
  case "$1" in
    *.age) age -d -i "${AGE_IDENTITY:?set AGE_IDENTITY to the age private key file to restore an encrypted backup}" "$1" ;;
    *) cat "$1" ;;
  esac
}

if [ "${3:-}" != "--yes" ]; then
  read -r -p "This REPLACES the live database and photos with $DB_FILE. Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "aborted"; exit 1; }
fi

echo "stopping the app (database and cache keep running)"
$COMPOSE stop frontend backend

echo "restoring the database"
# --clean --if-exists drops what is there first; --no-owner avoids role mismatches between servers.
$COMPOSE exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < <(plain "$DB_FILE")

echo "restoring the photos"
$COMPOSE run --rm --no-deps -T --entrypoint sh backend -c 'rm -rf /app/uploads/* && tar xzf - -C /app' < <(plain "$UPLOADS_FILE")

echo "starting the app"
$COMPOSE up -d --no-build
echo "done — check: $COMPOSE ps"
