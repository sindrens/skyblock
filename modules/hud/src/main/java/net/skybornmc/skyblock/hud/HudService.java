package net.skybornmc.skyblock.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.Msg;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The sidebar scoreboard (M1.4). Every player gets a personal scoreboard; lines come from {@code messages/hud.yml}
 * ({@code scoreboard.<layout>.title/lines}) with {@code <ph:key>} placeholders and the built-in values below.
 *
 * <p>Per-place layouts (SB): {@code hud.yml → places} picks the layout from where the player is ({@link PlaceRules}):
 * own island, someone else's island, The Depths (variants for The Gate and each wing), spawn, warzone. The board
 * switches on the tick after a teleport/respawn and right away on a world change; only changed lines are sent, so
 * switching never flickers. Lines may be conditional ({@code if:key|...}, {@link LayoutLines}).</p>
 *
 * <p>Published as a service so other modules can: add a layout for their context ({@link #addLayout}, e.g. The
 * Depths), add nametag teams to a player's board ({@link #scoreboard}), toggle it ({@link #setEnabled}; the
 * {@code scoreboard} toggle of {@code /settings} is registered by {@link HudModule}).</p>
 */
public final class HudService implements Listener {

    static final String MODULE = "hud";
    /** The {@code /settings} toggle (F1): off hides the sidebar, right away. Stored as {@code setting.scoreboard}. */
    public static final String SETTING = "scoreboard";
    /** Profile key of the value from before F1 ({@code "off"} hid the sidebar); moved to the toggle on join. */
    static final String LEGACY_KEY = "scoreboard";
    static final String STORAGE_KEY = "setting." + SETTING;

    private record LayoutRule(String owner, String layout, Predicate<Player> applies) {
    }

    private final Core core;
    private final HudConfig config;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final List<LayoutRule> rules = new CopyOnWriteArrayList<>();
    /** Players whose board switches on the next tick (teleport, respawn). */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    /** Parsed layout lines by layout name (main thread). */
    private final Map<String, LayoutLines> layouts = new HashMap<>();
    private volatile PlaceRules.Settings settings;
    private volatile PlayerSettings playerSettings;
    private BukkitTask task;
    private long tick;

    HudService(Core core, HudConfig config) {
        this.core = core;
        this.config = config;
        reloaded();
    }

    /** Re-reads the place settings of hud.yml (call after the config is reloaded). */
    void reloaded() {
        settings = PlaceRules.Settings.of(config, problem -> core.plugin().getLogger().warning(problem));
        layouts.clear();
    }

    // ------------------------------------------------------------------ public API

    /**
     * Uses {@code layout} ({@code scoreboard.<layout>} in messages/hud.yml) for players matching {@code applies}.
     * Rules are checked in the order they were added and win over the places of hud.yml; without a match the place
     * layout, then the world name, then {@code default} is used.
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
        return visible(config.scoreboardEnabled, config.disabledWorlds.contains(player.getWorld().getName()), wantsSidebar(player));
    }

    /** Whether the sidebar shows (pure, for tests): on in hud.yml, not in a disabled world, and the player's toggle on. */
    static boolean visible(boolean enabledInConfig, boolean worldDisabled, boolean playerToggle) {
        return enabledInConfig && !worldDisabled && playerToggle;
    }

    /** The player's {@code scoreboard} toggle; read from the profile directly while the settings service is not there. */
    private boolean wantsSidebar(Player player) {
        PlayerSettings s = playerSettings;
        if (s != null) {
            return s.get(player, SETTING);
        }
        return core.players().get(player.getUniqueId()).map(d -> !"false".equals(d.setting(STORAGE_KEY, null))).orElse(true);
    }

    /** Turns the sidebar on/off for a player (the /settings toggle, saved in their profile and audited by it). */
    public void setEnabled(Player player, boolean enabled) {
        PlayerSettings s = playerSettings;
        if (s != null) {
            s.set(player, SETTING, enabled); // fires the change listener, which refreshes
            return;
        }
        refresh(player);
    }

    /** Hooks the /settings service (one tick after enable: the menus module enables after hud). */
    void useSettings(PlayerSettings service) {
        this.playerSettings = service;
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    /**
     * The stored toggle value after moving a pre-F1 value (pure, for tests): legacy {@code "off"} becomes {@code "false"}
     * unless the toggle already has a value; anything else keeps the current value.
     */
    static String migrated(String legacy, String current) {
        if (current == null && "off".equals(legacy)) {
            return "false";
        }
        return current;
    }

    private void migrateLegacy(Player player) {
        core.players().get(player.getUniqueId()).ifPresent(d -> {
            String legacy = d.setting(LEGACY_KEY, null);
            if (legacy == null) {
                return;
            }
            String current = d.setting(STORAGE_KEY, null);
            String next = migrated(legacy, current);
            if (next != null && !next.equals(current)) {
                d.setSetting(STORAGE_KEY, next);
            }
            d.setSetting(LEGACY_KEY, null);
        });
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
        migrateLegacy(player);
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
            UUID id = player.getUniqueId();
            boolean switched = !pending.isEmpty() && pending.remove(id);
            if (switched || SidebarLines.due(id.hashCode(), tick, interval)) {
                refresh(player);
            }
        }
        if (!pending.isEmpty()) {
            pending.removeIf(id -> Bukkit.getPlayer(id) == null);
        }
    }

    // ------------------------------------------------------------------ rendering

    /** The chosen layout and, on an island, the name of the island the player stands on (for {@code <island_here>}). */
    private record Choice(String layout, String islandHere) {
    }

    private boolean layoutExists(String name) {
        return core.messages().has(MODULE, "scoreboard." + name + ".lines");
    }

    private Choice choose(Player player) {
        for (LayoutRule rule : rules) {
            try {
                if (rule.applies().test(player)) {
                    return new Choice(rule.layout(), null);
                }
            } catch (RuntimeException e) {
                core.plugin().getLogger().warning("Scoreboard layout rule of " + rule.owner() + " failed: " + e.getMessage());
            }
        }
        World world = player.getWorld();
        String worldName = world.getName();
        PlaceRules.Settings s = settings;
        IslandService islands = core.services().optional(IslandService.class).orElse(null);
        boolean islandWorld = islands != null && islands.isIslandWorld(world);
        PlaceRules.IslandHere here = PlaceRules.IslandHere.NONE;
        String islandHere = null;
        if (islands != null && (islandWorld || s.islandWorlds().contains(worldName))) {
            Optional<IslandService.IslandInfo> slot = islands.islandInSlot(player.getLocation());
            if (slot.isPresent()) {
                IslandService.IslandInfo info = slot.get();
                here = islands.isMember(info.id(), player.getUniqueId()) ? PlaceRules.IslandHere.OWN : PlaceRules.IslandHere.OTHER;
                islandHere = info.name();
            }
        }
        PlaceRules.Place place = PlaceRules.place(s, worldName, islandWorld, here);
        String variantText = place == PlaceRules.Place.DEPTHS ? text(player, config.depthsVariantPlaceholder).orElse(null) : null;
        return new Choice(PlaceRules.layout(s, place, worldName, variantText, this::layoutExists), islandHere);
    }

    /** Parsed lines of a layout; re-parsed only when the message text changed (reload). */
    private LayoutLines lines(String layout) {
        String raw = core.messages().raw(MODULE, "scoreboard." + layout + ".lines");
        LayoutLines cached = layouts.get(layout);
        if (cached != null && cached.source() == raw) {
            return cached;
        }
        LayoutLines parsed = LayoutLines.parse(raw);
        layouts.put(layout, parsed);
        return parsed;
    }

    private void render(Player player, Sidebar sidebar) {
        Choice choice = choose(player);
        String layout = choice.layout();
        TagResolver values = values(player, choice);
        String title = core.messages().raw(MODULE, "scoreboard." + layout + ".title");
        sidebar.title(title == null ? Component.empty() : core.messages().parse(player, title, values));
        List<LayoutLines.Line> visible = lines(layout).visible(
                key -> LayoutLines.present(core.placeholders().text(player, key).orElse(null)), config.maxLines);
        List<Component> lines = new ArrayList<>(visible.size());
        for (LayoutLines.Line line : visible) {
            lines.add(line.blank() ? Component.empty() : core.messages().parse(player, line.text(), values));
        }
        sidebar.lines(lines);
    }

    /**
     * Built-in values: name, rank, money, crystals, gems, island, island_here, objective, online. "-" when a feature
     * isn't there yet. Values are computed only when the layout uses them (online counts every player, so it is only
     * worked out on boards that show it). {@code <tokens>} is a deprecated alias of {@code <crystals>}.
     */
    private TagResolver values(Player player, Choice choice) {
        var ranks = core.ranks();
        var color = ranks.rankColor(player);
        return TagResolver.resolver(
                Msg.c("name", Component.text(player.getName(), color)),
                Msg.c("rank", Component.text(ranks.groupDisplayName(player), color)),
                lazy("money", () -> value(player, config.moneyKey, "values.money")),
                lazy("crystals", () -> value(player, config.crystalsKey, "values.crystals")),
                lazy("tokens", () -> value(player, config.crystalsKey, "values.crystals")),
                lazy("gems", () -> value(player, config.gemsKey, "values.gems")),
                lazy("island", () -> island(player)),
                lazy("island_here", () -> choice.islandHere() == null || choice.islandHere().isBlank() ? dash(player)
                        : core.messages().component(player, MODULE, "values.island-here", Msg.p("value", choice.islandHere()))),
                lazy("objective", () -> objective(player)),
                lazy("online", () -> Component.text(online(player))));
    }

    /** A tag whose value is only computed if the line uses it. */
    private static TagResolver lazy(String name, Supplier<? extends ComponentLike> value) {
        return TagResolver.resolver(name, (args, ctx) -> Tag.selfClosingInserting(value.get()));
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
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    /** The new location is only set after the event: switch the board on the next tick (once, however many teleports). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        pending.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        pending.add(event.getPlayer().getUniqueId());
    }
}
