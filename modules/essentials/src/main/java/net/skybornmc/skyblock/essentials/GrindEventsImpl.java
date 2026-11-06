package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.grind.GrindEvents;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Owner 6 Nov 2026: the grind-area events. A round runs {@code grind.events.active-min} to {@code active-max} random
 * events for {@code duration-seconds}, then a break of {@code break-min-seconds} to {@code break-max-seconds}. Every
 * running event has a boss bar for the players in the grind world; a round start says which events run (chat + sound).
 * One repeating task (1 s), no per-block work: the tools and the farm ask {@link #multiplier} when a block breaks.
 */
final class GrindEventsImpl implements GrindEvents {

    private final Core core;
    private final ModuleMessages msg;
    private final Supplier<EssentialsConfig> config;
    private final Supplier<World> world;
    private volatile Set<Kind> active = Set.of();
    private long roundEnds;
    private long nextRound;
    private final Map<Kind, BossBar> bars = new EnumMap<>(Kind.class);
    /** The event names of this round, parsed once at its start. */
    private final Map<Kind, Component> names = new EnumMap<>(Kind.class);
    private final Set<UUID> viewers = new HashSet<>();
    private Scheduler.Task task;

    GrindEventsImpl(Core core, ModuleMessages msg, Supplier<EssentialsConfig> config, Supplier<World> world) {
        this.core = core;
        this.msg = msg;
        this.config = config;
        this.world = world;
    }

    void start() {
        nextRound = System.currentTimeMillis() + breakMillis(config.get());
        task = core.scheduler().repeat("essentials:grind-events", 20L, 20L, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        endRound();
    }

    // ------------------------------------------------------------------ pure rules

    /** The kinds a config enables (unknown ids skipped). Pure. */
    static List<Kind> enabled(List<String> ids) {
        List<Kind> out = new ArrayList<>();
        for (String id : ids) {
            for (Kind k : Kind.values()) {
                if (k.id().equalsIgnoreCase(id.strip()) && !out.contains(k)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** {@code count} different kinds picked at random from {@code from} (all of them when there are fewer). Pure apart from the random. */
    static Set<Kind> pick(List<Kind> from, int count, java.util.Random random) {
        List<Kind> pool = new ArrayList<>(from);
        Collections.shuffle(pool, random);
        Set<Kind> out = EnumSet.noneOf(Kind.class);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            out.add(pool.get(i));
        }
        return out;
    }

    /** The configured multiplier of a kind (INSTANT_REGROW is a switch: 1). Pure. */
    static double value(EssentialsConfig c, Kind kind) {
        return switch (kind) {
            case DOUBLE_XP, DOUBLE_DROPS, DOUBLE_SHARDS -> c.grindEventDouble;
            case PROC_FRENZY -> c.grindEventProcs;
            case GOLDEN_BLOCKS -> c.grindEventGolden;
            case INSTANT_REGROW -> 1.0;
        };
    }

    private static long breakMillis(EssentialsConfig c) {
        int lo = Math.max(5, Math.min(c.grindEventBreakMin, c.grindEventBreakMax));
        int hi = Math.max(lo, c.grindEventBreakMax);
        return 1000L * ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    // ------------------------------------------------------------------ the service

    @Override
    public Set<Kind> active() {
        return active;
    }

    @Override
    public boolean active(Player player, Kind kind) {
        World w = world.get();
        return player != null && w != null && active.contains(kind) && player.getWorld().equals(w);
    }

    @Override
    public double multiplier(Player player, Kind kind) {
        return active(player, kind) ? value(config.get(), kind) : 1.0;
    }

    /** Seconds until the next round (break) or until this round ends; for the scoreboard. */
    long secondsLeft() {
        long now = System.currentTimeMillis();
        return Math.max(0, ((active.isEmpty() ? nextRound : roundEnds) - now + 999) / 1000);
    }

    // ------------------------------------------------------------------ the timer (main thread, every second)

    private void tick() {
        EssentialsConfig c = config.get();
        World w = world.get();
        long now = System.currentTimeMillis();
        if (!c.grindEventsEnabled || w == null) {
            if (!active.isEmpty()) {
                endRound();
            }
            return;
        }
        if (active.isEmpty() && now >= nextRound) {
            startRound(c, w);
        } else if (!active.isEmpty() && now >= roundEnds) {
            endRound();
            nextRound = now + breakMillis(c);
        }
        updateBars(c, w, now);
    }

    private void startRound(EssentialsConfig c, World w) {
        List<Kind> kinds = enabled(c.grindEventKinds);
        if (kinds.isEmpty()) {
            nextRound = System.currentTimeMillis() + breakMillis(c);
            return;
        }
        int lo = Math.max(1, Math.min(c.grindEventActiveMin, c.grindEventActiveMax));
        int hi = Math.max(lo, c.grindEventActiveMax);
        Set<Kind> chosen = pick(kinds, ThreadLocalRandom.current().nextInt(lo, hi + 1), ThreadLocalRandom.current());
        active = Collections.unmodifiableSet(chosen);
        roundEnds = System.currentTimeMillis() + 1000L * Math.max(5, c.grindEventSeconds);
        List<Component> list = new ArrayList<>();
        for (Kind k : chosen) {
            Component name = msg.component(Audience.empty(), "grind.events.name." + k.id());
            names.put(k, name);
            list.add(name);
            bars.put(k, BossBar.bossBar(name, 1f, color(k), BossBar.Overlay.PROGRESS));
        }
        Component joined = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(
                msg.component(Audience.empty(), "grind.events.separator")), list);
        for (Player p : w.getPlayers()) {
            msg.send(p, "grind.events.start", Msg.c("events", joined), Msg.number("seconds", Math.max(5, c.grindEventSeconds)));
            core.sounds().play(p, "unlock");
        }
    }

    private void endRound() {
        for (BossBar bar : bars.values()) {
            for (UUID id : viewers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        bars.clear();
        names.clear();
        viewers.clear();
        active = Set.of();
    }

    private void updateBars(EssentialsConfig c, World w, long now) {
        if (bars.isEmpty()) {
            return;
        }
        List<Player> players = w.getPlayers();
        if (players.isEmpty() && viewers.isEmpty()) {
            return; // nobody to show it to: no text work
        }
        long total = 1000L * Math.max(5, c.grindEventSeconds);
        float progress = Math.max(0f, Math.min(1f, (roundEnds - now) / (float) total));
        long seconds = Math.max(0, (roundEnds - now + 999) / 1000);
        // One parse per tick for the time, the names were parsed once at the round start.
        Component time = msg.component(Audience.empty(), "grind.events.bar", Msg.number("seconds", seconds));
        for (Map.Entry<Kind, BossBar> e : bars.entrySet()) {
            e.getValue().progress(progress);
            e.getValue().name(names.getOrDefault(e.getKey(), Component.empty()).append(time));
        }
        Set<UUID> here = new HashSet<>();
        for (Player p : players) {
            here.add(p.getUniqueId());
            if (viewers.add(p.getUniqueId())) {
                bars.values().forEach(p::showBossBar);
            }
        }
        for (var it = viewers.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!here.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    bars.values().forEach(p::hideBossBar);
                }
                it.remove();
            }
        }
    }

    private static BossBar.Color color(Kind k) {
        return switch (k) {
            case DOUBLE_XP -> BossBar.Color.GREEN;
            case DOUBLE_DROPS -> BossBar.Color.BLUE;
            case DOUBLE_SHARDS -> BossBar.Color.PURPLE;
            case INSTANT_REGROW -> BossBar.Color.WHITE;
            case PROC_FRENZY -> BossBar.Color.RED;
            case GOLDEN_BLOCKS -> BossBar.Color.YELLOW;
        };
    }
}
