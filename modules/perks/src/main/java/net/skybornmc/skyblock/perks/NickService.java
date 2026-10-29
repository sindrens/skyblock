package net.skybornmc.skyblock.perks;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Sql;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Nicknames: stored in {@code perks_nicknames}, cached while the player is online and applied as the player's
 * display name (so chat, tab and the {@code displayname} placeholder pick it up). The real name is always available
 * through {@link #realName} and the {@code realname} placeholder.
 */
public final class NickService implements Listener {

    /** A stored nickname. {@code setBy} uuid string of who set it (the player themself, or staff via /setnick), may be null */
    public record StoredNick(UUID uuid, String formatted, String plain, String setBy) {
        public Component component() {
            return NickRules.SAFE.deserialize(formatted);
        }

        /** True if staff set it for this player (then rank checks don't reset it). */
        public boolean setByStaff() {
            return setBy != null && !setBy.equals(uuid.toString());
        }
    }

    /** Why {@link #set} failed. */
    public enum SetResult { OK, TAKEN }

    private final Plugin plugin;
    private final Database db;
    private final Map<UUID, StoredNick> online = new ConcurrentHashMap<>();
    private final java.util.function.Supplier<Component> marker;
    private volatile java.util.function.Consumer<Player> onLoaded = p -> { };

    /** @param marker shown in front of every nickname (owner decision: {@code ~}), re-read on every apply */
    public NickService(Plugin plugin, Database db, java.util.function.Supplier<Component> marker) {
        this.plugin = plugin;
        this.db = db;
        this.marker = marker;
    }

    /** Called on the main thread after a player's nickname was loaded (rank checks). */
    public void onLoaded(java.util.function.Consumer<Player> callback) {
        this.onLoaded = callback;
    }

    /** Re-applies every online nickname (marker changed on reload). */
    public void reapplyAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            StoredNick nick = online.get(player.getUniqueId());
            if (nick != null) {
                apply(player, nick);
            }
        }
    }

    /** Loads nicknames of players already online (module enable / reload). */
    public void loadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player);
        }
    }

    /** Resets every online display name to the real name (module disable). */
    public void resetAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (online.remove(player.getUniqueId()) != null) {
                player.displayName(null);
            }
        }
        online.clear();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        db.async(c -> Sql.queryOne(c, "SELECT nick, nick_plain, set_by FROM perks_nicknames WHERE uuid = ?",
                        rs -> new StoredNick(uuid, rs.getString("nick"), rs.getString("nick_plain"), rs.getString("set_by")),
                        Sql.toBytes(uuid)))
                .thenAcceptAsync(found -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    found.ifPresentOrElse(nick -> apply(player, nick), () -> online.remove(uuid));
                    if (found.isPresent()) {
                        onLoaded.accept(player);
                    }
                }, new MainThread(plugin))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Could not load the nickname of " + player.getName(), Database.cause(error));
                    return null;
                });
    }

    private void apply(Player player, StoredNick nick) {
        online.put(player.getUniqueId(), nick);
        player.displayName(Component.empty().append(marker.get()).append(nick.component()));
    }

    /** The nickname of an online player, if any. */
    public Optional<StoredNick> nick(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    /** Visible nick letters of online players with a nickname (for tab completion of /realname). */
    public java.util.List<String> onlineNicks() {
        return online.values().stream().map(StoredNick::plain).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /**
     * Saves a nickname (async). Uniqueness is enforced by the database: another player using the same visible letters
     * gives {@link SetResult#TAKEN}. Applies it on the main thread if the target is online.
     */
    public CompletableFuture<SetResult> set(UUID target, NickRules.Nick nick, String setBy) {
        String lower = nick.plain().toLowerCase(Locale.ROOT);
        return db.transaction(c -> {
            boolean taken = Sql.queryOne(c, "SELECT uuid FROM perks_nicknames WHERE nick_lower = ? AND uuid <> ?",
                    rs -> Boolean.TRUE, lower, Sql.toBytes(target)).isPresent();
            if (taken) {
                return SetResult.TAKEN;
            }
            Sql.update(c, "DELETE FROM perks_nicknames WHERE uuid = ?", Sql.toBytes(target));
            try {
                Sql.update(c, "INSERT INTO perks_nicknames (uuid, nick, nick_plain, nick_lower, set_by, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                        Sql.toBytes(target), nick.formatted(), nick.plain(), lower, setBy, System.currentTimeMillis());
            } catch (SQLException e) {
                if (isUniqueViolation(e)) {
                    c.rollback();
                    return SetResult.TAKEN;
                }
                throw e;
            }
            return SetResult.OK;
        }).thenApplyAsync(result -> {
            Player player = Bukkit.getPlayer(target);
            if (result == SetResult.OK && player != null) {
                apply(player, new StoredNick(target, nick.formatted(), nick.plain(), setBy));
            }
            return result;
        }, new MainThread(plugin));
    }

    /** Removes a nickname (async); resets the display name if the player is online. Completes with true if one existed. */
    public CompletableFuture<Boolean> remove(UUID target) {
        return db.async(c -> Sql.update(c, "DELETE FROM perks_nicknames WHERE uuid = ?", Sql.toBytes(target)) > 0)
                .thenApplyAsync(removed -> {
                    online.remove(target);
                    Player player = Bukkit.getPlayer(target);
                    if (player != null) {
                        player.displayName(null);
                    }
                    return removed;
                }, new MainThread(plugin));
    }

    /** Finds who uses a nickname (visible letters, case-insensitive): online cache first, then the database. */
    public CompletableFuture<Optional<UUID>> realName(String nickPlain) {
        for (StoredNick nick : online.values()) {
            if (nick.plain().equalsIgnoreCase(nickPlain)) {
                return CompletableFuture.completedFuture(Optional.of(nick.uuid()));
            }
        }
        String lower = nickPlain.toLowerCase(Locale.ROOT);
        return db.async(c -> Sql.queryOne(c, "SELECT uuid FROM perks_nicknames WHERE nick_lower = ?",
                rs -> Sql.uuid(rs, "uuid"), lower));
    }

    private static boolean isUniqueViolation(SQLException e) {
        // MariaDB/MySQL: 1062 / 23000; H2: 23505.
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState()) || "23000".equals(e.getSQLState());
    }
}
