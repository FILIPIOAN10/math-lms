-- Catch-up homework may re-give a test the class already had as homework ("redo it"): one quiz stays whole-class homework
-- of a class at most once, but catch-up homework (given to some students only, V59) is left out of that rule.
ALTER TABLE assignments ADD COLUMN catch_up BOOLEAN NOT NULL DEFAULT FALSE;

-- Homework created since V59 with a student list is catch-up homework.
UPDATE assignments SET catch_up = TRUE WHERE id IN (SELECT assignment_id FROM assignment_students);

ALTER TABLE assignments DROP CONSTRAINT uk_assignments_quiz_class;
CREATE UNIQUE INDEX uk_assignments_quiz_class_whole ON assignments (quiz_id, school_class_id) WHERE NOT catch_up;

-- The old unique constraint also served lookups by quiz (FK, "is this quiz homework anywhere"); keep one for that.
CREATE INDEX idx_assignments_quiz ON assignments (quiz_id);
