# Saves the lessons of the local tutor, so a database reset does not mean uploading and paying for every chapter again:
#   - the DB rows: chapter photos, their transcripts, the tutor's chunks with embeddings (3 tables, no other table points at them)
#   - the photo files (backend/uploads/en-chapters), a mirror copy
# Everything goes to tutor/data/snapshots/ (git-ignored). Run it after each batch of new chapters; it overwrites the last one.
#   powershell -ExecutionPolicy Bypass -File tutor\scripts\lessons-backup.ps1
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
$snap = Join-Path $root 'tutor\data\snapshots'
New-Item -ItemType Directory -Force $snap | Out-Null

docker exec mathlms-postgres pg_dump -U mathlms -d mathlms -Fc --data-only `
  -t en_chapter_photos -t tutor_photo_transcripts -t tutor_chunks -f /tmp/tutor-lessons.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
docker cp mathlms-postgres:/tmp/tutor-lessons.dump (Join-Path $snap 'tutor-lessons.dump')
if ($LASTEXITCODE -ne 0) { throw 'docker cp failed' }

robocopy (Join-Path $root 'backend\uploads\en-chapters') (Join-Path $snap 'en-chapters') /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'robocopy failed' }

$counts = docker exec mathlms-postgres psql -U mathlms -d mathlms -At -c "select (select count(*) from en_chapter_photos), (select count(*) from tutor_photo_transcripts), (select count(*) from tutor_chunks)"
Write-Host "Saved in $snap  (photos | transcripts | chunks): $counts"
