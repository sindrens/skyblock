-- Island stats (M2.6): counters per island, member and category in day and week buckets plus all-time totals.
-- member_key: the member's profile key, or 16 zero bytes for island-wide counters (generator output, visitors).
-- stat_key: IslandStat key (blocks_mined, logs_chopped, crops_harvested, blocks_placed, generator_output, mobs_killed,
-- items_sold, money_earned, visitors, playtime (seconds), koth_time (seconds), bosses_killed).
-- bucket_day: epoch day in the stats time zone (island-stats.yml, Europe/Oslo). bucket_week: epoch day of that week's Monday.
-- Written in batches (one transaction per flush, amount = amount + delta). Day rows are kept retention.daily-days, week
-- rows retention.weekly-weeks, totals forever (rows of removed islands stay for season history, V2).
CREATE TABLE IF NOT EXISTS island_stats_daily (
  island_id BINARY(16) NOT NULL,
  member_key BINARY(16) NOT NULL,
  stat_key VARCHAR(32) NOT NULL,
  bucket_day INT NOT NULL,
  amount BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, member_key, stat_key, bucket_day)
);

CREATE INDEX IF NOT EXISTS idx_island_stats_daily_top ON island_stats_daily (stat_key, bucket_day);
CREATE INDEX IF NOT EXISTS idx_island_stats_daily_day ON island_stats_daily (bucket_day);

CREATE TABLE IF NOT EXISTS island_stats_weekly (
  island_id BINARY(16) NOT NULL,
  member_key BINARY(16) NOT NULL,
  stat_key VARCHAR(32) NOT NULL,
  bucket_week INT NOT NULL,
  amount BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, member_key, stat_key, bucket_week)
);

CREATE INDEX IF NOT EXISTS idx_island_stats_weekly_top ON island_stats_weekly (stat_key, bucket_week);
CREATE INDEX IF NOT EXISTS idx_island_stats_weekly_week ON island_stats_weekly (bucket_week);

CREATE TABLE IF NOT EXISTS island_stats_total (
  island_id BINARY(16) NOT NULL,
  member_key BINARY(16) NOT NULL,
  stat_key VARCHAR(32) NOT NULL,
  amount BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, member_key, stat_key)
);

CREATE INDEX IF NOT EXISTS idx_island_stats_total_top ON island_stats_total (stat_key);
