-- Audit log (Phase 0 M0.8), MariaDB variant: partitioned by month on created_at (UTC millis) so years of data stay fast.
-- Partitions for upcoming months are added automatically by the plugin (AuditLog partition maintenance).
-- The primary key must contain the partition column, hence (id, created_at).
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
)
PARTITION BY RANGE (created_at) (
  PARTITION p_future VALUES LESS THAN MAXVALUE
);

CREATE INDEX IF NOT EXISTS idx_audit_actor_time ON audit_log (actor_uuid, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_target_time ON audit_log (target, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_cat_action_time ON audit_log (category, action, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log (created_at);
