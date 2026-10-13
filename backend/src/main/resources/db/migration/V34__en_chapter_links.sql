-- EN questions point at the EN chapter page a student should open when they lose points on them.
-- 1) By default the link follows the question's place on the paper: SUBIECTUL I, question 1 → A1, and so on
--    (en_chapter_slots, from docs/EN-capitole.md §1; a requirement of a Subiectul III problem may have its own slot,
--    "III.5.b"). Computed when read, so it follows the questions as they are reordered.
-- 2) The teacher may override it on one question, or on one requirement a)/b) (en_chapter_id below).
-- Deleting a chapter only drops the links to it. Nullable columns: no table rewrite.
ALTER TABLE quiz_items ADD COLUMN en_chapter_id BIGINT REFERENCES en_chapters (id) ON DELETE SET NULL;
ALTER TABLE quiz_item_parts ADD COLUMN en_chapter_id BIGINT REFERENCES en_chapters (id) ON DELETE SET NULL;

CREATE INDEX idx_quiz_items_en_chapter ON quiz_items (en_chapter_id);
CREATE INDEX idx_quiz_item_parts_en_chapter ON quiz_item_parts (en_chapter_id);

-- "I.1" = SUBIECTUL I, question 1; "III.5.b" = SUBIECTUL al III-lea, problem 5, requirement b).
CREATE TABLE en_chapter_slots (
    slot       VARCHAR(12) PRIMARY KEY,
    chapter_id BIGINT      NOT NULL REFERENCES en_chapters (id) ON DELETE CASCADE
);

CREATE INDEX idx_en_chapter_slots_chapter ON en_chapter_slots (chapter_id);

INSERT INTO en_chapter_slots (slot, chapter_id)
SELECT s.slot, c.id
FROM (VALUES
    ('I.1', 'A1'), ('I.2', 'A2'), ('I.3', 'A4'), ('I.4', 'A3'), ('I.5', 'A6'), ('I.6', 'A8'),
    ('II.1', 'G1'), ('II.2', 'G2'), ('II.3', 'G3'), ('II.4', 'G5'), ('II.5', 'G6'), ('II.6', 'G8'),
    ('III.1', 'A9'), ('III.2', 'A10'), ('III.3', 'A11'), ('III.4', 'G5'),
    ('III.5', 'G7'), ('III.5.a', 'G5'), ('III.5.b', 'G7'),
    ('III.6', 'G9'), ('III.6.a', 'G8'), ('III.6.b', 'G9')
) AS s (slot, code)
JOIN en_chapters c ON c.code = s.code;
