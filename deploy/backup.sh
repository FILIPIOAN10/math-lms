#!/usr/bin/env bash
# Backup of the production data (Phase 6.7): the PostgreSQL database (pg_dump, custom format) and the
# students' solution photos (the `uploads` volume). Those are the only things that cannot be rebuilt:
# grades, accounts and the photos of minors. Run it from anywhere:
#
#   ./deploy/backup.sh [backup_dir]            # default ./backups, keeps 14 days (KEEP_DAYS=30 to change)
#
# Cron (daily 03:15):  15 3 * * *  cd /opt/math-lms && ./deploy/backup.sh /var/backups/math-lms >> /var/log/math-lms-backup.log 2>&1
# Copy the backup directory OFF the server too (another machine / object storage) — a backup on the
# same disk dies with the disk. Test a restore at least once: deploy/restore.sh.
set -euo pipefail

cd "$(dirname "$0")/.."
BACKUP_DIR="${1:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file ${ENV_FILE:-.env.prod}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
DB_FILE="$BACKUP_DIR/db-$STAMP.dump"
UPLOADS_FILE="$BACKUP_DIR/uploads-$STAMP.tar.gz"

echo "[$(date -Is)] database -> $DB_FILE"
# POSTGRES_USER / POSTGRES_DB are already set inside the container.
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$DB_FILE"

echo "[$(date -Is)] uploads  -> $UPLOADS_FILE"
# (inside `sh -c` so the path is never rewritten by a Windows shell / Git Bash)
$COMPOSE exec -T backend sh -c 'tar czf - -C /app uploads' > "$UPLOADS_FILE"

# A backup you never checked is a hope, not a backup: the dump must be readable and the archive intact.
$COMPOSE exec -T postgres pg_restore --list < "$DB_FILE" > /dev/null
tar tz < "$UPLOADS_FILE" > /dev/null   # stdin: a path like C:/... would be read by GNU tar as host:file
echo "[$(date -Is)] verified: $(du -h "$DB_FILE" | cut -f1) database, $(du -h "$UPLOADS_FILE" | cut -f1) photos"

find "$BACKUP_DIR" \( -name 'db-*.dump' -o -name 'uploads-*.tar.gz' \) -mtime +"$KEEP_DAYS" -print -delete
