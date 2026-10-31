-- Owner F1 (REVIEW3): a profile can send money away only after it earned send-gate.required itself. The earned amount is
-- summed from the ledger (income reasons only) until it reaches the requirement once; then the profile is remembered here,
-- so the sum is never needed again (earning can only go up).
CREATE TABLE IF NOT EXISTS eco_send_unlocked (
  profile_id BINARY(16) NOT NULL,
  earned BIGINT NOT NULL,
  unlocked_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id)
);
