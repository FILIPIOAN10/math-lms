-- How many times a student may hand a test in; NULL = as often as they like.
-- Tests that already exist stay unlimited, so nothing changes for students the day this ships. New tests start at one
-- attempt (the test dialog's default): the result shows the answers and the barem, so a retake must be the teacher's choice.
ALTER TABLE quizzes ADD COLUMN max_attempts INTEGER;
ALTER TABLE quizzes ADD CONSTRAINT ck_quizzes_max_attempts CHECK (max_attempts BETWEEN 1 AND 20);
