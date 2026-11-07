-- Extra time on timed tests for one student (special educational needs, CES): 0 = none, else a share of the test's
-- own limit added on start. Only these steps, so a typo cannot hand someone ten hours. A constant default makes the
-- ADD COLUMN a catalogue change on Postgres 11+, no table rewrite.
ALTER TABLE users ADD COLUMN extra_time_percent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT users_extra_time_percent_check CHECK (extra_time_percent IN (0, 25, 50, 100));
