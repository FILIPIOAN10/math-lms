-- Empties the DEV database of what was created while testing, keeping EVERY account (never run it on production).
--
-- Deletes: classes, books, chapters, exercises, enrollments, quizzes, items, options, hints, attempts, answers (and
-- their photo pages), daily-review answers, homework, invite links, queued emails, AI imports and the teachers' journal
-- (ids restart at 1).
-- Kept, unlike deploy/dev-reset.sql: all users with their passwords, Google links, 2FA and parent links.
-- Also kept: the EN chapters with their photos and models, the automatic links, the saved comments.
--
-- Run (from math-lms/):
--   PowerShell: Get-Content -Raw deploy\dev-clear-content.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
--   Git Bash:   docker exec -i mathlms-postgres psql -U mathlms -d mathlms < deploy/dev-clear-content.sql
-- Dry run (shows the result, changes nothing): replace the final COMMIT with ROLLBACK.
-- One transaction: any error rolls everything back.

\set ON_ERROR_STOP on
BEGIN;

\echo '--- before:'
SELECT (SELECT count(*) FROM users) AS users, (SELECT count(*) FROM school_classes) AS classes,
       (SELECT count(*) FROM quizzes) AS quizzes, (SELECT count(*) FROM quiz_attempts) AS attempts;

UPDATE users SET invite_link_id = NULL;

TRUNCATE review_answers, response_part_points, response_photos, item_responses, ai_grade_proposals, ai_barem_reads, quiz_attempts,
         quiz_item_hints, quiz_options, quiz_item_parts, quiz_items, assignments,
         quizzes, exercises, chapters, books, enrollments, outbox_event, ai_imports, admin_audit_log
         RESTART IDENTITY;

-- users references invite_links and invite_links references school_classes: TRUNCATE refuses a table that a table
-- outside the list references (users is kept), so these two are deleted and their ids restarted by hand.
DELETE FROM invite_links;
DELETE FROM school_classes;
ALTER TABLE invite_links ALTER COLUMN id RESTART WITH 1;
ALTER TABLE school_classes ALTER COLUMN id RESTART WITH 1;

\echo '--- after:'
SELECT (SELECT count(*) FROM users) AS users, (SELECT count(*) FROM school_classes) AS classes,
       (SELECT count(*) FROM quizzes) AS quizzes, (SELECT count(*) FROM quiz_attempts) AS attempts;

COMMIT;
