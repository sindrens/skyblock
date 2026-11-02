package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * All milestone SQL (Complete Developer Spec §7.10-7.11). Every method takes the connection of one transaction, so a flush
 * writes progress, contributions and newly completed tiers together (a crash loses at most one flush interval, never a
 * half-written tier). No Bukkit; tested on H2.
 */
final class MilestoneStore {

    private MilestoneStore() {
    }

    /** A chain seen for the first time in a scope (discovery: "New milestone: Wheat Reaper"). */
    record Discovered(ProgressBuffer.Scope scope, UUID scopeId, String chainId) {
    }

    /** A tier reached during a flush (READY until claimed). */
    record Completed(ProgressBuffer.Scope scope, UUID scopeId, String chainId, int tier) {
    }

    /** What one flush did. */
    record FlushResult(List<Discovered> discovered, List<Completed> completed) {
    }

    /** One chain's state in a scope (menu). */
    record Row(String chainId, long progress, long bonus, int highestTier, int claimedTier) {
        long total() {
            return progress + bonus;
        }
    }

    /**
     * Writes a drained buffer. For each scope and chain: adds the amount (creating the row = discovery), adds island
     * contributions per member, then records every tier now reached that was not recorded yet.
     *
     * @param chains the catalog lookup (a chain removed from the config is skipped and its data kept)
     */
    static FlushResult flush(Connection c, Map<ProgressBuffer.Key, Long> batch,
                             Function<String, Optional<MilestoneModel.Chain>> chains, long now) throws SQLException {
        Map<ProgressBuffer.Key, Long> perScope = new HashMap<>();
        for (Map.Entry<ProgressBuffer.Key, Long> e : batch.entrySet()) {
            ProgressBuffer.Key k = e.getKey();
            perScope.merge(new ProgressBuffer.Key(k.scope(), k.scopeId(), k.chainId(), null), e.getValue(), Long::sum);
            if (k.scope() == ProgressBuffer.Scope.ISLAND && k.actor() != null) {
                int n = Sql.update(c, "UPDATE milestone_contrib SET amount = amount + ? WHERE island_id = ? AND chain_id = ? "
                        + "AND profile_key = ?", e.getValue(), k.scopeId(), k.chainId(), k.actor());
                if (n == 0) {
                    Sql.update(c, "INSERT INTO milestone_contrib (island_id, chain_id, profile_key, amount) VALUES (?, ?, ?, ?)",
                            k.scopeId(), k.chainId(), k.actor(), e.getValue());
                }
            }
        }
        List<Discovered> discovered = new ArrayList<>();
        List<Completed> completed = new ArrayList<>();
        for (Map.Entry<ProgressBuffer.Key, Long> e : perScope.entrySet()) {
            ProgressBuffer.Key k = e.getKey();
            Optional<MilestoneModel.Chain> chain = chains.apply(k.chainId());
            if (chain.isEmpty()) {
                continue;
            }
            String scope = k.scope().name();
            int n = Sql.update(c, "UPDATE milestone_progress SET progress = progress + ?, updated_at = ? WHERE scope_type = ? "
                    + "AND scope_id = ? AND chain_id = ?", e.getValue(), now, scope, k.scopeId(), k.chainId());
            if (n == 0) {
                Sql.update(c, "INSERT INTO milestone_progress (scope_type, scope_id, chain_id, progress, bonus_progress, discovered_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, 0, ?, ?)", scope, k.scopeId(), k.chainId(), e.getValue(), now, now);
                discovered.add(new Discovered(k.scope(), k.scopeId(), k.chainId()));
            }
            completed.addAll(recordTiers(c, k.scope(), k.scopeId(), chain.get(), now));
        }
        return new FlushResult(discovered, completed);
    }

    /** Records tiers reached by the stored total that are not recorded yet (idempotent; also used when a target is lowered). */
    static List<Completed> recordTiers(Connection c, ProgressBuffer.Scope scope, UUID scopeId, MilestoneModel.Chain chain, long now)
            throws SQLException {
        long total = Sql.queryOne(c, "SELECT progress + bonus_progress FROM milestone_progress WHERE scope_type = ? AND scope_id = ? "
                + "AND chain_id = ?", rs -> rs.getLong(1), scope.name(), scopeId, chain.id()).orElse(0L);
        int reached = chain.tierFor(total);
        int recorded = Sql.queryOne(c, "SELECT MAX(tier) FROM milestone_tiers WHERE scope_type = ? AND scope_id = ? AND chain_id = ?",
                rs -> rs.getInt(1), scope.name(), scopeId, chain.id()).orElse(0);
        List<Completed> out = new ArrayList<>();
        for (int tier = recorded + 1; tier <= reached; tier++) {
            Sql.update(c, "INSERT INTO milestone_tiers (scope_type, scope_id, chain_id, tier, completed_at) VALUES (?, ?, ?, ?, ?)",
                    scope.name(), scopeId, chain.id(), tier, now);
            out.add(new Completed(scope, scopeId, chain.id(), tier));
        }
        return out;
    }

    /** Every discovered chain of a scope with its progress, highest completed tier and highest claimed tier. */
    static List<Row> rows(Connection c, ProgressBuffer.Scope scope, UUID scopeId) throws SQLException {
        Map<String, Integer> tiers = new HashMap<>();
        for (Map.Entry<String, Integer> e : Sql.query(c, "SELECT chain_id, MAX(tier) FROM milestone_tiers WHERE scope_type = ? "
                + "AND scope_id = ? GROUP BY chain_id", rs -> Map.entry(rs.getString(1), rs.getInt(2)), scope.name(), scopeId)) {
            tiers.put(e.getKey(), e.getValue());
        }
        Map<String, Integer> claims = claimed(c, scope, scopeId);
        return Sql.query(c, "SELECT chain_id, progress, bonus_progress FROM milestone_progress WHERE scope_type = ? AND scope_id = ? "
                        + "ORDER BY discovered_at, chain_id", rs -> new Row(rs.getString(1), rs.getLong(2), rs.getLong(3),
                        tiers.getOrDefault(rs.getString(1), 0), claims.getOrDefault(rs.getString(1), 0)),
                scope.name(), scopeId);
    }

    /** Highest claimed tier per chain of a scope (the perks: OWNER B3, the highest replaces lower ones). */
    static Map<String, Integer> claimed(Connection c, ProgressBuffer.Scope scope, UUID scopeId) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : Sql.query(c, "SELECT chain_id, MAX(tier) FROM milestone_claims WHERE scope_type = ? "
                + "AND scope_id = ? GROUP BY chain_id", rs -> Map.entry(rs.getString(1), rs.getInt(2)), scope.name(), scopeId)) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Claims tier {@code tier} of a chain for a scope (spec §7.4): only when it is completed and not claimed; marks the
     * lower unclaimed completed tiers too. Returns false when the tier is not ready (not completed, or already claimed:
     * another member was faster), so nothing is written twice.
     */
    static boolean claim(Connection c, ProgressBuffer.Scope scope, UUID scopeId, String chainId, int tier, UUID profile, long now)
            throws SQLException {
        boolean completed = Sql.queryOne(c, "SELECT 1 FROM milestone_tiers WHERE scope_type = ? AND scope_id = ? AND chain_id = ? "
                + "AND tier = ?", rs -> 1, scope.name(), scopeId, chainId, tier).isPresent();
        boolean taken = Sql.queryOne(c, "SELECT 1 FROM milestone_claims WHERE scope_type = ? AND scope_id = ? AND chain_id = ? "
                + "AND tier = ?", rs -> 1, scope.name(), scopeId, chainId, tier).isPresent();
        if (!completed || taken) {
            return false;
        }
        for (int t = 1; t <= tier; t++) {
            boolean done = Sql.queryOne(c, "SELECT 1 FROM milestone_claims WHERE scope_type = ? AND scope_id = ? AND chain_id = ? "
                    + "AND tier = ?", rs -> 1, scope.name(), scopeId, chainId, t).isPresent();
            if (!done) {
                Sql.update(c, "INSERT INTO milestone_claims (scope_type, scope_id, chain_id, tier, claimed_by_profile, claimed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", scope.name(), scopeId, chainId, t, profile, now);
            }
        }
        return true;
    }

    /** A member's share of an island chain in percent (0-100), for "You contributed 18%". */
    static int contribution(Connection c, UUID island, String chainId, UUID profile) throws SQLException {
        long mine = Sql.queryOne(c, "SELECT amount FROM milestone_contrib WHERE island_id = ? AND chain_id = ? AND profile_key = ?",
                rs -> rs.getLong(1), island, chainId, profile).orElse(0L);
        long all = Sql.queryOne(c, "SELECT SUM(amount) FROM milestone_contrib WHERE island_id = ? AND chain_id = ?",
                rs -> rs.getLong(1), island, chainId).orElse(0L);
        return all <= 0 ? 0 : (int) Math.min(100, Math.round(mine * 100.0 / all));
    }
}
