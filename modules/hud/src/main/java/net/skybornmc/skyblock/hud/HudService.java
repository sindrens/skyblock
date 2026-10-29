package net.skybornmc.skyblock.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * The sidebar scoreboard (M1.4). Every player gets a personal scoreboard; lines come from {@code messages/hud.yml}
 * ({@code scoreboard.<layout>.title/lines}) with {@code <ph:key>} placeholders and the built-in values below.
 *
 * <p>Published as a service so other modules can: add a layout for their context ({@link #addLayout}, e.g. The
 * Depths), add nametag teams to a player's board ({@link #scoreboard}), toggle it ({@link #setEnabled}, used by
 * {@code /settings}).</p>
 */
public final class HudService implements Listener {

    static final String MODULE = "hud";
    /** Player setting: {@code "off"} hides the sidebar. */
    public static final String SETTING = "scoreboard";

    private record LayoutRule(String owner, String layout, Predicate<Player> applies) {
    }

    private final Core core;
    private final HudConfig config;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final List<LayoutRule> rules = new CopyOnWriteArrayList<>();
    private BukkitTask task;
    private long tick;

    HudService(Core core, HudConfig config) {
        this.core = core;
        this.config = config;
    }

    // ------------------------------------------------------------------ public API

    /**
     * Uses {@code layout} ({@code scoreboard.<layout>} in messages/hud.yml) for players matching {@code applies}.
     * Rules are checked in the order they were added; without a match the world name, then {@code default} is used.
     */
    public void addLayout(String owner, String layout, Predicate<Player> applies) {
        rules.add(new LayoutRule(owner, layout, applies));
    }

    public void removeLayouts(String owner) {
        rules.removeIf(r -> r.owner().equals(owner));
    }

    /** The player's personal scoreboard (add nametag teams here, never replace it). */
    public Optional<Scoreboard> scoreboard(Player player) {
        return Optional.ofNullable(sidebars.get(player.getUniqueId())).map(Sidebar::board);
    }

    public boolean isEnabled(Player player) {
        return config.scoreboardEnabled && !config.disabledWorlds.contains(player.getWorld().getName())
                && core.players().get(player.getUniqueId()).map(d -> !"off".equals(d.setting(SETTING, null))).orElse(true);
    }

    /** Turns the sidebar on/off for a player (saved in their profile). */
    public void setEnabled(Player player, boolean enabled) {
        core.players().get(player.getUniqueId()).ifPresent(d -> d.setSetting(SETTING, enabled ? null : "off"));
        refresh(player);
    }

    /** Re-renders right away (otherwise it refreshes on its next interval). */
    public void refresh(Player player) {
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            return;
        }
        boolean enabled = isEnabled(player);
        sidebar.show(enabled);
        if (enabled) {
            render(player, sidebar);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        Bukkit.getOnlinePlayers().forEach(this::attach);
        task = Bukkit.getScheduler().runTaskTimer(core.plugin(), this::tick, 1L, 1L);
        core.ranks().onRankChange(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                refresh(p);
            }
        });
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Map.Entry<UUID, Sidebar> e : sidebars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            e.getValue().remove();
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        sidebars.clear();
    }

    private void attach(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Sidebar sidebar = new Sidebar(board);
        sidebars.put(player.getUniqueId(), sidebar);
        player.setScoreboard(board);
        refresh(player);
    }

    private void tick() {
        tick++;
        int interval = config.updateTicks;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (SidebarLines.due(player.getUniqueId().hashCode(), tick, interval)) {
                refresh(player);
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    private String layoutFor(Player player) {
        for (LayoutRule rule : rules) {
            try {
                if (rule.applies().test(player)) {
                    return rule.layout();
                }
            } catch (RuntimeException e) {
                core.plugin().getLogger().warning("Scoreboard layout rule of " + rule.owner() + " failed: " + e.getMessage());
            }
        }
        String world = player.getWorld().getName();
        return core.messages().has(MODULE, "scoreboard." + world + ".lines") ? world : "default";
    }

    private void render(Player player, Sidebar sidebar) {
        String layout = layoutFor(player);
        TagResolver values = values(player);
        String title = core.messages().raw(MODULE, "scoreboard." + layout + ".title");
        sidebar.title(title == null ? Component.empty() : core.messages().parse(player, title, values));
        String raw = core.messages().raw(MODULE, "scoreboard." + layout + ".lines");
        List<Component> lines = new ArrayList<>();
        if (raw != null) {
            for (String line : raw.split("<newline>", -1)) {
                if (lines.size() >= config.maxLines) {
                    break;
                }
                lines.add(line.isEmpty() ? Component.empty() : core.messages().parse(player, line, values));
            }
        }
        sidebar.lines(lines);
    }

    /** Built-in values: name, rank, money, tokens, gems, island, objective, online. "—" when a feature isn't there yet. */
    private TagResolver values(Player player) {
        var ranks = core.ranks();
        var color = ranks.rankColor(player);
        return TagResolver.resolver(
                Msg.c("name", Component.text(player.getName(), color)),
                Msg.c("rank", Component.text(ranks.groupDisplayName(player), color)),
                Msg.c("money", value(player, config.moneyKey, "values.money")),
                Msg.c("tokens", value(player, config.tokensKey, "values.tokens")),
                Msg.c("gems", value(player, config.gemsKey, "values.gems")),
                Msg.c("island", island(player)),
                Msg.c("objective", objective(player)),
                Msg.p("online", online(player)));
    }

    private Optional<String> text(Player player, String key) {
        if (key == null || key.isBlank() || !core.placeholders().has(key)) {
            return Optional.empty();
        }
        return core.placeholders().text(player, key).filter(s -> !s.isBlank());
    }

    private Component dash(Player player) {
        return core.messages().component(player, MODULE, "values.dash");
    }

    private Component value(Player player, String key, String template) {
        return text(player, key)
                .map(v -> core.messages().component(player, MODULE, template, Msg.p("value", v)))
                .orElseGet(() -> dash(player));
    }

    private Component island(Player player) {
        Optional<String> level = text(player, config.islandLevelKey);
        if (level.isEmpty()) {
            return dash(player);
        }
        Optional<String> rank = text(player, config.islandRankKey);
        return rank.isPresent()
                ? core.messages().component(player, MODULE, "values.island", Msg.p("level", level.get()), Msg.p("position", rank.get()))
                : core.messages().component(player, MODULE, "values.island-no-rank", Msg.p("level", level.get()));
    }

    private Component objective(Player player) {
        return text(player, config.objectiveKey)
                .map(v -> core.messages().component(player, MODULE, "values.objective", Msg.p("value", v)))
                .orElseGet(() -> core.messages().component(player, MODULE, "values.objective-default"));
    }

    private static int online(Player viewer) {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (viewer.canSee(p)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebars.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }
}
