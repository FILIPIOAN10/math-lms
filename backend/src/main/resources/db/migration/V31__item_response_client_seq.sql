-- Feature 2 (answers that survive a bad connection): the browser keeps unsent answers in a queue and retries them, so
-- an old retry could arrive after a newer answer. Each answer carries the browser's id and a counter; an answer from the
-- same browser with a lower counter than the stored one is ignored. Another browser (another device) always wins: the
-- last to arrive. Both nullable: answers from older clients (and the E2E seeder) have neither and always apply.
ALTER TABLE item_responses ADD COLUMN client_id UUID;
ALTER TABLE item_responses ADD COLUMN client_seq BIGINT;
