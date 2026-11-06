-- Card C1 (C1-R1-03): gems were renamed credits. pay_currency 'credits' means "paid with credits": earned first, then
-- bought (CreditSpend). A refund looks up what the payment's ledger rows took (idempotency key bm:<buy id>), so an old
-- gem payment (now 'credits_earned' in the ledger) comes back as earned credits. ('credits_earned' does not fit the
-- VARCHAR(12) column, and the column names the price currency, not a ledger half.)
-- Safe to re-run (only rows that still say 'gems'). Valid on MariaDB 11.8 and H2.
UPDATE black_market_buys SET pay_currency = 'credits' WHERE pay_currency = 'gems';
