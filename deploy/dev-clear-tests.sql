-- Clears every TEST from the DEV database (never run it on production) and keeps the accounts, so tests can be made
-- again from scratch. Unlike dev-reset.sql it deletes NO account and changes no password.
--
-- Deletes: quizzes with their questions, variants, hints, requirements a)/b), every attempt and answer (with the
-- points per requirement and the photo pages' rows), daily-review answers, homework, AI imports (from textbook photos),
-- queued emails and the teachers' journal. Ids restart at 1.
-- Keeps: every account and its parent link, classes and who is enrolled, books/chapters/exercises, invite links,
-- the teachers' saved grading comments.
--
-- Also clear classes and the content bank (books, chapters, exercises), keeping only the accounts: add  -v clase=1
--
-- Run (from math-lms/):
--   PowerShell: Get-Content -Raw deploy\dev-clear-tests.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
--   with classes: Get-Content -Raw deploy\dev-clear-tests.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms -v clase=1
-- Dry run (shows the result, changes nothing): replace the final COMMIT with ROLLBACK.
-- One transaction: any error rolls everything back. The photo files stay in backend/uploads/ (orphans, harmless).

\set ON_ERROR_STOP on
BEGIN;

TRUNCATE review_answers, response_part_points, response_photos, item_responses, quiz_attempts,
         quiz_item_hints, quiz_options, quiz_item_parts, quiz_items, assignments, ai_imports, quizzes,
         outbox_event, admin_audit_log
         RESTART IDENTITY;

\if :{?clase}
-- users point at invite_links and invite_links at school_classes: unhook, then delete, then restart the ids.
UPDATE users SET invite_link_id = NULL;
DELETE FROM invite_links;
-- DELETE, not TRUNCATE: the (already empty) quiz tables still reference these, and TRUNCATE refuses that.
DELETE FROM exercises;
DELETE FROM chapters;
DELETE FROM books;
DELETE FROM enrollments;
DELETE FROM school_classes;
ALTER TABLE invite_links ALTER COLUMN id RESTART WITH 1;
ALTER TABLE exercises ALTER COLUMN id RESTART WITH 1;
ALTER TABLE chapters ALTER COLUMN id RESTART WITH 1;
ALTER TABLE books ALTER COLUMN id RESTART WITH 1;
ALTER TABLE enrollments ALTER COLUMN id RESTART WITH 1;
ALTER TABLE school_classes ALTER COLUMN id RESTART WITH 1;
\endif

\echo '--- after clearing:'
SELECT email, role, status FROM users ORDER BY role, email;
SELECT (SELECT count(*) FROM quizzes) AS quizzes, (SELECT count(*) FROM quiz_attempts) AS attempts,
       (SELECT count(*) FROM assignments) AS homework, (SELECT count(*) FROM school_classes) AS classes,
       (SELECT count(*) FROM enrollments) AS enrollments;

COMMIT;
