-- Server-side quiz timer. A quiz may carry a time limit (null = untimed, as before); an attempt snapshots its
-- own deadline when it starts, so editing the quiz's limit later never moves the clock of a sitting in progress.
-- Both columns are nullable and have no default: adding them is a metadata-only change (no table rewrite).
ALTER TABLE quizzes
    ADD COLUMN time_limit_minutes INTEGER,
    ADD CONSTRAINT ck_quizzes_time_limit CHECK (time_limit_minutes IS NULL OR time_limit_minutes BETWEEN 1 AND 600);

ALTER TABLE quiz_attempts
    ADD COLUMN deadline_at TIMESTAMPTZ;

-- The expiry job looks for in-progress attempts whose deadline has passed.
CREATE INDEX idx_quiz_attempts_overdue ON quiz_attempts (deadline_at)
    WHERE status = 'IN_PROGRESS' AND deadline_at IS NOT NULL;
