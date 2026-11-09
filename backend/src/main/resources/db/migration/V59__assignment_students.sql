-- Catch-up homework: a homework given to some students of its class only (from the mastery map: the students weak on a
-- chapter). No rows = the whole class, as before; rows = only those students see it, count in it and get its reminder.
-- Both keys cascade: the list means nothing without its homework, and an erased-then-deleted student drops out of it.
CREATE TABLE assignment_students (
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    student_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (assignment_id, student_id)
);

-- The primary key covers lookups by homework; the student's side gets its own index (FK + "my homework").
CREATE INDEX idx_assignment_students_student ON assignment_students (student_id);
