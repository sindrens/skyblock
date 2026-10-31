-- Card C1 (C1-R1-03): wardrobe_sigils.gems (the price paid for an account Sigil) is renamed credits. H2 2.x syntax
-- (the MariaDB variant uses CHANGE COLUMN); IF EXISTS makes a second run a no-op. Values are unchanged.
ALTER TABLE wardrobe_sigils ALTER COLUMN IF EXISTS gems RENAME TO credits;
