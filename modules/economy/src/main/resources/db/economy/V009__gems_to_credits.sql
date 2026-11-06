-- Card C1 (C1-R1-03, C1-R2-02): gems are renamed credits and split into two ledgers: 'credits' (bought, tradeable) and
-- 'credits_earned' (votes, thanks, streak, pass; never tradeable). Nothing was ever sold for gems, so every old gem row
-- becomes 'credits_earned'. Amounts, balances and ledger ids stay as they are; only the currency key changes, so balances
-- still equal their ledger deltas (C1-R2-19) and no ledger row is needed for the rename itself.
-- Safe to re-run: every statement only touches rows that still say 'gems', and no 'credits_earned' row can exist before
-- this file (the currency did not exist; migrations run before the module writes anything). If one did exist, the
-- eco_accounts UPDATE would fail on the primary key and the module would stop: it never merges or loses a balance silently.
-- Deploy together with the jar that knows 'credits_earned' (docs/MIGRATION_CREDITS.md). Valid on MariaDB 11.8 and H2.
UPDATE eco_accounts SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE eco_ledger SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE money_notes SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE eco_watch_flows SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE eco_watch_accounts SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE eco_watch_supply SET currency = 'credits_earned' WHERE currency = 'gems';
UPDATE eco_watch_alerts SET currency = 'credits_earned' WHERE currency = 'gems';
