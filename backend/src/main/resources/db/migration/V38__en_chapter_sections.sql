-- EN chapter pages grow sections: a chapter holds its sections (and a section its points), so a student opens ONE chapter
-- (all its pages, in order) and jumps to a section by the page it starts on. A section has no photos of its own.
-- book_page = the page number printed in the book where the section starts; with the first page of the chapter it
-- tells which of the chapter's photos to open.
-- parent_id has no ON DELETE action on purpose: the service refuses to delete a chapter that still has sections.
-- Idempotent: the same statements may already have been applied by hand on a dev database.
ALTER TABLE en_chapters
    ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES en_chapters (id),
    ADD COLUMN IF NOT EXISTS book_page INT CHECK (book_page IS NULL OR book_page BETWEEN 1 AND 5000);

CREATE INDEX IF NOT EXISTS idx_en_chapters_parent ON en_chapters (parent_id);
