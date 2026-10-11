-- E6 mastery map: which chapter a question practises. A quiz carries a default chapter that its questions inherit; a
-- question may override it (mixed tests such as a mock exam). Both optional. Deleting a chapter only removes the tag -
-- the answers and grades stay. Nullable columns: no table rewrite.
ALTER TABLE quizzes ADD COLUMN chapter_id BIGINT REFERENCES chapters (id) ON DELETE SET NULL;
ALTER TABLE quiz_items ADD COLUMN chapter_id BIGINT REFERENCES chapters (id) ON DELETE SET NULL;

CREATE INDEX idx_quizzes_chapter_id ON quizzes (chapter_id);
CREATE INDEX idx_quiz_items_chapter_id ON quiz_items (chapter_id);
