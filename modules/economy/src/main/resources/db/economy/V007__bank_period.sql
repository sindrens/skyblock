-- BANK2 (owner #169): interest every 8 h instead of 3 h. Period numbers (cycle_no, settled_cycle) count periods of a fixed
-- length from the epoch, so every state row remembers the length it was written in. Rows from M3.3 are 3 h (180 minutes);
-- the bank converts them to the configured length at start (InterestMath.convertPeriod) before it opens.
ALTER TABLE eco_bank_state ADD COLUMN IF NOT EXISTS period_minutes INT NOT NULL DEFAULT 180;
