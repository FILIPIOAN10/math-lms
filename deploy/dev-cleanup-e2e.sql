-- Removes the data the Selenium E2E suite leaves in the DEV database (never run it on production).
--
-- What counts as E2E data (matched by exact name pattern, nothing else is touched):
--   * quizzes titled  "E2E flow|clasa|parinte|publica si tema|nota noua|pe subiect|pe întrebare|profesor|offline|IDOR|dublu submit|reload|editor|elev|teme <digits>"
--     (and their copies, "... (copie)")
--     + their items, options, hints, attempts, answers and homework
--   * classes named   "E2E clasa|invitatie|tema|profesor|clasa offline|clasa IDOR|clasa dublu submit|clasa reload <digits>" + their enrollments, homework, invite links
--     and the books named "E2E carte <digits>" in them, with their chapters and exercises
--   * the journal rows (F1) about any of the above
--   * accounts made by the email tests (e2e.<what>.<n>@mathlms.test) + their enrollments and parent links
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
  SELECT id FROM quizzes WHERE title ~ '^E2E (flow|clasa|parinte|publica si tema|nota noua|pe subiect|pe .ntrebare|profesor|offline|IDOR|dublu submit|reload|editor|elev|teme) [0-9]+( \(copie\))*$';
CREATE TEMP TABLE e2e_class ON COMMIT DROP AS
  SELECT id FROM school_classes WHERE name ~ '^E2E (clasa|invitatie|tema|profesor|clasa offline|clasa IDOR|clasa dublu submit|clasa reload) [0-9]+$';

-- Safety net: an E2E class that holds a real quiz or real content would lose it; stop instead.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM quizzes WHERE school_class_id IN (SELECT id FROM e2e_class)
                                     AND id NOT IN (SELECT id FROM e2e_quiz))
     OR EXISTS (SELECT 1 FROM books WHERE school_class_id IN (SELECT id FROM e2e_class)
                                   AND title !~ '^E2E carte [0-9]+$') THEN
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
-- The content tests' books ("E2E carte <digits>", the only books the guard above lets through), chapters, exercises
DELETE FROM exercises WHERE chapter_id IN (SELECT c.id FROM chapters c JOIN books b ON b.id = c.book_id
                                          WHERE b.school_class_id IN (SELECT id FROM e2e_class));
DELETE FROM chapters WHERE book_id IN (SELECT id FROM books WHERE school_class_id IN (SELECT id FROM e2e_class));
DELETE FROM books WHERE school_class_id IN (SELECT id FROM e2e_class);
DELETE FROM school_classes WHERE id IN (SELECT id FROM e2e_class); -- invite links go with it (ON DELETE CASCADE)
-- The teachers' journal is append-only in the app; on a dev database its E2E rows are just noise.
DELETE FROM admin_audit_log WHERE label ~ 'E2E (flow|clasa|parinte|publica si tema|nota noua|pe subiect|pe .ntrebare|invitatie|tema|profesor|offline|IDOR|dublu submit|reload|editor|elev|teme) [0-9]+';
-- EN lessons the student tests make (code E2E<digits>), when a test failed before removing its own
DELETE FROM en_chapter_examples WHERE chapter_id IN (SELECT id FROM en_chapters WHERE code ~ '^E2E[0-9]+$');
DELETE FROM en_chapters WHERE code ~ '^E2E[0-9]+$';
-- Comment bank (V30): GradeByItemAndJournalE2eTest removes its own snippet, unless it failed half-way
DELETE FROM comment_snippets WHERE text ~ '^E2E atenție la pașii de demonstrație [0-9]+$';
-- Corectură (V43): coloanele reale pentru 2FA
DELETE FROM totp_recovery_codes WHERE user_id IN (SELECT id FROM users WHERE email = 'admin2fa@mathlms.local');
UPDATE users SET totp_secret = NULL, totp_enabled_at = NULL, totp_last_step = NULL WHERE email = 'admin2fa@mathlms.local';

-- Accounts the email tests make (e2e.<what>.<n>@mathlms.test, signed up through Mailpit): their enrollments and parent
-- links first, then the accounts. An erased one is already an anonymised tombstone and stays.
CREATE TEMP TABLE e2e_user ON COMMIT DROP AS
  SELECT id FROM users WHERE email ~ '^e2e\.[a-z0-9]+\.[0-9]+@mathlms\.test$';
DELETE FROM enrollments WHERE student_id IN (SELECT id FROM e2e_user);
UPDATE users SET parent_id = NULL WHERE parent_id IN (SELECT id FROM e2e_user);
DELETE FROM users WHERE id IN (SELECT id FROM e2e_user);

\echo '--- left after cleanup:'
SELECT (SELECT count(*) FROM quizzes) AS quizzes,
       (SELECT count(*) FROM school_classes) AS classes,
       (SELECT count(*) FROM quiz_attempts) AS attempts;

COMMIT;