-- Results that say more than a number: the teacher's written comment on one graded open answer, and one comment on the
-- whole paper. Both optional, shown to the student and the parent with the result, exported with the student's data and
-- cleared by GDPR erasure (free text about a person may name them). Nullable columns: no table rewrite.
ALTER TABLE item_responses ADD COLUMN teacher_comment VARCHAR(2000);
ALTER TABLE quiz_attempts ADD COLUMN teacher_comment VARCHAR(2000);
