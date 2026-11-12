-- Indexes for the foreign keys that had none (audit 2026-11-05 L1, re-checked 2026-11-12). Postgres does not index a
-- foreign key by itself: without one, deleting the parent row (a quiz, a class, an option, a requirement) scans the
-- whole child table, and listing a parent's children does too. SchemaIndexesTest fails if a new one is missed.
-- The tables are small today, so a plain CREATE INDEX inside Flyway's transaction is fine.
CREATE INDEX IF NOT EXISTS idx_ai_barem_reads_quiz ON ai_barem_reads (quiz_id);
CREATE INDEX IF NOT EXISTS idx_ai_grade_proposals_item ON ai_grade_proposals (item_id);
CREATE INDEX IF NOT EXISTS idx_ai_imports_chapter ON ai_imports (chapter_id);
CREATE INDEX IF NOT EXISTS idx_ai_imports_quiz ON ai_imports (quiz_id);
CREATE INDEX IF NOT EXISTS idx_ai_imports_reused_from ON ai_imports (reused_from);
CREATE INDEX IF NOT EXISTS idx_ai_imports_school_class ON ai_imports (school_class_id);
CREATE INDEX IF NOT EXISTS idx_response_part_points_part ON response_part_points (part_id);
CREATE INDEX IF NOT EXISTS idx_review_answers_option ON review_answers (option_id);
