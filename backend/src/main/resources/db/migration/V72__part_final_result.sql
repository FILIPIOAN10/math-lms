-- The final result a requirement of an open problem should reach ("Calculează aria" → 24), written by the teacher next
-- to its barem. NULL = the requirement asks for no typed result ("Arată că…", or a result that is not a plain number).
-- Compared by value, like the answer of a numeric question (V48): 0,5 = 1/2.
ALTER TABLE quiz_item_parts ADD COLUMN final_answer VARCHAR(40);

-- The result the student typed for a requirement, next to the photographed solution, as written. It never gives
-- points by itself: on the grading screen a right result only proposes the requirement's points to the teacher.
CREATE TABLE response_part_answers (
    response_id BIGINT      NOT NULL REFERENCES item_responses (id) ON DELETE CASCADE,
    part_id     BIGINT      NOT NULL REFERENCES quiz_item_parts (id) ON DELETE CASCADE,
    answer      VARCHAR(40) NOT NULL,
    PRIMARY KEY (response_id, part_id)
);

-- Deleting a requirement looks its answers up by part_id (the primary key starts with response_id).
CREATE INDEX idx_response_part_answers_part ON response_part_answers (part_id);
