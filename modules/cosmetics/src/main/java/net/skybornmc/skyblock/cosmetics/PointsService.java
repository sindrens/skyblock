package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.cosmetics.CosmeticPoints;
import net.skybornmc.skyblock.common.db.Sql;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CosmeticPoints} on the database (tables {@code cosmetic_points} and {@code cosmetic_points_log}). One
 * transaction per change: the log row first (its key is the primary key: a replay is a duplicate key and changes
 * nothing), then the balance. A spend that would go below 0 rolls back. Also the playtime points: every online minute
 * counts, every {@code points.playtime-minutes} minutes give {@code points.playtime-points}.
 */
final class PointsService implements CosmeticPoints, Listener {

    private final Core core;
    private final CosmeticsConfig config;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> minutes = new ConcurrentHashMap<>();

    PointsService(Core core, CosmeticsConfig config) {
        this.core = core;
        this.config = config;
    }

    @Override
    public long cached(UUID account) {
        return balances.getOrDefault(account, 0L);
    }

    @Override
    public CompletableFuture<Boolean> add(UUID account, long amount, String reason, String key) {
        return change(account, amount, reason, key);
    }

    @Override
    public CompletableFuture<Boolean> spend(UUID account, long amount, String reason, String key) {
        return change(account, -amount, reason, key);
    }

    /** One change; true when done (or done before with this key), false when a spend is too big. */
    private CompletableFuture<Boolean> change(UUID account, long delta, String reason, String key) {
        if (delta == 0 || key == null || key.length() > 96) {
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        return core.database().transaction(c -> {
            if (Sql.queryOne(c, "SELECT 1 FROM cosmetic_points_log WHERE change_key = ?", rs -> 1, key).isPresent()) {
                return Long.MIN_VALUE; // a replay: nothing changes
            }
            long have = balance(c, account);
            if (have + delta < 0) {
                return -1L;
            }
            Sql.update(c, "INSERT INTO cosmetic_points_log (change_key, account_uuid, amount, reason, created_at) VALUES (?, ?, ?, ?, ?)",
                    key, account, delta, reason == null ? "unknown" : reason.length() > 32 ? reason.substring(0, 32) : reason, now);
            if (Sql.update(c, "UPDATE cosmetic_points SET points = points + ?, updated_at = ? WHERE account_uuid = ?", delta, now, account) == 0) {
                Sql.update(c, "INSERT INTO cosmetic_points (account_uuid, points, updated_at) VALUES (?, ?, ?)", account, delta, now);
            }
            return have + delta;
        }).thenApply(after -> {
            if (after == -1L) {
                return false;
            }
            if (after != Long.MIN_VALUE) {
                balances.computeIfPresent(account, (k, v) -> after);
            }
            return true;
        });
    }

    static long balance(Connection c, UUID account) throws SQLException {
        return Sql.queryOne(c, "SELECT points FROM cosmetic_points WHERE account_uuid = ?", rs -> rs.getLong(1), account).orElse(0L);
    }

    void load(Player p) {
        UUID id = p.getUniqueId();
        core.database().async(c -> balance(c, id)).thenAccept(points -> balances.put(id, points));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        balances.remove(event.getPlayer().getUniqueId());
        minutes.remove(event.getPlayer().getUniqueId());
    }

    /** Every minute (main thread): playtime points for online players. */
    void tick() {
        int every = Math.max(1, config.pointsPlaytimeMinutes);
        long points = config.pointsPlaytimePoints;
        if (points <= 0) {
            return;
        }
        long hour = System.currentTimeMillis() / 60_000L;
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            int m = minutes.merge(p.getUniqueId(), 1, Integer::sum);
            if (m >= every) {
                minutes.put(p.getUniqueId(), 0);
                add(p.getUniqueId(), points, "playtime", "play:" + p.getUniqueId() + ":" + hour);
            }
        }
    }
}
