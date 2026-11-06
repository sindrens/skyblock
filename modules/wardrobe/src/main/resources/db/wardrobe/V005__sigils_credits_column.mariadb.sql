-- Card C1 (C1-R1-03): wardrobe_sigils.gems (the price paid for an account Sigil) is renamed credits. MariaDB (the VPS
-- runs 11.8): "CHANGE [COLUMN] [IF EXISTS] old new definition" with the same definition as V001 (BIGINT NOT NULL), so
-- values are unchanged; when the old column is gone (a second run) IF EXISTS turns it into a note, not an error.
ALTER TABLE wardrobe_sigils CHANGE COLUMN IF EXISTS gems credits BIGINT NOT NULL;
