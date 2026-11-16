# Puts back what lessons-backup.ps1 saved, after the database was cleared and the backend has started once (so Flyway
# created the tables and the 25 EN chapters). It replaces the chapter photos, transcripts and chunks with the saved ones
# and copies the photo files back if they are gone. It does not touch quizzes, users or the text of the chapter pages.
#   powershell -ExecutionPolicy Bypass -File tutor\scripts\lessons-restore.ps1
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
$snap = Join-Path $root 'tutor\data\snapshots'
$dump = Join-Path $snap 'tutor-lessons.dump'
if (-not (Test-Path $dump)) { throw "No backup at $dump - run lessons-backup.ps1 first" }

# The photo files first: the rows point at them.
$uploads = Join-Path $root 'backend\uploads\en-chapters'
if (Test-Path (Join-Path $snap 'en-chapters')) {
  robocopy (Join-Path $snap 'en-chapters') $uploads /E /XC /XN /XO /NFL /NDL /NJH /NJS /NP | Out-Null
  if ($LASTEXITCODE -ge 8) { throw 'robocopy failed' }
}

docker cp $dump mathlms-postgres:/tmp/tutor-lessons.dump
if ($LASTEXITCODE -ne 0) { throw 'docker cp failed' }
docker exec mathlms-postgres psql -U mathlms -d mathlms -v ON_ERROR_STOP=1 -c "truncate tutor_chunks, tutor_photo_transcripts, en_chapter_photos"
if ($LASTEXITCODE -ne 0) { throw 'truncate failed' }
docker exec mathlms-postgres pg_restore -U mathlms -d mathlms --data-only --exit-on-error /tmp/tutor-lessons.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed (the chapters must have the same ids as when the backup was made)' }
# Identity counters follow the restored ids, so the next upload does not reuse one.
docker exec mathlms-postgres psql -U mathlms -d mathlms -At -c "select setval(pg_get_serial_sequence('en_chapter_photos','id'), (select coalesce(max(id),1) from en_chapter_photos)), setval(pg_get_serial_sequence('tutor_chunks','id'), (select coalesce(max(id),1) from tutor_chunks))" | Out-Null

$counts = docker exec mathlms-postgres psql -U mathlms -d mathlms -At -c "select (select count(*) from en_chapter_photos), (select count(*) from tutor_photo_transcripts), (select count(*) from tutor_chunks)"
Write-Host "Restored (photos | transcripts | chunks): $counts"
Write-Host 'For "Dă-mi una asemănătoare" run once: cd tutor; uv run python -X utf8 -m tutor.similar_index'
