-- Empties the EN chapters of their material on the DEV database, keeping the chapters (never run it on production).
--
-- Deletes: the theory text, the photos' rows and transcripts, the solved models and what the tutor indexed from them.
-- Kept: each chapter's code, title, area, order, parent and book page, the automatic links, the questions' links.
-- The image files stay in backend/uploads/en-chapters: this only touches the database.
--
-- Run (from math-lms/):
--   PowerShell: Get-Content -Raw deploy\dev-clear-en-materia.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
--   Git Bash:   docker exec -i mathlms-postgres psql -U mathlms -d mathlms < deploy/dev-clear-en-materia.sql
-- Dry run (shows the result, changes nothing): replace the final COMMIT with ROLLBACK.
-- One transaction: any error rolls everything back.

\set ON_ERROR_STOP on
BEGIN;

\echo '--- before:'
SELECT (SELECT count(*) FROM en_chapters) AS chapters,
       (SELECT count(*) FROM en_chapters WHERE coalesce(notes, '') <> '') AS with_text,
       (SELECT count(*) FROM en_chapter_photos) AS photos,
       (SELECT count(*) FROM en_chapter_examples) AS models,
       (SELECT count(*) FROM tutor_chunks) AS tutor_chunks,
       (SELECT count(*) FROM tutor_photo_transcripts) AS transcripts;

DELETE FROM tutor_chunks;
DELETE FROM tutor_photo_transcripts;
DELETE FROM en_chapter_examples;
DELETE FROM en_chapter_photos;
UPDATE en_chapters SET notes = NULL;

\echo '--- after:'
SELECT (SELECT count(*) FROM en_chapters) AS chapters,
       (SELECT count(*) FROM en_chapters WHERE coalesce(notes, '') <> '') AS with_text,
       (SELECT count(*) FROM en_chapter_photos) AS photos,
       (SELECT count(*) FROM en_chapter_examples) AS models,
       (SELECT count(*) FROM tutor_chunks) AS tutor_chunks,
       (SELECT count(*) FROM tutor_photo_transcripts) AS transcripts;

COMMIT;
