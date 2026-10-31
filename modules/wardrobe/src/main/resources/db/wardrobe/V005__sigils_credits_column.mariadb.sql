-- Card C1 (C1-R1-03): wardrobe_sigils.gems (the price paid for an account Sigil) is renamed credits. MariaDB 10.5+ syntax;
-- IF EXISTS makes a second run a no-op (it only renames while the old column is still there). Values are unchanged.
ALTER TABLE wardrobe_sigils CHANGE COLUMN IF EXISTS gems credits BIGINT NOT NULL;
