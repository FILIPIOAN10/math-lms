-- Removes the data the Selenium E2E suite leaves in the DEV database (never run it on production).
--
-- What counts as E2E data (matched by exact name pattern, nothing else is touched):
--   * quizzes titled  "E2E flow <digits>", "E2E clasa <digits>", "E2E parinte <digits>"
--     + their items, options, hints, attempts, answers and homework
--   * classes named   "E2E clasa <digits>" + their enrollments and homework
-- Kept: every other quiz/class, all users, parent links, your own attempts.
--
-- Run (from math-lms/):
--   PowerShell: Get-Content -Raw deploy\dev-cleanup-e2e.sql | docker exec -i mathlms-postgres psql -U mathlms -d mathlms
--   Git Bash:   docker exec -i mathlms-postgres psql -U mathlms -d mathlms < deploy/dev-cleanup-e2e.sql
-- Dry run (shows the counts, deletes nothing): replace the final COMMIT with ROLLBACK.
-- Everything runs in one transaction: any error rolls the whole cleanup back.

\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE e2e_quiz  ON COMMIT DROP AS
  SELECT id FROM quizzes WHERE title ~ '^E2E (flow|clasa|parinte) [0-9]+$';
CREATE TEMP TABLE e2e_class ON COMMIT DROP AS
  SELECT id FROM school_classes WHERE name ~ '^E2E clasa [0-9]+$';

-- Safety net: an E2E class that holds a real quiz or real content would lose it; stop instead.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM quizzes WHERE school_class_id IN (SELECT id FROM e2e_class)
                                     AND id NOT IN (SELECT id FROM e2e_quiz))
     OR EXISTS (SELECT 1 FROM books WHERE school_class_id IN (SELECT id FROM e2e_class)) THEN
    RAISE EXCEPTION 'An E2E class contains non-E2E quizzes or books - nothing was deleted';
  END IF;
END $$;

\echo '--- will delete:'
SELECT (SELECT count(*) FROM e2e_quiz)  AS quizzes,
       (SELECT count(*) FROM quiz_attempts WHERE quiz_id IN (SELECT id FROM e2e_quiz)) AS attempts,
       (SELECT count(*) FROM e2e_class) AS classes,
       (SELECT count(*) FROM enrollments WHERE school_class_id IN (SELECT id FROM e2e_class)) AS enrollments;

-- Children first: none of these foreign keys cascade except assignments.
DELETE FROM item_responses WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE quiz_id IN (SELECT id FROM e2e_quiz));
DELETE FROM quiz_attempts  WHERE quiz_id IN (SELECT id FROM e2e_quiz);
DELETE FROM quiz_item_hints WHERE item_id IN (SELECT id FROM quiz_items WHERE quiz_id IN (SELECT id FROM e2e_quiz));
DELETE FROM quiz_options   WHERE item_id IN (SELECT id FROM quiz_items WHERE quiz_id IN (SELECT id FROM e2e_quiz));
DELETE FROM quiz_items     WHERE quiz_id IN (SELECT id FROM e2e_quiz);
DELETE FROM assignments    WHERE quiz_id IN (SELECT id FROM e2e_quiz) OR school_class_id IN (SELECT id FROM e2e_class);
DELETE FROM quizzes        WHERE id IN (SELECT id FROM e2e_quiz);
DELETE FROM enrollments    WHERE school_class_id IN (SELECT id FROM e2e_class);
DELETE FROM school_classes WHERE id IN (SELECT id FROM e2e_class);

\echo '--- left after cleanup:'
SELECT (SELECT count(*) FROM quizzes) AS quizzes,
       (SELECT count(*) FROM school_classes) AS classes,
       (SELECT count(*) FROM quiz_attempts) AS attempts;

COMMIT;
