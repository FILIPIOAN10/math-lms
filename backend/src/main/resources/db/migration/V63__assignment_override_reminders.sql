-- A student working to a deadline of their own (V57) is reminded before THAT date: the class reminder skips them, so
-- until now nothing reminded them at all (audit M2, 2026-11-10). One reminder per date: moving their date clears it,
-- the way rescheduling the homework clears the class one. Excused students are never reminded.
ALTER TABLE assignment_overrides ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- The reminder job's lookup: own dates not reminded yet, excused students left out.
CREATE INDEX idx_assignment_overrides_reminder_due ON assignment_overrides (due_at)
    WHERE reminder_sent_at IS NULL AND NOT excused AND due_at IS NOT NULL;
