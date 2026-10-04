-- Q11: a student may have at most ONE in-progress attempt per quiz. The service already resumes the
-- existing one, but two concurrent "start" requests (double click, two tabs) can both see "none"
-- and insert two rows — after which every later start fails, because the lookup expects one row.
-- A partial unique index makes the database the final gate: only IN_PROGRESS rows are constrained,
-- so any number of SUBMITTED / GRADED attempts per (quiz, student) stay allowed.
CREATE UNIQUE INDEX uk_quiz_attempts_one_in_progress
    ON quiz_attempts (quiz_id, student_id)
    WHERE status = 'IN_PROGRESS';
