-- Card C1 (C1-R1-03): wardrobe_sigils.gems (the price paid for an account Sigil) is renamed credits. H2 2.x grammar
-- "ALTER TABLE t ALTER COLUMN [IF EXISTS] c RENAME TO n" (checked against the repo's H2 2.5.252 in MODE=MySQL: values and
-- NOT NULL are kept, a second run is a no-op). The MariaDB variant uses CHANGE COLUMN.
ALTER TABLE wardrobe_sigils ALTER COLUMN IF EXISTS gems RENAME TO credits;
