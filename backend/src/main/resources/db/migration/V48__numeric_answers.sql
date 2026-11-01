-- A question answered with a number, marked by the server. The teacher's answer and the student's stay as written
-- ("1/2", "0,5"): they are compared by value when marked, and shown as they were typed.
ALTER TABLE quiz_items ADD COLUMN numeric_answer VARCHAR(40);
ALTER TABLE item_responses ADD COLUMN numeric_answer VARCHAR(40);
