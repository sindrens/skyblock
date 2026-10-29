package net.skybornmc.skyblock.common.player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.text.BedrockCheck;
import net.skybornmc.skyblock.common.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player data lifecycle (Phase 0 M0.4):
 * <ul>
 *   <li>loaded in {@link AsyncPlayerPreLoginEvent}; if the database is slower than the timeout, the player gets a friendly
 *       "try again" message instead of joining with broken data;</li>
 *   <li>cached while online; dirty data saved every 60 s, on quit and flushed on shutdown;</li>
 *   <li>name changes go to {@code name_history}; playtime and Bedrock flag are tracked.</li>
 * </ul>
 */
public final class PlayerService implements Listener {

    private static final long TIMEOUT_SECONDS = 5;
    private static final long AUTOSAVE_TICKS = 20L * 60;
    private static final long NAMES_REFRESH_TICKS = 20L * 60 * 5;
    private static final int KNOWN_NAMES_LIMIT = 5000;

    private record Pending(PlayerRepository.Loaded loaded, long time) {
    }

    private final Plugin plugin;
    private final Database db;
    private final Messages messages;
    private final BedrockCheck bedrock;
    private final Logger log;
    private final PlayerRepository repo = new PlayerRepository();

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private final Set<String> knownNames = ConcurrentHashMap.newKeySet();
    private final List<BukkitTask> tasks = new ArrayList<>();

    public PlayerService(Plugin plugin, Database db, Messages messages, BedrockCheck bedrock) {
        this.plugin = plugin;
        this.db = db;
        this.messages = messages;
        this.bedrock = bedrock;
        this.log = plugin.getLogger();
    }

    /** Registers listeners and timers. Run the {@code core} migrations before calling this. */
    public void start() {
        messages.load("players");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, AUTOSAVE_TICKS, AUTOSAVE_TICKS));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::refreshKnownNames, 1L, NAMES_REFRESH_TICKS));
        // Players already online (plugin reload / late enable) get loaded too.
        for (Player player : Bukkit.getOnlinePlayers()) {
            db.transaction(c -> repo.loadOrCreate(c, player.getUniqueId(), player.getName(), System.currentTimeMillis()))
                    .thenAcceptAsync(loaded -> attach(player, loaded), runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                    .exceptionally(e -> {
                        log.log(Level.SEVERE, "Could not load data of online player " + player.getName(), Database.cause(e));
                        return null;
                    });
        }
    }

    // ------------------------------------------------------------------ public API

    /** Data of an online player. */
    public Optional<PlayerData> get(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    /** Data of an online player; throws if not loaded (use only for players you know are online). */
    public PlayerData require(Player player) {
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            throw new IllegalStateException("No player data loaded for " + player.getName());
        }
        return data;
    }

    public Collection<PlayerData> online() {
        return Collections.unmodifiableCollection(online.values());
    }

    /** Any player by name (online first, then the database). Case-insensitive. Completes off the main thread. */
    public CompletableFuture<Optional<PlayerData.Snapshot>> offline(String name) {
        for (PlayerData data : online.values()) {
            if (data.name().equalsIgnoreCase(name)) {
                return CompletableFuture.completedFuture(Optional.of(data.snapshot()));
            }
        }
        return db.async(c -> repo.findByName(c, name));
    }

    /** Any player by UUID (online first, then the database). Completes off the main thread. */
    public CompletableFuture<Optional<PlayerData.Snapshot>> offline(UUID uuid) {
        PlayerData data = online.get(uuid);
        if (data != null) {
            return CompletableFuture.completedFuture(Optional.of(data.snapshot()));
        }
        return db.async(c -> repo.findByUuid(c, uuid));
    }

    /** All names a player used, oldest first. */
    public CompletableFuture<List<PlayerRepository.NameRecord>> nameHistory(UUID uuid) {
        return db.async(c -> repo.nameHistory(c, uuid));
    }

    /** Known player names (online + recently seen), for offline-player tab completion. Never touches the database. */
    public Collection<String> knownNames() {
        return Collections.unmodifiableSet(knownNames);
    }

    /** {@link #knownNames()} as a suggestion source for the command framework. */
    public Supplier<Collection<String>> nameSuggestions() {
        return this::knownNames;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            PlayerRepository.Loaded loaded = db.transaction(c -> repo.loadOrCreate(c, uuid, name, System.currentTimeMillis()))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            pending.put(uuid, new Pending(loaded, System.currentTimeMillis()));
            if (loaded.previousName() != null) {
                log.info("Name change: " + loaded.previousName() + " → " + name + " (" + uuid + ")");
            }
        } catch (TimeoutException e) {
            log.warning("Player data of " + name + " took longer than " + TIMEOUT_SECONDS + " s to load; asked them to rejoin.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage("login.slow"));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.log(Level.SEVERE, "Could not load player data of " + name, Database.cause(e));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage("login.failed"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            // Should never happen; never let a player play without data.
            log.severe("No player data for " + player.getName() + " at join; kicking to protect their data.");
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(kickMessage("login.failed")));
            return;
        }
        attach(player, p.loaded());
    }

    private void attach(Player player, PlayerRepository.Loaded loaded) {
        if (!player.isOnline()) {
            return;
        }
        PlayerData data = new PlayerData(loaded.snapshot(), loaded.isNew());
        data.name(player.getName());
        data.bedrock(bedrock.isBedrock(player.getUniqueId()));
        data.startSession(System.currentTimeMillis());
        online.put(player.getUniqueId(), data);
        knownNames.add(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = online.remove(event.getPlayer().getUniqueId());
        if (data == null) {
            return;
        }
        data.endSession(System.currentTimeMillis());
        PlayerData.Snapshot snapshot = data.snapshot();
        db.run(c -> repo.save(c, List.of(snapshot))).exceptionally(e -> {
            log.log(Level.SEVERE, "Could not save player data of " + snapshot.name() + " on quit", Database.cause(e));
            return null;
        });
    }

    // ------------------------------------------------------------------ saving

    /** Every 60 s: folds playtime and writes all dirty players in one batch. */
    private void autosave() {
        long now = System.currentTimeMillis();
        pending.values().removeIf(p -> now - p.time() > TimeUnit.MINUTES.toMillis(1));
        List<PlayerData.Snapshot> dirty = new ArrayList<>();
        for (PlayerData data : online.values()) {
            data.checkpoint(now);
            if (data.dirty()) {
                dirty.add(data.snapshot());
                data.clean();
            }
        }
        if (dirty.isEmpty()) {
            return;
        }
        db.run(c -> repo.save(c, dirty)).exceptionally(e -> {
            log.log(Level.SEVERE, "Autosave of " + dirty.size() + " player(s) failed; will retry next cycle", Database.cause(e));
            dirty.forEach(s -> get(s.uuid()).ifPresent(PlayerData::markDirty));
            return null;
        });
    }

    private void refreshKnownNames() {
        db.async(c -> repo.recentNames(c, KNOWN_NAMES_LIMIT)).thenAccept(names -> {
            knownNames.addAll(names);
            if (knownNames.size() > KNOWN_NAMES_LIMIT * 2) {
                knownNames.retainAll(names);
                online.values().forEach(d -> knownNames.add(d.name()));
            }
        }).exceptionally(e -> {
            log.warning("Could not refresh known player names: " + Database.cause(e).getMessage());
            return null;
        });
    }

    /**
     * Stops timers and saves every online player <b>blocking</b>. Call in onDisable before modules that need player data
     * are gone and before {@link Database#close()}.
     */
    public void shutdown() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        HandlerList.unregisterAll(this);
        long now = System.currentTimeMillis();
        List<PlayerData.Snapshot> all = new ArrayList<>();
        for (PlayerData data : online.values()) {
            data.endSession(now);
            all.add(data.snapshot());
        }
        online.clear();
        if (all.isEmpty() || !db.isRunning()) {
            return;
        }
        try {
            db.blocking(c -> {
                repo.save(c, all);
                return null;
            });
            log.info("Saved player data of " + all.size() + " player(s).");
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not save player data at shutdown", Database.cause(e));
        }
    }

    private Component kickMessage(String key) {
        return messages.component(Audience.empty(), "players", key);
    }
}
