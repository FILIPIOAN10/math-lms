-- A written-out book page the teacher must look at before any student does: the transcription could not read part of it
-- ([?]) or warned about it. While one page of a chapter needs review (or a photo is not written out yet) the chapter
-- cannot be shown, and a page flagged after the chapter was shown is left out of what students get.
-- Filled by tutor/scripts/publish_lessons.py; cleared by the teacher's "Am verificat pagina".
ALTER TABLE en_photo_lessons
    ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN review_note  TEXT;

-- The pages already written: unread text goes to review (the warnings were never kept in the published text).
UPDATE en_photo_lessons SET needs_review = TRUE, review_note = 'text necitit: [?]' WHERE markdown LIKE '%[?]%';
