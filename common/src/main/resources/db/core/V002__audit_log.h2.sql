-- Audit log (Phase 0 M0.8), H2 variant for local development: same columns as the MariaDB file, no partitions.
CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at BIGINT NOT NULL,
  season_id INT NOT NULL DEFAULT 0,
  server_id VARCHAR(32) NOT NULL,
  category VARCHAR(16) NOT NULL,
  action VARCHAR(48) NOT NULL,
  actor_uuid BINARY(16) NULL,
  actor_name VARCHAR(32) NOT NULL,
  target VARCHAR(64) NULL,
  target_name VARCHAR(64) NULL,
  amount BIGINT NULL,
  world VARCHAR(64) NULL,
  x INT NULL,
  y INT NULL,
  z INT NULL,
  details TEXT NULL,
  PRIMARY KEY (id, created_at)
);

CREATE INDEX IF NOT EXISTS idx_audit_actor_time ON audit_log (actor_uuid, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_target_time ON audit_log (target, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_cat_action_time ON audit_log (category, action, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log (created_at);
