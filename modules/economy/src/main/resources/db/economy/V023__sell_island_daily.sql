-- Dynamic prices v2 (docs/plans/DYNAMIC_PRICES_V2.md): how many of each item an island ('i:<island id>') or a profile
-- ('p:<profile key>') sold on one sales day (epoch day, the day starts at sell.daily.reset-hour Oslo). Kept in memory by
-- the plugin, saved every few seconds and on shutdown. One row per scope and item; the daily reset is applied when read.
CREATE TABLE IF NOT EXISTS sell_island_daily (
  scope VARCHAR(40) NOT NULL,
  item_key VARCHAR(80) NOT NULL,
  sales_day BIGINT NOT NULL,
  sold BIGINT NOT NULL,
  PRIMARY KEY (scope, item_key)
);
