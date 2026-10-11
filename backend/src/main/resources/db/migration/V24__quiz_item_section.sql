-- EN-style tests: questions grouped under a heading ("SUBIECTUL I" + the instruction line under it). Each question keeps
-- its own points, grading and statistics; the section is only how the test is laid out and numbered (1, 2, … again
-- under each heading). NULL = no heading. Nullable column: no table rewrite.
ALTER TABLE quiz_items ADD COLUMN section VARCHAR(300);
