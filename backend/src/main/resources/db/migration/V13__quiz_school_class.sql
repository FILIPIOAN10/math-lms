-- Step 2.4b: a quiz may be assigned to ONE school class. NULL = visible to every student, so
-- the quizzes that already exist keep working unchanged (no backfill). Deleting a class that
-- still has quizzes is refused by the FK — move or delete the quizzes first.

ALTER TABLE quizzes ADD COLUMN school_class_id BIGINT REFERENCES school_classes (id);

CREATE INDEX idx_quizzes_school_class_id ON quizzes (school_class_id);
