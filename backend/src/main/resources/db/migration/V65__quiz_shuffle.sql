-- Shuffled tests (Moodle "Shuffle questions" / "Shuffle within questions"): each student gets the questions - within
-- each section, so SUBIECTUL I stays before SUBIECTUL II - and the options of each grilă in an order of their own, the
-- same every time they reopen their attempt. Off by default: every existing test keeps its order.
ALTER TABLE quizzes ADD COLUMN shuffle_items BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quizzes ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;

-- Snapshotted on the attempt when it starts, like its deadline: turning the switch while a student is working does not
-- reorder the paper under them.
ALTER TABLE quiz_attempts ADD COLUMN shuffle_items BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_attempts ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT FALSE;
