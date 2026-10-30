package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.progression.LevelSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL of the island level tables (MariaDB, H2 in MySQL mode for tests). Every method takes the caller's connection.
 * Column names avoid MariaDB reserved words ({@code island_level}, {@code history_day}, {@code sampled_at}).
 */
final class LevelStore {

    /** One stored island level. {@code points} = per source after the caps. */
    record LevelRow(UUID island, double total, double earned, int level, int bestLevel, long reachedAt,
                    Map<LevelSource, Double> points, double blockWorth, long bankLow, long scannedAt, long updatedAt) {
    }

    /** One stored chunk worth. */
    record WorthRow(UUID island, int chunkX, int chunkZ, double blocks, double spawners) {
    }

    /** One stored bank sample. */
    record BankRow(UUID island, long at, long balance) {
    }

    private static final String LEVEL_COLUMNS = "island_id, season_id, total_points, earned_points, island_level, best_level, "
            + "level_reached_at, pts_pillars, pts_upgrades, pts_achievements, pts_collections, pts_skills, pts_depths, pts_bank, "
            + "pts_blocks, block_worth, bank_low, blocks_scanned_at, updated_at";

    private static final String UPSERT_LEVEL = "INSERT INTO island_level (" + LEVEL_COLUMNS + ") VALUES "
            + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
            + "total_points = VALUES(total_points), earned_points = VALUES(earned_points), island_level = VALUES(island_level), "
            + "best_level = GREATEST(best_level, VALUES(best_level)), level_reached_at = VALUES(level_reached_at), "
            + "pts_pillars = VALUES(pts_pillars), pts_upgrades = VALUES(pts_upgrades), pts_achievements = VALUES(pts_achievements), "
            + "pts_collections = VALUES(pts_collections), pts_skills = VALUES(pts_skills), pts_depths = VALUES(pts_depths), "
            + "pts_bank = VALUES(pts_bank), pts_blocks = VALUES(pts_blocks), block_worth = VALUES(block_worth), "
            + "bank_low = VALUES(bank_low), blocks_scanned_at = GREATEST(blocks_scanned_at, VALUES(blocks_scanned_at)), "
            + "updated_at = VALUES(updated_at)";

    private static final String UPSERT_HISTORY = "INSERT INTO island_level_history (island_id, season_id, history_day, island_level, "
            + "earned_points, total_points, block_worth, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
            + "island_level = VALUES(island_level), earned_points = VALUES(earned_points), total_points = VALUES(total_points), "
            + "block_worth = VALUES(block_worth), updated_at = VALUES(updated_at)";

    private static final String UPSERT_WORTH = "INSERT INTO island_block_worth (island_id, season_id, chunk_x, chunk_z, blocks_worth, "
            + "spawners_worth, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE blocks_worth = VALUES(blocks_worth), "
            + "spawners_worth = VALUES(spawners_worth), updated_at = VALUES(updated_at)";

    private static final List<LevelSource> COLUMN_ORDER = List.of(LevelSource.PILLARS, LevelSource.UPGRADES, LevelSource.ACHIEVEMENTS,
            LevelSource.COLLECTIONS, LevelSource.SKILLS, LevelSource.DEPTHS, LevelSource.BANK, LevelSource.BLOCKS);

    List<LevelRow> loadLevels(Connection c, int season) throws SQLException {
        return Sql.query(c, "SELECT * FROM island_level WHERE season_id = ?", rs -> {
            Map<LevelSource, Double> points = new EnumMap<>(LevelSource.class);
            for (LevelSource s : COLUMN_ORDER) {
                points.put(s, rs.getDouble("pts_" + s.id()));
            }
            return new LevelRow(Sql.uuid(rs, "island_id"), rs.getDouble("total_points"), rs.getDouble("earned_points"),
                    rs.getInt("island_level"), rs.getInt("best_level"), rs.getLong("level_reached_at"), points,
                    rs.getDouble("block_worth"), rs.getLong("bank_low"), rs.getLong("blocks_scanned_at"), rs.getLong("updated_at"));
        }, season);
    }

    List<WorthRow> loadWorth(Connection c, int season) throws SQLException {
        return Sql.query(c, "SELECT island_id, chunk_x, chunk_z, blocks_worth, spawners_worth FROM island_block_worth WHERE season_id = ?",
                rs -> new WorthRow(Sql.uuid(rs, "island_id"), rs.getInt("chunk_x"), rs.getInt("chunk_z"), rs.getDouble("blocks_worth"),
                        rs.getDouble("spawners_worth")), season);
    }

    List<BankRow> loadBankSamples(Connection c, int season, long since) throws SQLException {
        return Sql.query(c, "SELECT island_id, sampled_at, balance FROM island_bank_samples WHERE season_id = ? AND sampled_at >= ? "
                        + "ORDER BY sampled_at",
                rs -> new BankRow(Sql.uuid(rs, "island_id"), rs.getLong("sampled_at"), rs.getLong("balance")), season, since);
    }

    /** The latest sample of every island older than {@code before} (the balance at the window start). */
    List<BankRow> loadLatestBefore(Connection c, int season, long before, long notOlderThan) throws SQLException {
        return Sql.query(c, "SELECT s.island_id, s.sampled_at, s.balance FROM island_bank_samples s JOIN (SELECT island_id, "
                        + "MAX(sampled_at) AS latest FROM island_bank_samples WHERE season_id = ? AND sampled_at < ? AND sampled_at >= ? "
                        + "GROUP BY island_id) m ON s.island_id = m.island_id AND s.sampled_at = m.latest WHERE s.season_id = ?",
                rs -> new BankRow(Sql.uuid(rs, "island_id"), rs.getLong("sampled_at"), rs.getLong("balance")),
                season, before, notOlderThan, season);
    }

    /** Saves one calculated level and today's history row (call inside a transaction). */
    void saveLevel(Connection c, int season, LevelRow row, int day) throws SQLException {
        List<Object> params = new ArrayList<>(List.of(Sql.toBytes(row.island()), season, row.total(), row.earned(), row.level(),
                row.bestLevel(), row.reachedAt()));
        for (LevelSource s : COLUMN_ORDER) {
            params.add(row.points().getOrDefault(s, 0.0));
        }
        params.addAll(List.of(row.blockWorth(), row.bankLow(), row.scannedAt(), row.updatedAt()));
        Sql.update(c, UPSERT_LEVEL, params.toArray());
        Sql.update(c, UPSERT_HISTORY, Sql.toBytes(row.island()), season, day, row.level(), row.earned(), row.total(),
                row.blockWorth(), row.updatedAt());
    }

    int saveWorth(Connection c, int season, List<WorthRow> rows, long now) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (WorthRow r : rows) {
            batch.add(new Object[]{Sql.toBytes(r.island()), season, r.chunkX(), r.chunkZ(), r.blocks(), r.spawners(), now});
        }
        Sql.batch(c, UPSERT_WORTH, batch);
        return rows.size();
    }

    void addBankSample(Connection c, int season, UUID island, long at, long balance) throws SQLException {
        Sql.update(c, "INSERT INTO island_bank_samples (island_id, season_id, sampled_at, balance) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE balance = LEAST(balance, VALUES(balance))", Sql.toBytes(island), season, at, balance);
    }

    /**
     * Deletes each island's bank samples older than its own keep time (the latest sample before the window start stays,
     * it is the balance at the window start). {@code keepFrom} = island → oldest sample time to keep.
     */
    int pruneBankSamples(Connection c, int season, Map<UUID, Long> keepFrom) throws SQLException {
        if (keepFrom.isEmpty()) {
            return 0;
        }
        List<Object[]> batch = new ArrayList<>(keepFrom.size());
        keepFrom.forEach((island, keep) -> batch.add(new Object[]{Sql.toBytes(island), season, keep}));
        int n = 0;
        for (int r : Sql.batch(c, "DELETE FROM island_bank_samples WHERE island_id = ? AND season_id = ? AND sampled_at < ?", batch)) {
            n += Math.max(0, r);
        }
        return n;
    }
}
