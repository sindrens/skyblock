-- Phase P (P6): network-ready punishments (spec 13.12). Raid lockdown (13.9) is in memory only (cleared on restart) and
-- anticheat suggestions (13.3) reuse punishment_suggestions, so neither needs a table.
-- Never edit V001-V006. Re-runnable: every ALTER uses IF NOT EXISTS.

-- server_id: the server the punishment was given on (extras.network.server-id). scope: 'network' (counts on every server
-- sharing this database) or 'server' (only on server_id). NULL in both = given before P6; such rows count as 'network'.
-- Only read when extras.network.enabled is true; with one server nothing changes.
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS server_id VARCHAR(32) NULL;

ALTER TABLE punishments ADD COLUMN IF NOT EXISTS scope VARCHAR(8) NULL;
