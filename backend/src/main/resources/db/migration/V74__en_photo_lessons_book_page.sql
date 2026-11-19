-- The page of the book each written-out page is (V73), so a link to a lesson page ("De ce am greșit?", the tutor's
-- sources, "Dă-mi una asemănătoare" all name one) opens the written-out page itself. Filled here from the transcript
-- header ("book_page: 144") of the pages already written, and by tutor/scripts/publish_lessons.py from now on.
ALTER TABLE en_photo_lessons ADD COLUMN book_page INT;

UPDATE en_photo_lessons l
SET book_page = substring(t.content from 'book_page:[ \t]*([0-9]+)')::INT
FROM tutor_photo_transcripts t
WHERE t.photo_id = l.photo_id;
