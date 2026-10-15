-- Tutor AI: where a chunk came from, so an answer can say "page 10 of the textbook" and a re-ingestion of the same
-- pages replaces exactly its own chunks. Written by the tutor service only.
-- Idempotent on purpose: the same statements may already have been applied by hand on a dev database.

ALTER TABLE tutor_chunks
    ADD COLUMN IF NOT EXISTS source      VARCHAR(10)  NOT NULL DEFAULT 'DB' CHECK (source IN ('DB', 'PDF', 'PHOTO')),
    ADD COLUMN IF NOT EXISTS source_name VARCHAR(200),
    -- The page number printed in the book (not the position inside the PDF).
    ADD COLUMN IF NOT EXISTS book_page   INT;

CREATE INDEX IF NOT EXISTS idx_tutor_chunks_source ON tutor_chunks (source, source_name, book_page);
