-- Island chat role colors (M2.5, owner I11: the leader picks a color per role from islands.yml chat.palette).
-- Only the roles the leader changed are stored; the others use chat.role-colors.*. member_role: leader | admin | mod |
-- member. chat_color: a palette entry (named color or #RRGGBB). Rows go with the island (IslandStore.remove).
CREATE TABLE IF NOT EXISTS island_chat_colors (
  island_id BINARY(16) NOT NULL,
  member_role VARCHAR(16) NOT NULL,
  chat_color VARCHAR(16) NOT NULL,
  updated_at BIGINT NOT NULL,
  updated_by BINARY(16) NOT NULL,
  PRIMARY KEY (island_id, member_role)
);
