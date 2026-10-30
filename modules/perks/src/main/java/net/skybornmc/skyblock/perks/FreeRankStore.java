package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code perks_freerank} table. Every write is one atomic statement: the primary key allows one claim per player,
 * and the tag can only be set while it is still empty, so double clicks or two servers can never give two rewards.
 */
final class FreeRankStore {

    private final Database db;

    FreeRankStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Optional<FreeRankRules.Claim>> load(UUID player) {
        return db.async(c -> Sql.queryOne(c, "SELECT rank_group, claimed_at, tag FROM perks_freerank WHERE uuid = ?",
                rs -> new FreeRankRules.Claim(rs.getString("rank_group"), rs.getLong("claimed_at"), rs.getString("tag")),
                Sql.toBytes(player)));
    }

    /** Inserts the claim; {@code false} if the player already claimed (primary key). */
    CompletableFuture<Boolean> claim(UUID player, String name, String group, long now) {
        return db.async(c -> {
            try {
                Sql.update(c, "INSERT INTO perks_freerank (uuid, name, rank_group, claimed_at, tag, tag_at) VALUES (?, ?, ?, ?, NULL, NULL)",
                        Sql.toBytes(player), name, group, now);
                return true;
            } catch (SQLException e) {
                if (duplicate(e)) {
                    return false;
                }
                throw e;
            }
        });
    }

    /** Undoes a claim whose rank could not be given (only while no tag was picked). */
    CompletableFuture<Boolean> unclaim(UUID player) {
        return db.async(c -> Sql.update(c, "DELETE FROM perks_freerank WHERE uuid = ? AND tag IS NULL", Sql.toBytes(player)) > 0);
    }

    /** Sets the tag once; {@code false} if there is no claim or a tag was already picked. */
    CompletableFuture<Boolean> pickTag(UUID player, String tag, long now) {
        return db.async(c -> Sql.update(c, "UPDATE perks_freerank SET tag = ?, tag_at = ? WHERE uuid = ? AND tag IS NULL",
                tag, now, Sql.toBytes(player)) == 1);
    }

    /** Undoes a tag pick whose tag could not be given. */
    CompletableFuture<Boolean> clearTag(UUID player, String tag) {
        return db.async(c -> Sql.update(c, "UPDATE perks_freerank SET tag = NULL, tag_at = NULL WHERE uuid = ? AND tag = ?",
                Sql.toBytes(player), tag) == 1);
    }

    private static boolean duplicate(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException || e.getErrorCode() == 1062
                || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }
}
