package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQL of the tools module ({@code db/tools/V001__tools.sql}). Pass a connection from {@code Database}. */
final class ToolRepository {

    private static final String COLUMNS = "tool_id, kind, tier, level, xp, prestige, prestige_season, perks, skin, ability, starter, stats";

    /** A stored tool row plus who has it. */
    record Row(ToolData tool, UUID owner, List<ToolCodec.Owner> history, UUID blueprintId, String source, long createdAt) {
    }

    /** Thrown when a blueprint's unique id was already used for a tool (a duplicated blueprint item). */
    static final class BlueprintUsedException extends SQLException {
        BlueprintUsedException() {
            super("blueprint already used");
        }
    }

    // ------------------------------------------------------------------ tools

    /** Inserts a new tool. {@code blueprintId} (nullable) must be unused, otherwise {@link BlueprintUsedException}. */
    void insert(Connection c, ToolData tool, UUID owner, String ownerName, UUID blueprintId, String source, int season, long now)
            throws SQLException {
        if (blueprintId != null && Sql.queryOne(c, "SELECT tool_id FROM tools WHERE blueprint_id = ? FOR UPDATE",
                rs -> Boolean.TRUE, blueprintId).isPresent()) {
            throw new BlueprintUsedException();
        }
        String history = owner == null ? "[]" : ToolCodec.owners(List.of(new ToolCodec.Owner(owner, ownerName, now)));
        try {
            Sql.update(c, "INSERT INTO tools (" + COLUMNS + ", owner, owner_history, blueprint_id, source, season_id, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tool.id(), tool.kind().id(), tool.tier(), tool.level(), tool.xp(), tool.prestige(), tool.prestigeSeason(),
                    ToolCodec.perks(tool.perks()), tool.skin(), tool.ability(), tool.starter(), ToolCodec.stats(tool.stats()),
                    owner, history, blueprintId, clip(source, 64), season, now, now);
        } catch (SQLException e) {
            if (blueprintId != null && isDuplicate(e)) {
                throw new BlueprintUsedException();
            }
            throw e;
        }
    }

    /**
     * Writes the current state of a tool (update; insert when the row is missing, e.g. an item from before this module).
     * When the holder changed, the new holder is appended to the owner history. Run inside a transaction.
     */
    void save(Connection c, ToolData tool, UUID holder, String holderName, int season, long now) throws SQLException {
        Optional<Object[]> stored = Sql.queryOne(c, "SELECT owner, owner_history FROM tools WHERE tool_id = ? FOR UPDATE",
                rs -> new Object[]{Sql.uuid(rs, "owner"), rs.getString("owner_history")}, tool.id());
        if (stored.isEmpty()) {
            insert(c, tool, holder, holderName, null, "restored", season, now);
            return;
        }
        UUID owner = (UUID) stored.get()[0];
        String history = (String) stored.get()[1];
        if (holder != null && !holder.equals(owner)) {
            List<ToolCodec.Owner> list = new ArrayList<>(ToolCodec.owners(history));
            list.add(new ToolCodec.Owner(holder, holderName, now));
            history = ToolCodec.owners(list);
            owner = holder;
        }
        Sql.update(c, "UPDATE tools SET tier = ?, level = ?, xp = ?, prestige = ?, prestige_season = ?, perks = ?, skin = ?, ability = ?,"
                        + " starter = ?, stats = ?, owner = ?, owner_history = ?, updated_at = ? WHERE tool_id = ?",
                tool.tier(), tool.level(), tool.xp(), tool.prestige(), tool.prestigeSeason(), ToolCodec.perks(tool.perks()), tool.skin(),
                tool.ability(), tool.starter(), ToolCodec.stats(tool.stats()), owner, history, now, tool.id());
    }

    /**
     * Forge upgrade, compare-and-set: moves the row from {@code fromTier} to {@code toTier}. A missing row is inserted
     * at the new tier. Returns false if the stored tier is not {@code fromTier} (a duplicated item was already upgraded).
     */
    boolean upgrade(Connection c, ToolData upgraded, int fromTier, UUID holder, String holderName, int season, long now) throws SQLException {
        Optional<Integer> stored = Sql.queryOne(c, "SELECT tier FROM tools WHERE tool_id = ? FOR UPDATE", rs -> rs.getInt("tier"), upgraded.id());
        if (stored.isPresent() && stored.get() != fromTier) {
            return false;
        }
        save(c, upgraded, holder, holderName, season, now);
        return true;
    }

    void savePerks(Connection c, UUID toolId, List<ToolPerk> perks, long now) throws SQLException {
        Sql.update(c, "UPDATE tools SET perks = ?, updated_at = ? WHERE tool_id = ?", ToolCodec.perks(perks), now, toolId);
    }

    Optional<Row> load(Connection c, UUID toolId) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + ", owner, owner_history, blueprint_id, source, created_at FROM tools WHERE tool_id = ?",
                ToolRepository::row, toolId);
    }

    private static Row row(ResultSet rs) throws SQLException {
        ToolKind kind = ToolKind.byId(rs.getString("kind")).orElse(ToolKind.PICKAXE);
        ToolData tool = new ToolData(Sql.uuid(rs, "tool_id"), kind, rs.getInt("tier"), rs.getInt("level"), rs.getDouble("xp"),
                rs.getInt("prestige"), rs.getInt("prestige_season"), ToolCodec.perks(rs.getString("perks")), rs.getString("skin"),
                rs.getString("ability"), rs.getBoolean("starter"), ToolCodec.stats(rs.getString("stats")));
        return new Row(tool, Sql.uuid(rs, "owner"), ToolCodec.owners(rs.getString("owner_history")), Sql.uuid(rs, "blueprint_id"),
                rs.getString("source"), rs.getLong("created_at"));
    }

    // ------------------------------------------------------------------ skins

    Set<String> skins(Connection c, UUID player) throws SQLException {
        return new LinkedHashSet<>(Sql.query(c, "SELECT skin FROM skins_owned WHERE player = ? ORDER BY obtained_at",
                rs -> rs.getString("skin"), player));
    }

    /** Adds a skin to a collection. Returns false if the player already owns it. Run inside a transaction. */
    boolean grantSkin(Connection c, UUID player, String skin, String source, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT skin FROM skins_owned WHERE player = ? AND skin = ? FOR UPDATE", rs -> Boolean.TRUE, player, skin).isPresent()) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO skins_owned (player, skin, source, obtained_at) VALUES (?, ?, ?, ?)", player, skin, clip(source, 64), now);
            return true;
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return false;
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ blueprints

    void recordBlueprint(Connection c, UUID player, String blueprint, int season, long now) throws SQLException {
        if (Sql.update(c, "UPDATE blueprints_found SET found = found + 1, last_at = ? WHERE player = ? AND blueprint = ? AND season_id = ?",
                now, player, blueprint, season) == 0) {
            Sql.update(c, "INSERT INTO blueprints_found (player, blueprint, season_id, found, first_at, last_at) VALUES (?, ?, ?, 1, ?, ?)",
                    player, blueprint, season, now, now);
        }
    }

    /** Blueprint ids a player found this season with their counts. */
    java.util.Map<String, Integer> blueprintsFound(Connection c, UUID player, int season) throws SQLException {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT blueprint, found FROM blueprints_found WHERE player = ? AND season_id = ? ORDER BY first_at",
                rs -> new Object[]{rs.getString("blueprint"), rs.getInt("found")}, player, season)) {
            out.put((String) row[0], (Integer) row[1]);
        }
        return out;
    }

    // ------------------------------------------------------------------ starter kit

    Optional<Long> lastClaim(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT last_claim FROM starter_claims WHERE player = ?", rs -> rs.getLong("last_claim"), player);
    }

    /**
     * Atomic starter kit claim: locks the player's row, checks the cooldown and stores the claim.
     *
     * @return 0 when claimed now, otherwise the milliseconds left (nothing changed)
     */
    long claim(Connection c, UUID player, long now, long cooldownMillis) throws SQLException {
        Optional<Long> last = Sql.queryOne(c, "SELECT last_claim FROM starter_claims WHERE player = ? FOR UPDATE",
                rs -> rs.getLong("last_claim"), player);
        long remaining = StarterKit.remainingMillis(last.orElse(null), now, cooldownMillis);
        if (remaining > 0) {
            return remaining;
        }
        if (last.isPresent()) {
            Sql.update(c, "UPDATE starter_claims SET last_claim = ?, claims = claims + 1 WHERE player = ?", now, player);
        } else {
            Sql.update(c, "INSERT INTO starter_claims (player, last_claim, claims) VALUES (?, ?, 1)", player, now);
        }
        return 0;
    }

    /** Undo a claim when nothing could be given (e.g. the player logged out in between). */
    void unclaim(Connection c, UUID player, Long previous) throws SQLException {
        if (previous == null) {
            Sql.update(c, "DELETE FROM starter_claims WHERE player = ?", player);
        } else {
            Sql.update(c, "UPDATE starter_claims SET last_claim = ?, claims = claims - 1 WHERE player = ?", previous, player);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isDuplicate(SQLException e) {
        String state = e.getSQLState();
        return state != null && (state.startsWith("23") || state.equals("1062")) || e.getErrorCode() == 1062 || e.getErrorCode() == 23505;
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
