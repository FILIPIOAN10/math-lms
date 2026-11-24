-- Tutor AI (plan step 6): passages of a chapter's NEW lesson (en_chapter_lessons, V78), embedded by tutor.lesson_index
-- once the teacher has approved the lesson AND shown the chapter. The tutor's search prefers them to the raw book
-- pages; a LESSON passage has no book page (book_page 0). Written by the tutor service only.

ALTER TABLE tutor_chunks DROP CONSTRAINT IF EXISTS tutor_chunks_source_check;
ALTER TABLE tutor_chunks
    ADD CONSTRAINT tutor_chunks_source_check CHECK (source IN ('DB', 'PDF', 'PHOTO', 'MANUAL', 'LESSON'));
