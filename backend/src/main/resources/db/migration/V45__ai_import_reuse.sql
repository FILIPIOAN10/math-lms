-- The same pages sent again (re-importing a test, the same PDF twice) reuse what the AI already read, at no cost:
-- pages_hash is a SHA-256 of the pages as stored, in order; reused_from points at the import whose reading was copied
-- (the teacher may still ask for a fresh reading). A reused import does not count toward the daily page limit.
ALTER TABLE ai_imports
    ADD COLUMN pages_hash  VARCHAR(64),
    ADD COLUMN reused_from BIGINT REFERENCES ai_imports (id) ON DELETE SET NULL;

CREATE INDEX idx_ai_imports_reuse ON ai_imports (teacher_id, pages_hash, id DESC) WHERE status = 'DONE';
