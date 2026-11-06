-- ECON-SELL-SHOP-AH B2: staff switch single shop items off and on again (/eco shop disable|enable) without editing
-- shop.yml. One row per item that was ever toggled; enabled = the current state. Loaded into memory at enable.
-- actor = staff UUID as text (or "console"). Times in epoch millis. Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS shop_overrides (
  item_id VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL,
  reason VARCHAR(128) NOT NULL,
  actor VARCHAR(36) NOT NULL,
  changed_at BIGINT NOT NULL,
  PRIMARY KEY (item_id)
);
