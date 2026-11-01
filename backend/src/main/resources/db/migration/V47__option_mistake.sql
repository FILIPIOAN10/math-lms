-- What a wrong option gives away: the mistake behind it, in the teacher's words. The student who chose it reads this
-- instead of the general solution, and the teacher sees it next to how many chose that option. NULL = not written.
ALTER TABLE quiz_options ADD COLUMN mistake VARCHAR(500);
