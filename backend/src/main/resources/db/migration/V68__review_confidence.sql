-- How sure the student said they were when answering a daily-review question: GUESS ("Am ghicit"), THINK ("Cred că
-- da") or SURE ("Știu sigur"). NULL = not asked (every answer before this migration) or not tapped - it is optional.
-- Never part of a grade: it only keeps a guessed right answer from moving up a Leitner box, and feeds the student's
-- own calibration line. Adding a nullable column with no default does not rewrite the table.
ALTER TABLE review_answers ADD COLUMN confidence VARCHAR(10);

ALTER TABLE review_answers ADD CONSTRAINT chk_review_answers_confidence
    CHECK (confidence IS NULL OR confidence IN ('GUESS', 'THINK', 'SURE'));
