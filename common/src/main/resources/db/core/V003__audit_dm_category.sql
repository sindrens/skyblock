-- R3-9b / owner answer 318: private messages get their own audit category (dm), so /logs can show chat to helpers
-- without DMs. Rows written before the split move from chat/dm to dm/dm (uses idx_audit_cat_action_time).
UPDATE audit_log SET category = 'dm' WHERE category = 'chat' AND action = 'dm';
