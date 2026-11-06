package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Island level v2 rewards (owner 7 Nov 2026, plans/TOOLS_V5.md section 3): every new island level puts money in the
 * island bank, every 5 levels each member gets crystals and a crate key. Paid once per level and season: the highest paid
 * level is stored ({@code island_xp_rewards}) in the same transaction as the payments, and every payment has its own
 * idempotency key.
 */
final class XpRewards {

    static final String REASON = "island_level_reward";

    private final Core core;
    private final LevelConfig config;
    private final ModuleMessages msg;
    private final int season;
    private final Map<UUID, Integer> paid = new ConcurrentHashMap<>();
    private final Set<UUID> paying = ConcurrentHashMap.newKeySet();
    private volatile boolean loaded;

    XpRewards(Core core, LevelConfig config, ModuleMessages msg, int season) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.season = season;
    }

    void load() {
        core.database().async(c -> Sql.query(c, "SELECT island_id, paid_level FROM island_xp_rewards WHERE season_id = ?",
                rs -> Map.entry(Sql.uuid(rs, "island_id"), rs.getInt("paid_level")), season)).whenComplete((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: could not load the island level rewards (none paid until a restart)", error);
                return;
            }
            rows.forEach(e -> paid.merge(e.getKey(), e.getValue(), Math::max));
            loaded = true;
        });
    }

    /** The highest level whose rewards were paid (0 = none). */
    int paid(UUID island) {
        return paid.getOrDefault(island, 0);
    }

    /**
     * Pays the rewards of every level above the paid one up to {@code level}. Returns true when something new was
     * started (the caller then shows the level up). A first calculation after the switch to island XP pays the levels
     * the island already has, once.
     */
    boolean reach(UUID island, int level) {
        if (!loaded || island == null || level <= paid(island) || !paying.add(island)) {
            return false;
        }
        int from = paid(island) + 1;
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        IslandService islands = core.services().optional(IslandService.class).orElse(null);
        if (economy == null || islands == null) {
            paying.remove(island);
            return false;
        }
        List<UUID> members = new ArrayList<>(islands.members(island).keySet());
        List<Transaction> txs = new ArrayList<>();
        long money = 0;
        long crystals = 0;
        int keys = 0;
        for (int l = from; l <= level; l++) {
            long m = IslandXp.money(l, config.rewardMoneyPerLevel);
            if (m > 0) {
                money += m;
                txs.add(Transaction.deposit(AccountRef.island(island), Currency.MONEY, m, REASON).actor(Actor.system("progression"))
                        .reference("island_level:" + l).idempotencyKey(key(island, l, null)).detail("level", l));
            }
            long c = IslandXp.crystals(l, config.rewardCrystalsPerLevel);
            if (c > 0) {
                crystals += c;
                for (UUID member : members) {
                    txs.add(Transaction.deposit(AccountRef.player(member), Currency.CRYSTALS, c, REASON).actor(Actor.system("progression"))
                            .reference("island_level:" + l).idempotencyKey(key(island, l, member)).detail("level", l));
                }
            }
            if (IslandXp.key(l)) {
                keys++;
            }
        }
        long now = System.currentTimeMillis();
        long moneyTotal = money;
        long crystalTotal = crystals;
        int keyTotal = keys;
        java.util.concurrent.CompletableFuture<Boolean> done = txs.isEmpty()
                ? core.database().transaction(c -> mark(c, island, level, now))
                : economy.executeBatch(txs, c -> mark(c, island, level, now)).thenApply(r -> r.ok() ? Boolean.TRUE
                : r.status() == TxResult.Status.DUPLICATE ? Boolean.FALSE : null);
        done.whenComplete((ok, error) -> core.scheduler().main("progression-xp-rewards", () -> {
            paying.remove(island);
            if (error != null || ok == null) {
                core.plugin().getLogger().log(Level.WARNING, "Progression: island level rewards of " + island + " failed; tried again "
                        + "with the next level change", error);
                return;
            }
            paid.merge(island, level, Math::max);
            if (!ok) {
                return;
            }
            if (keyTotal > 0 && config.rewardKeyCrate != null && !config.rewardKeyCrate.isBlank()) {
                core.services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class).ifPresent(k -> members.forEach(m ->
                        k.giveKeys(m, config.rewardKeyCrate, keyTotal, REASON, Actor.system("progression"))));
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", from);
            details.put("to", level);
            details.put("money", moneyTotal);
            details.put("crystals_per_member", crystalTotal);
            details.put("keys_per_member", keyTotal);
            core.audit().log(AuditCategory.ISLAND, "island_level_reward", Actor.system("progression"), island.toString(), (long) level, null, details);
            ProfileService profiles = ProfileService.of(core.services());
            for (UUID member : members) {
                profiles.onlinePlayer(member).ifPresent(p -> {
                    msg.send(p, "level.reward.money", Msg.p("money", Format.money(moneyTotal)), Msg.number("level", level));
                    if (crystalTotal > 0 || keyTotal > 0) {
                        msg.send(p, "level.reward.bonus", Msg.p("crystals", Format.number(crystalTotal)), Msg.number("keys", keyTotal),
                                Msg.p("crate", config.rewardKeyCrate == null ? "" : config.rewardKeyCrate));
                    }
                });
            }
        }));
        return true;
    }

    private boolean mark(java.sql.Connection c, UUID island, int level, long now) throws java.sql.SQLException {
        int have = Sql.queryOne(c, "SELECT paid_level FROM island_xp_rewards WHERE island_id = ? AND season_id = ? FOR UPDATE",
                rs -> rs.getInt(1), island, season).orElse(-1);
        if (have >= level) {
            return false;
        }
        if (have < 0) {
            return Sql.update(c, "INSERT INTO island_xp_rewards (island_id, season_id, paid_level, updated_at) VALUES (?, ?, ?, ?)",
                    island, season, level, now) == 1;
        }
        return Sql.update(c, "UPDATE island_xp_rewards SET paid_level = ?, updated_at = ? WHERE island_id = ? AND season_id = ? "
                + "AND paid_level = ?", level, now, island, season, have) == 1;
    }

    /** {@code isl_lv:<season>:<island hex>:<level>[:<member hex>]}, at most ~85 characters (limit 96). */
    private String key(UUID island, int level, UUID member) {
        return "isl_lv:" + season + ":" + hex(island) + ":" + level + (member == null ? "" : ":" + hex(member));
    }

    private static String hex(UUID id) {
        return id.toString().replace("-", "");
    }
}
