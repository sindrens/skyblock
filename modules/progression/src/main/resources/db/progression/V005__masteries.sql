-- Island masteries and Ascension (progression module, PHASE-5 M5.5). One row per island, season and mastery with the
-- bought rank; Ascension is the row 'ascension' whose rank is the number of Ascension levels bought (endless).
-- Written as the guard of the island bank payment (ledger reason 'island_mastery'), so the rank and the money change in
-- one database transaction: a rank exists exactly when it was paid, and the payment's idempotency key stops a second
-- payment for the same rank.
CREATE TABLE IF NOT EXISTS island_masteries (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  mastery_id VARCHAR(32) NOT NULL,
  mastery_rank INT NOT NULL,
  bought_by BINARY(16) NULL,
  bought_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, mastery_id)
);
