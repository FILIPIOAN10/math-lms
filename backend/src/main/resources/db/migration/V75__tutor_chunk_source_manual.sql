-- Tutor AI: passages from a whole school textbook (grades 5-8, tutor.manual), next to the class lessons. A MANUAL
-- passage sits under the EN chapter of its lesson, but its book_page is a page of that textbook, not of the chapter's
-- lesson, so the tutor never links it as "pagina N" of the lesson. Written by the tutor service only.

ALTER TABLE tutor_chunks DROP CONSTRAINT IF EXISTS tutor_chunks_source_check;
ALTER TABLE tutor_chunks
    ADD CONSTRAINT tutor_chunks_source_check CHECK (source IN ('DB', 'PDF', 'PHOTO', 'MANUAL'));
