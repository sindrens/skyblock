-- Loadouts W3 (docs/plans/WARDROBE.md §7): saved presets per PROFILE (profile key) that swap wardrobe pieces, the pet
-- and hotbar unique items in one action. Applying a preset moves real items only through wardrobe_storage (escrow, §4.4):
-- items leaving the hotbar become 'stored' and items coming from the chest 'releasing' (plan_id = the swap) in the SAME
-- transaction that writes wardrobe_equipped, so a crash leaves every item in exactly one recoverable place.

-- slot_index: 0-based place in the /loadout menu. name_key: lowercase name, unique per profile (for /loadout <name>).
-- armor: JSON {"head":"<entry uuid>"|"empty", ...} (missing slot = keep). hotbar: JSON {"0":{"id":"<item uuid>","name":"..","material":".."}, ...}
-- (missing slot = keep). pet_id NULL = keep the active pet.
CREATE TABLE IF NOT EXISTS loadouts (
  loadout_id BINARY(16) NOT NULL PRIMARY KEY,
  profile_key BINARY(16) NOT NULL,
  slot_index INT NOT NULL,
  name VARCHAR(16) NOT NULL,
  name_key VARCHAR(16) NOT NULL,
  icon VARCHAR(48) NOT NULL,
  armor VARCHAR(1024) NOT NULL,
  pet_id VARCHAR(64) NULL,
  hotbar VARCHAR(4096) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  last_used_at BIGINT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uq_loadouts_slot UNIQUE (profile_key, slot_index),
  CONSTRAINT uq_loadouts_name UNIQUE (profile_key, name_key)
);

-- Extra loadout slots bought with store credits (LD2): account-wide (player UUID), kept across seasons, used on every
-- profile. The future store module adds to slots_bought; until then nothing writes here (free + rank slots only).
CREATE TABLE IF NOT EXISTS loadout_slots_bought (
  player_uuid BINARY(16) NOT NULL PRIMARY KEY,
  slots_bought INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL
);
