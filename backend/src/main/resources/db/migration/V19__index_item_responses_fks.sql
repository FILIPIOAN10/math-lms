-- item_responses had no index of its own on its two foreign keys (the unique (attempt_id, item_id) leads with attempt_id),
-- so per-item statistics and deleting an item or an option scanned the whole table. Small table today, so a plain
-- CREATE INDEX (it locks writes for a moment) is fine; on a large table use CONCURRENTLY in a non-transactional migration.
CREATE INDEX IF NOT EXISTS idx_item_responses_item_id ON item_responses (item_id);
CREATE INDEX IF NOT EXISTS idx_item_responses_selected_option_id ON item_responses (selected_option_id);
