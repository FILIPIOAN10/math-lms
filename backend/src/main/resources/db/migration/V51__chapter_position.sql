-- A chapter's place in its book, so the chapter lists and the mastery map follow the textbook instead of the alphabet
-- ("Calcul algebric" was listed before "Numere reale"). The teacher moves a chapter up or down on Clase.
-- Chapters already there keep the order they were added in: a teacher types a book in, chapter after chapter.
ALTER TABLE chapters ADD COLUMN position INT;

UPDATE chapters c
SET position = ordered.place
FROM (SELECT id, row_number() OVER (PARTITION BY book_id ORDER BY id) - 1 AS place FROM chapters) ordered
WHERE c.id = ordered.id;

ALTER TABLE chapters ALTER COLUMN position SET DEFAULT 0;
ALTER TABLE chapters ALTER COLUMN position SET NOT NULL;
