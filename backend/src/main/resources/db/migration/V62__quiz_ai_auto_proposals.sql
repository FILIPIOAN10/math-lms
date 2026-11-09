-- AI grade proposals are asked for automatically at hand-in only on the tests where the teacher turned that on: every
-- proposal is a call to the strong model, also for papers the teacher grades by hand and for every retake. Off for
-- every existing test too; "Cere propunere AI" on one answer still works everywhere.
ALTER TABLE quizzes ADD COLUMN ai_auto_proposals BOOLEAN NOT NULL DEFAULT FALSE;
