-- Tokens are removed and replaced by island crystals (owner decision O8 / #39, docs/DECISIONS_2026-10-30.md).
-- Existing balances, history and notes keep their amounts; only the currency key changes.
-- Safe to re-run: no row has the key 'crystals' before this migration (the currency did not exist).
UPDATE eco_accounts SET currency = 'crystals' WHERE currency = 'tokens';
UPDATE eco_ledger SET currency = 'crystals' WHERE currency = 'tokens';
UPDATE money_notes SET currency = 'crystals' WHERE currency = 'tokens';
