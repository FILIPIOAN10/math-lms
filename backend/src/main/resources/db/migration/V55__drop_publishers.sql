-- The "Edituri" feature (V53 publishers, V54 their books) is withdrawn (2026-11-07, the user's decision): its tables
-- and the two columns on quizzes go. V53/V54 stay in place because databases already applied them (Flyway history);
-- this undoes them. Tests are kept as they are; only their publisher/book tag disappears. Logo files, if any were
-- uploaded, stay in uploads/publisher-logos (orphans, harmless).
ALTER TABLE quizzes DROP COLUMN IF EXISTS book_id;
ALTER TABLE quizzes DROP COLUMN IF EXISTS publisher_id;
DROP TABLE IF EXISTS publisher_books;
DROP TABLE IF EXISTS publishers;
