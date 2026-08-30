-- Phase 1.6: extend `users` for email/password auth + onboarding.
-- Columns are nullable or carry a DEFAULT so the current 3-arg User entity
-- keeps working; the entity refactor that maps these fields is Step 1.6c.

ALTER TABLE users
    -- BCrypt hash; null for Google-only accounts (they authenticate via Google).
    ADD COLUMN password       VARCHAR(255),
    -- Google subject id; set when the account is linked to Google. One per user.
    ADD COLUMN google_id      VARCHAR(255) UNIQUE,
    -- true once the email is confirmed (immediately true for Google logins).
    ADD COLUMN email_verified BOOLEAN     NOT NULL DEFAULT FALSE,
    -- PENDING_VERIFICATION -> PENDING_APPROVAL -> ACTIVE / REJECTED.
    ADD COLUMN status         VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    -- Role asked for via the invite link; the real role lives in `role` and is
    -- filled only when an admin approves the account.
    ADD COLUMN requested_role VARCHAR(20);

-- Role is no longer required at creation time (assigned on admin approval).
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;

-- Existing accounts (the admin/teacher seeded before 1.6) are already trusted:
-- mark them verified and active so the onboarding gates don't lock them out.
UPDATE users
SET status         = 'ACTIVE',
    email_verified = TRUE;
