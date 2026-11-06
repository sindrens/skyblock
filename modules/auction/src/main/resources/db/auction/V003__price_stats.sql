-- ECON-SELL-SHOP-AH C5: typical price per item (median price per item of recent sales), rebuilt by an async job.
-- A row exists only when the item had enough sales, sellers and buyers (auction.yml hints.*).
CREATE TABLE IF NOT EXISTS ah_price_stats (
  item_key VARCHAR(80) NOT NULL,
  median_price BIGINT NOT NULL,
  sales INT NOT NULL,
  sellers INT NOT NULL,
  buyers INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (item_key)
);
