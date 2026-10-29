package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.skills.SkillType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL of the {@code skills} table ({@code db/skills/V001__skills.sql}). Pass a connection from {@code Database}. */
final class SkillRepository {

    private static final String SELECT = "SELECT skill, xp FROM skills WHERE player = ? AND season_id = ?";
    private static final String UPDATE = "UPDATE skills SET xp = ?, level = ?, updated_at = ? WHERE player = ? AND season_id = ? AND skill = ?";
    private static final String INSERT = "INSERT INTO skills (player, season_id, skill, xp, level, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

    /** Stored totals of a player in a season (skills without a row are missing = 0). */
    Map<SkillType, Double> load(Connection c, UUID player, int season) throws SQLException {
        Map<SkillType, Double> out = new EnumMap<>(SkillType.class);
        for (Object[] row : Sql.query(c, SELECT, rs -> new Object[]{rs.getString("skill"), rs.getDouble("xp")}, player, season)) {
            SkillType.byId((String) row[0]).ifPresent(skill -> out.put(skill, (Double) row[1]));
        }
        return out;
    }

    /** Writes absolute totals (update, insert when missing). Call inside a transaction for many rows. */
    void save(Connection c, int season, List<PlayerSkills.Row> rows, long now) throws SQLException {
        for (PlayerSkills.Row row : rows) {
            upsert(c, row.player(), season, row.skill(), row.xp(), row.level(), now);
        }
    }

    /**
     * Adds XP to the stored totals (used when a player's data never loaded during the session). Run inside a
     * transaction; the row is locked while reading so two writers can't lose XP.
     */
    void addDeltas(Connection c, UUID player, int season, Map<SkillType, Double> deltas, SkillCurve curve, long now) throws SQLException {
        Map<SkillType, Double> stored = new EnumMap<>(SkillType.class);
        for (Object[] row : Sql.query(c, SELECT + " FOR UPDATE", rs -> new Object[]{rs.getString("skill"), rs.getDouble("xp")}, player, season)) {
            SkillType.byId((String) row[0]).ifPresent(skill -> stored.put(skill, (Double) row[1]));
        }
        for (Map.Entry<SkillType, Double> e : deltas.entrySet()) {
            if (!(e.getValue() > 0)) {
                continue;
            }
            double total = stored.getOrDefault(e.getKey(), 0.0) + e.getValue();
            upsert(c, player, season, e.getKey(), total, curve.levelFor(total), now);
        }
    }

    private static void upsert(Connection c, UUID player, int season, SkillType skill, double xp, int level, long now) throws SQLException {
        if (Sql.update(c, UPDATE, xp, level, now, player, season, skill.id()) == 0) {
            Sql.update(c, INSERT, player, season, skill.id(), xp, level, now);
        }
    }
}
