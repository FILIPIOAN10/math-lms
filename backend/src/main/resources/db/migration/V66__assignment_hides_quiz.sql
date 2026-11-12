-- Which scheduled homework also hides its TEST until the release (audit 2026-11-12 M1/M2). Only one whose test was
-- published for it ("Publică și dă ca temă" on a draft): that test is then hidden from every student until the release,
-- not only from the class it was given to. A homework given for a test already published hides just itself - the test
-- stays where the students already see it (and an attempt they are writing stays open).
-- Existing rows: FALSE. Nothing records whether their test was published for them, and keeping a test visible is the
-- safe side (V64 never reached production).
ALTER TABLE assignments ADD COLUMN hides_quiz BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE assignments ADD CONSTRAINT ck_assignments_hides_quiz_scheduled
    CHECK (NOT hides_quiz OR available_from IS NOT NULL);
