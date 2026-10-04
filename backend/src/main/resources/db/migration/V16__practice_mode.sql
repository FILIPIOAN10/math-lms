-- E2 practice mode. A quiz opts in with practice_allowed (default false: nothing changes until the teacher ticks it).
-- An attempt is a TEST (timed, graded - everything that exists today) or a PRACTICE (no timer, immediate feedback, never
-- graded). Both ADD COLUMN ... DEFAULT <constant> are metadata-only on PostgreSQL 11+: no table rewrite, existing rows
-- become TEST / not-allowed instantly.
ALTER TABLE quizzes
    ADD COLUMN practice_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE quiz_attempts
    ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'TEST',
    ADD CONSTRAINT ck_quiz_attempts_mode CHECK (mode IN ('TEST', 'PRACTICE')),
    -- a practice has no clock: the timer is a TEST-only rule, enforced here as well as in the code
    ADD CONSTRAINT ck_quiz_attempts_practice_untimed CHECK (mode = 'TEST' OR deadline_at IS NULL);

-- One in-progress attempt per student and quiz - now per MODE, so a student can have a practice open next to a test
-- but never two of the same kind (V12 only constrained the test case). Small table: a plain index swap, no CONCURRENTLY.
DROP INDEX uk_quiz_attempts_one_in_progress;
CREATE UNIQUE INDEX uk_quiz_attempts_one_in_progress
    ON quiz_attempts (quiz_id, student_id, mode)
    WHERE status = 'IN_PROGRESS';
