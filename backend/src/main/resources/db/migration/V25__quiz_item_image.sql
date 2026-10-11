-- A picture under a question's statement (EN Subiectul II: the figure the student reads the answer from). The file
-- lives in the uploads volume (app.storage.question-images-dir, backed up with the rest); image_key is its stored name.
-- image_required marks a question imported with "[imagine]" whose figure still has to be added; it clears itself as
-- soon as a picture is attached. Nullable / defaulted columns: no table rewrite.
ALTER TABLE quiz_items ADD COLUMN image_key VARCHAR(200);
ALTER TABLE quiz_items ADD COLUMN image_required BOOLEAN NOT NULL DEFAULT FALSE;
