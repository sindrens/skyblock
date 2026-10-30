package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weather and time of day per island (ISL2, {@code /is settings}): everyone in an island's slot sees the island's
 * weather (sunny, rain, storm) and time (day, noon, sunset, night, real) client-side through player weather/time, so
 * neighbours and the server's real world state are never affected. Default: always sunny, always day. "storm" is rain
 * plus thunder sounds only that player hears (Minecraft has no per-player thunderstorm). Leaving the island (or the
 * islands world) resets both. Applied on every presence change ({@link VisitManager#onPresence}) and setting change.
 */
final class IslandEnvironment implements Listener {

    private static final int TICKS_PER_SECOND = 20;

    private final IslandsModule module;
    private final IslandManager islands;
    /** Online player → island whose weather/time they see right now. */
    private final Map<UUID, UUID> applied = new HashMap<>();
    /** Storm players → next thunder (epoch millis). */
    private final Map<UUID, Long> nextThunder = new HashMap<>();
    private Scheduler.Task task;
    private long seconds;

    IslandEnvironment(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    private IslandsConfig config() {
        return module.config();
    }

    void start() {
        stop();
        task = module.core().scheduler().repeat("island-environment", TICKS_PER_SECOND, TICKS_PER_SECOND, this::second);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Resets everyone (module disable). */
    void resetAll() {
        for (UUID uuid : List.copyOf(applied.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                reset(player);
            }
        }
        applied.clear();
        nextThunder.clear();
    }

    /** The player is now in {@code island}'s slot ({@code null} = no island). */
    void apply(Player player, Island island) {
        if (island == null) {
            if (applied.remove(player.getUniqueId()) != null) {
                reset(player);
            }
            nextThunder.remove(player.getUniqueId());
            return;
        }
        applied.put(player.getUniqueId(), island.id());
        String weather = islands.flag(island, IslandFlag.WEATHER);
        player.setPlayerWeather("sunny".equals(weather) ? WeatherType.CLEAR : WeatherType.DOWNFALL);
        if ("storm".equals(weather) && config().stormThunderSeconds > 0) {
            nextThunder.putIfAbsent(player.getUniqueId(), System.currentTimeMillis() + thunderDelay());
        } else {
            nextThunder.remove(player.getUniqueId());
        }
        player.setPlayerTime(ticks(islands.flag(island, IslandFlag.TIME)), false);
    }

    /** A setting of {@code island} changed: re-apply for everyone who sees it. */
    void refresh(Island island) {
        for (Map.Entry<UUID, UUID> entry : List.copyOf(applied.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && island.id().equals(entry.getValue())) {
                apply(player, island);
            }
        }
    }

    private long ticks(String time) {
        if ("real".equals(time)) {
            return IslandClock.realTicks(LocalTime.now(zone()));
        }
        return config().timeTicks(time);
    }

    private String zoneSource;
    private java.time.ZoneId zone;

    /** The configured time zone, parsed once per config value (PERF4: was parsed per player per "real" refresh). */
    private java.time.ZoneId zone() {
        String id = config().timezone;
        if (zone == null || !java.util.Objects.equals(id, zoneSource)) {
            zone = VisitRules.zone(id);
            zoneSource = id;
        }
        return zone;
    }

    private long thunderDelay() {
        long base = config().stormThunderSeconds * 1000L;
        return base / 2 + ThreadLocalRandom.current().nextLong(Math.max(1, base));
    }

    private static void reset(Player player) {
        player.resetPlayerWeather();
        player.resetPlayerTime();
    }

    /** Once per second: thunder for storm islands, and the "real" clock every {@code flags.time.real-refresh-seconds}. */
    private void second() {
        seconds++;
        long now = System.currentTimeMillis();
        boolean realTick = seconds % Math.max(1, config().timeRealRefreshSeconds) == 0;
        for (Map.Entry<UUID, UUID> entry : List.copyOf(applied.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Island island = islands.byId(entry.getValue());
            if (player == null || island == null) {
                applied.remove(entry.getKey());
                nextThunder.remove(entry.getKey());
                continue;
            }
            if (realTick && "real".equals(islands.flag(island, IslandFlag.TIME))) {
                player.setPlayerTime(ticks("real"), false);
            }
            Long thunder = nextThunder.get(entry.getKey());
            if (thunder != null && now >= thunder) {
                nextThunder.put(entry.getKey(), now + thunderDelay());
                try {
                    player.playSound(player.getLocation(), config().stormThunderSound, SoundCategory.WEATHER,
                            (float) config().stormThunderVolume, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                } catch (RuntimeException ignored) {
                    // an invalid sound key in islands.yml: no thunder
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
        nextThunder.remove(event.getPlayer().getUniqueId());
    }
}
