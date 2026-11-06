-- ECON-SELL-SHOP-AH A3: the daily Hot Item announcement. sell_hot_announced has one row per Oslo day (epoch day): the
-- insert that wins broadcasts, so a restart or a second task never announces twice. sell_hot_seen remembers the last day
-- each player got the one-line join notice. Rows older than a few weeks are purged by the plugin. Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS sell_hot_announced (
  day INT NOT NULL,
  item_key VARCHAR(80) NOT NULL,
  announced_at BIGINT NOT NULL,
  PRIMARY KEY (day)
);
CREATE TABLE IF NOT EXISTS sell_hot_seen (
  player_uuid BINARY(16) NOT NULL,
  day INT NOT NULL,
  PRIMARY KEY (player_uuid)
);
