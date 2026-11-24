-- Tutor AI: which lesson text was written by tutor.build_chapter. The MD5 of the text it last published on a page; while
-- md5(notes) still equals it, nobody has edited the text since, so a later build may replace it (a fuller rebuild after a
-- partial one). Once the teacher changes a letter the hashes differ and the text is the teacher's: kept unless --overwrite.
-- NULL = not written by the tool (the teacher's own text, the V35 seed, or AI text published before this column).
-- Written by the tutor service only; the backend does not map it.

ALTER TABLE en_chapters ADD COLUMN notes_generated_md5 TEXT;
