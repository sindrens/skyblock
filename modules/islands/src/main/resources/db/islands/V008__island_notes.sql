-- Island notes (/is notes, owner N1-N9): one shared to-do list per island.
-- author / done_by / deleted_by are PROFILE keys (island profiles, PROF); names are looked up for display.
-- A note is open (done_at NULL) or done (✔ by done_by at done_at). Pinned notes have a hologram at pin_* inside the
-- island (N9); ticking a note done removes its pin. Deleting is soft (deleted_at set): staff can read deleted notes for
-- notes.deleted-keep-days (owner N7: 7) days, then they are removed for good. Disbanding an island soft-deletes its notes
-- (deleted_reason 'disband'). The 50-note and hologram limits are checked in one transaction that locks the island row.
CREATE TABLE IF NOT EXISTS island_notes (
  id BINARY(16) NOT NULL,
  island_id BINARY(16) NOT NULL,
  author BINARY(16) NOT NULL,
  note_text VARCHAR(256) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  updated_by BINARY(16) NULL,
  done_by BINARY(16) NULL,
  done_at BIGINT NULL,
  pin_world VARCHAR(64) NULL,
  pin_x DOUBLE NULL,
  pin_y DOUBLE NULL,
  pin_z DOUBLE NULL,
  deleted_at BIGINT NULL,
  deleted_by BINARY(16) NULL,
  deleted_reason VARCHAR(16) NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_island_notes_island ON island_notes (island_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_island_notes_deleted ON island_notes (deleted_at);
