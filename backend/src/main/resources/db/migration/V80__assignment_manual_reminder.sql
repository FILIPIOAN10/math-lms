-- "Trimite reminder" (UX redesign, step 4): the teacher reminds the students who have not handed in yet, now, by email.
-- When the last such reminder was queued: a second one waits 12 hours, so a double click or an impatient afternoon does
-- not flood the students (and their parents) with the same email. Independent of reminder_sent_at, the automatic
-- "due soon" reminder (V18), which a manual reminder inside its window marks as sent instead.

ALTER TABLE assignments ADD COLUMN manual_reminder_at TIMESTAMPTZ;
