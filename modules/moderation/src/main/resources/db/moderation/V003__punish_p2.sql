-- Phase P (P2): undo, double-punish protection, offence expiry, /pstats. Never edit V001/V002.

-- The warn that fired a warn-rule suggestion (spec 12.1: undoing that warn removes its pending suggestion).
-- NULL for suggestions created before P2 or by a mute (repeat rule).
ALTER TABLE punishment_suggestions ADD COLUMN cause_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_suggestions_cause ON punishment_suggestions (cause_id);

-- /pstats (spec 12.10): punishments given by one staff member in a period, without scanning the whole table.
CREATE INDEX IF NOT EXISTS idx_punishments_issuer ON punishments (issuer_uuid, created);
