package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.economy.EconomyConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * ECON-SELL-SHOP-AH A3: tells players which item is hot today. Every 30 seconds (async) it checks the Oslo day; the first
 * check of a new day inserts the day into {@code sell_hot_announced} and only the insert that wins broadcasts
 * ({@code sell.hot.announce}), so restarts and several servers on one database never announce twice. A player who joins
 * later that day gets one line ({@code sell.hot.join}) once per day ({@code sell_hot_seen}). Both are off with
 * {@code sell.hot-item.announce} / {@code join-line} and silent while the Hot Item is off.
 */
final class HotAnnouncer {

    static final long CHECK_TICKS = 600L;
    static final long JOIN_DELAY_TICKS = 60L;
    static final long PURGE_TICKS = 72_000L;
    /** Days of rows kept (sell_hot_seen / sell_hot_announced). */
    static final int KEEP_SEEN_DAYS = 7;
    static final int KEEP_ANNOUNCED_DAYS = 60;

    private final Core core;
    private final SellFeature feature;
    private final Supplier<EconomyConfig> config;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** The last day this server knows is announced (by it or another), so the check is a field read most of the time. */
    private volatile long announcedDay = Long.MIN_VALUE;

    HotAnnouncer(Core core, SellFeature feature, Supplier<EconomyConfig> config) {
        this.core = core;
        this.feature = feature;
        this.config = config;
    }

    void start() {
        tasks.add(core.scheduler().asyncRepeat(CHECK_TICKS, CHECK_TICKS, this::check));
        tasks.add(core.scheduler().asyncRepeat(PURGE_TICKS, PURGE_TICKS, this::purge));
    }

    void stop() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
    }

    static long today() {
        return LocalDate.now(Format.zone()).toEpochDay();
    }

    /** Async: announce today's Hot Item once. */
    void check() {
        EconomyConfig c = config.get();
        long day = today();
        if (!c.hotAnnounce || day == announcedDay || !feature.ready()) {
            return;
        }
        String hot = feature.service().hotKey();
        if (hot == null) {
            return;
        }
        boolean won;
        try {
            won = core.database().blocking(conn -> claimDay(conn, day, hot, System.currentTimeMillis()));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Sell: could not record the Hot Item announcement", e);
            return;
        }
        announcedDay = day;
        if (!won) {
            return;
        }
        String percent = SellFeature.percentText(c.sellHotPercent);
        core.scheduler().main("sell-hot-announce", () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                feature.msg.send(p, "sell.hot.announce", Msg.c("item", SellFeature.keyName(hot)), Msg.p("percent", percent));
            }
            // Players online now got the broadcast: no join line for them today.
            markSeen(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList(), day);
        });
        core.audit().log(AuditCategory.ECONOMY, "sell_hot_announce", Actor.system("sell"), Map.<String, Object>of("item", hot, "day", day,
                "percent", c.sellHotPercent));
    }

    /** True when this call inserted the day (only that caller broadcasts). */
    static boolean claimDay(Connection c, long day, String item, long now) throws SQLException {
        try {
            Sql.update(c, "INSERT INTO sell_hot_announced (day, item_key, announced_at) VALUES (?, ?, ?)", (int) day, item, now);
            return true;
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return false;
            }
            throw e;
        }
    }

    /** True the first time this player is seen on {@code day} (the row is moved to that day). */
    static boolean firstToday(Connection c, UUID player, long day) throws SQLException {
        if (Sql.update(c, "UPDATE sell_hot_seen SET day = ? WHERE player_uuid = ? AND day < ?", (int) day, player, (int) day) == 1) {
            return true;
        }
        try {
            Sql.update(c, "INSERT INTO sell_hot_seen (player_uuid, day) VALUES (?, ?)", player, (int) day);
            return true;
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return false; // already seen today
            }
            throw e;
        }
    }

    static boolean isDuplicate(SQLException e) {
        return e instanceof java.sql.SQLIntegrityConstraintViolationException
                || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }

    private void markSeen(List<UUID> players, long day) {
        if (players.isEmpty()) {
            return;
        }
        core.database().async(conn -> {
            for (UUID id : players) {
                firstToday(conn, id, day);
            }
            return null;
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Sell: could not save Hot Item notices", e);
            return null;
        });
    }

    /** Main thread, on join: one line a little later, once per day, after today's announcement went out. */
    void onJoin(Player player) {
        EconomyConfig c = config.get();
        if (!c.hotJoinLine) {
            return;
        }
        UUID id = player.getUniqueId();
        core.scheduler().later("sell-hot-join", JOIN_DELAY_TICKS, () -> {
            String hot = feature.ready() ? feature.service().hotKey() : null;
            long day = today();
            if (hot == null || !player.isOnline()) {
                return;
            }
            core.database().async(conn -> firstToday(conn, id, day)).whenComplete((first, error) -> {
                if (error != null || !Boolean.TRUE.equals(first)) {
                    return;
                }
                String percent = SellFeature.percentText(config.get().sellHotPercent);
                core.scheduler().main("sell-hot-join-line", () -> {
                    if (player.isOnline()) {
                        feature.msg.send(player, "sell.hot.join", Msg.c("item", SellFeature.keyName(hot)), Msg.p("percent", percent));
                    }
                });
            });
        });
    }

    /** Async, hourly: old rows go. */
    void purge() {
        long day = today();
        core.database().async(conn -> {
            Sql.update(conn, "DELETE FROM sell_hot_seen WHERE day < ?", (int) (day - KEEP_SEEN_DAYS));
            Sql.update(conn, "DELETE FROM sell_hot_announced WHERE day < ?", (int) (day - KEEP_ANNOUNCED_DAYS));
            return null;
        }).exceptionally(e -> null);
    }
}
