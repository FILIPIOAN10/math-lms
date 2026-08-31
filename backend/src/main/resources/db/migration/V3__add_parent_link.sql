-- Step 1.6l: link a student to a parent account. A student has at most one
-- parent; a parent may have many children. The admin sets this at approval time.
ALTER TABLE users
    ADD COLUMN parent_id BIGINT REFERENCES users (id);

CREATE INDEX idx_users_parent_id ON users (parent_id);
