-- The barem of ONE open question read from a photo during the review of "Test din poze", before the test exists:
-- such a reading has no quiz; it keeps the question as the review sent it (statement, points, requirements) so the
-- worker can tell the AI what to look for. A reading is for a test or for one question, never neither.
ALTER TABLE ai_barem_reads ALTER COLUMN quiz_id DROP NOT NULL;
ALTER TABLE ai_barem_reads ADD COLUMN question_json TEXT;
ALTER TABLE ai_barem_reads ADD CONSTRAINT ck_ai_barem_reads_target CHECK (quiz_id IS NOT NULL OR question_json IS NOT NULL);
