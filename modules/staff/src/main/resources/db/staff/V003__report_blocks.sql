-- Phase P (P9, spec 14.5): report blocks. The ladder step report-block <time|perm> of moderation stops a player from
-- using /report until until_ms (NULL = until lifted). reference = who set it (punishment:<id>), used to lift exactly
-- that block on undo/pardon/rollback. Rows are never deleted (lifted_at marks a lifted one). Account-wide (player uuid).
CREATE TABLE IF NOT EXISTS report_blocks (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  player_uuid BINARY(16) NOT NULL,
  until_ms BIGINT NULL,
  reference VARCHAR(48) NOT NULL,
  created BIGINT NOT NULL,
  lifted_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_blocks_player ON report_blocks (player_uuid, lifted_at);

CREATE INDEX IF NOT EXISTS idx_report_blocks_reference ON report_blocks (reference);
