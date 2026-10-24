-- TEACHER role + ownership: a TEACHER sees and changes only the classes and tests they own; an ADMIN still sees
-- everything (and can also teach). One owner per class/test; an ADMIN can hand a class to another teacher.
-- users.role is a plain VARCHAR(20) with no CHECK, so the new 'TEACHER' value needs no change there.
--
-- Everything that exists today goes to the oldest active ADMIN (the school's teacher), so nothing changes for them.
-- A fresh database (CI, tests) has no admin yet: the columns stay NULL, and an owner-less row is visible to ADMINs only.
ALTER TABLE school_classes ADD COLUMN teacher_id BIGINT REFERENCES users (id);
ALTER TABLE quizzes        ADD COLUMN teacher_id BIGINT REFERENCES users (id);

UPDATE school_classes SET teacher_id = (
    SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' AND erased = FALSE ORDER BY id LIMIT 1);
UPDATE quizzes SET teacher_id = (
    SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' AND erased = FALSE ORDER BY id LIMIT 1);

CREATE INDEX idx_school_classes_teacher ON school_classes (teacher_id);
CREATE INDEX idx_quizzes_teacher ON quizzes (teacher_id);
