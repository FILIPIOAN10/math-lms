-- „De ce am greșit?": a student asks, on their result, why the option they chose is wrong. The teacher sees how many
-- asked next to that option (Statistici), writes the explanation - by hand or drafted from the EN lessons by the tutor -
-- and saves it as the option's `mistake` (V47); from then on every student who chose it reads it. Saving a non-blank
-- explanation deletes the option's requests: a request only means "waiting for the teacher".
CREATE TABLE mistake_explanation_requests (
    option_id    BIGINT      NOT NULL REFERENCES quiz_options (id) ON DELETE CASCADE,
    student_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (option_id, student_id)
);

CREATE INDEX idx_mistake_requests_student ON mistake_explanation_requests (student_id);

-- The lesson the explanation comes from (a chapter, section or point of the EN pages) and the page printed in the book,
-- so the student gets "Deschide lecția A3, pagina 21" under it. Both optional; a deleted page drops the link only.
ALTER TABLE quiz_options
    ADD COLUMN mistake_lesson_id   BIGINT REFERENCES en_chapters (id) ON DELETE SET NULL,
    ADD COLUMN mistake_lesson_page INT CHECK (mistake_lesson_page IS NULL OR mistake_lesson_page BETWEEN 1 AND 5000);

CREATE INDEX idx_quiz_options_mistake_lesson ON quiz_options (mistake_lesson_id);
