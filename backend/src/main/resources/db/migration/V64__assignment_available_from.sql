-- Scheduled release of a homework (Google Classroom "Programează", Canvas "Available from"): until available_from the
-- homework - and a test given only by it - is hidden from its students. NULL = open since it was given (every existing
-- row). A release on or after the deadline would never be workable, so it cannot exist.
ALTER TABLE assignments ADD COLUMN available_from TIMESTAMPTZ;

ALTER TABLE assignments ADD CONSTRAINT ck_assignments_available_before_due
    CHECK (available_from IS NULL OR available_from < due_at);
