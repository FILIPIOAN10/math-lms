-- S3 "Ai o notă nouă": when the student first opened the result of a graded test. NULL on a GRADED test = a new grade
-- (or a new teacher comment) the student has not seen yet - shown as "Nou" on their home page and quiz list. Grades
-- given before this migration count as seen: they are old news, not something to light up on the next login.
ALTER TABLE quiz_attempts ADD COLUMN result_seen_at TIMESTAMPTZ;

UPDATE quiz_attempts SET result_seen_at = COALESCE(submitted_at, started_at) WHERE status = 'GRADED';
