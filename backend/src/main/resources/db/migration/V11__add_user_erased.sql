-- GDPR Art. 17: erasure turns a user row into an anonymised tombstone rather than deleting it,
-- so retained (anonymised) quiz attempts keep a valid foreign key. `erased` closes every auth path.
ALTER TABLE users
    ADD COLUMN erased     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN erased_at  TIMESTAMPTZ;
