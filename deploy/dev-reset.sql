-- FULL reset of the DEV database to a clean slate for manual testing (never run it on production).
--
-- Deletes ALL content and quiz data: classes, books, chapters, exercises, enrollments, quizzes,
-- items, options, hints, attempts, answers, homework and queued emails (ids restart at 1).
-- Deletes every user EXCEPT the four seed accounts below, which are (re)created ACTIVE with the
-- password Admin123!, no parent link and no Google link:
--   admin@mathlms.local          ADMIN
--   parinte@mathlms.local        PARENT
--   student.activ@mathlms.local  STUDENT
--   student.nou@mathlms.local    STUDENT
--
-- Run (from math-lms/):
--   PowerShell: Get-Content -Raw deploy\dev-reset.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
--   Git Bash:   docker exec -i mathlms-postgres psql -U mathlms -d mathlms < deploy/dev-reset.sql
-- Dry run (shows the result, changes nothing): replace the final COMMIT with ROLLBACK.
-- One transaction: any error rolls everything back.

\set ON_ERROR_STOP on
BEGIN;

TRUNCATE item_responses, quiz_attempts, quiz_item_hints, quiz_options, quiz_items, assignments,
         quizzes, exercises, chapters, books, enrollments, school_classes, outbox_event
         RESTART IDENTITY;

UPDATE users SET parent_id = NULL;
DELETE FROM users WHERE email NOT IN ('admin@mathlms.local', 'parinte@mathlms.local',
                                      'student.activ@mathlms.local', 'student.nou@mathlms.local');

-- BCrypt hash of Admin123! (same as docs/TESTING.md section B).
INSERT INTO users (email, full_name, role, password, email_verified, status, requested_role,
                   parent_id, google_id, erased, erased_at) VALUES
  ('admin@mathlms.local',         'Prof Admin',    'ADMIN',   '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL, NULL, NULL, false, NULL),
  ('parinte@mathlms.local',       'Maria Parinte', 'PARENT',  '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL, NULL, NULL, false, NULL),
  ('student.activ@mathlms.local', 'Ana Student',   'STUDENT', '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL, NULL, NULL, false, NULL),
  ('student.nou@mathlms.local',   'Radu Nou',      'STUDENT', '$2b$10$m4o.4XuUThq9WDeJynErLuS5nirO61RZ8TUGYT6N7uqrGUIjNBouy', true, 'ACTIVE', NULL, NULL, NULL, false, NULL)
ON CONFLICT (email) DO UPDATE SET
  full_name = EXCLUDED.full_name, role = EXCLUDED.role, password = EXCLUDED.password,
  email_verified = true, status = 'ACTIVE', requested_role = NULL, parent_id = NULL,
  google_id = NULL, erased = false, erased_at = NULL;

\echo '--- after reset:'
SELECT email, role, status FROM users ORDER BY role, email;
SELECT (SELECT count(*) FROM school_classes) AS classes, (SELECT count(*) FROM quizzes) AS quizzes,
       (SELECT count(*) FROM quiz_attempts) AS attempts;

COMMIT;
