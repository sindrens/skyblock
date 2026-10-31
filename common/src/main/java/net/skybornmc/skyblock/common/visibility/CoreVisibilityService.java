package net.skybornmc.skyblock.common.visibility;

import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.island.IslandJoinedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.service.ServiceRegistry;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core's {@link VisibilityService} (REVIEW3 D4). Keeps, per viewer, the set of players it has hidden from them and
 * applies only differences. Triggers: join (forced re-apply of the joiner's pairs), quit, world change, profile switch,
 * island join, the Show players choice, vanish, module rules ({@link #refresh}) and a light position check every
 * {@code region-check-ticks} that only reacts when a player enters or leaves a place where the choice applies.
 * Main thread only.
 */
public final class CoreVisibilityService implements VisibilityService, Listener {

    private record Owned<T>(String owner, String id, T value) {
    }

    /** Where a player stood at the last position check: world + inside a place where the choice applies. */
    private record Zone(String world, boolean inSettingZone) {
    }

    private final Plugin plugin;
    private final Supplier<VisibilityConfig> config;
    private final ServiceRegistry services;
    private final Scheduler scheduler;
    private final Logger log;
    private final Set<UUID> vanished = new HashSet<>();
    /** viewer → players this service has hidden from them. */
    private final Map<UUID, Set<UUID>> applied = new HashMap<>();
    private final Map<UUID, Zone> zones = new HashMap<>();
    /** PERF11: cached region lookups and the block each player stood on at their last position check. */
    private final ZoneTracker tracker = new ZoneTracker();
    /** PERF11: reused for every position read (main thread only), so a check allocates no Location. */
    private final Location scratch = new Location(null, 0, 0, 0);
    private final List<Owned<Rule>> rules = new CopyOnWriteArrayList<>();
    private final List<Owned<BiPredicate<Player, Player>>> relations = new CopyOnWriteArrayList<>();
    private Scheduler.Task zoneTask = Scheduler.Task.NONE;
    private volatile PlayerSettings settings;

    public CoreVisibilityService(Plugin plugin, Supplier<VisibilityConfig> config, ServiceRegistry services, Scheduler scheduler) {
        this.plugin = plugin;
        this.config = config;
        this.services = services;
        this.scheduler = scheduler;
        this.log = plugin.getLogger();
        // Built in: island members and coop players count as "friends" until a friends system adds itself.
        relations.add(new Owned<>("core", "island", this::sameIsland));
        Visibility.use(this);
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Called once every module is enabled: registers the {@code /settings} choice (the menus module publishes
     * PlayerSettings), starts the position check and applies everything for players already online.
     */
    public void start() {
        registerSetting();
        schedule();
        refreshAll();
    }

    /** After {@code /skyblock reload core}: the setting's default, the position check and everything applied again. */
    public void reload() {
        registerSetting();
        schedule();
        refreshAll();
    }

    /** (Re)starts the position check. */
    private void schedule() {
        zoneTask.cancel();
        long every = Math.max(5, config.get().regionCheckTicks);
        zoneTask = scheduler.repeat("visibility-zones", every, every, this::checkZones);
    }

    private void registerSetting() {
        PlayerSettings s = services.optional(PlayerSettings.class).orElse(null);
        if (s == null) {
            log.info("Visibility: /settings is not available, every player uses the default Show players choice ("
                    + config.get().defaultMode.id() + ").");
            return;
        }
        try {
            // Registered again on every reload (same owner replaces it), so a changed setting.default applies without a restart.
            s.register(SettingToggle.choice("core", SETTING, SettingCategory.DISPLAY, Material.SPYGLASS, ShowMode.ids(),
                    config.get().defaultMode.id(), null, "navigation", "settings.toggles." + SETTING));
            if (settings != s) {
                s.onChoiceChange(SETTING, (player, value) -> refreshAsViewer(player, false));
            }
            settings = s;
        } catch (RuntimeException e) {
            log.warning("Visibility: could not add the Show players setting to /settings: " + e.getMessage());
        }
    }

    /** Shows everyone to everyone again (plugin stop), so no hide of ours outlives the plugin. */
    public void shutdown() {
        zoneTask.cancel();
        for (Map.Entry<UUID, Set<UUID>> e : applied.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer == null) {
                continue;
            }
            for (UUID id : e.getValue()) {
                Player target = Bukkit.getPlayer(id);
                if (target != null) {
                    viewer.showPlayer(plugin, target);
                }
            }
        }
        applied.clear();
        vanished.clear();
        zones.clear();
        tracker.clear();
        Visibility.use(null);
    }

    // ------------------------------------------------------------------ vanish

    @Override
    public void setVanished(Player player, boolean vanish) {
        boolean changed = vanish ? vanished.add(player.getUniqueId()) : vanished.remove(player.getUniqueId());
        if (changed) {
            refreshAsTarget(player, false);
        }
    }

    @Override
    public boolean isVanished(UUID player) {
        return vanished.contains(player);
    }

    @Override
    public boolean knows(Player viewer, Player target) {
        return viewer.equals(target) || !vanished.contains(target.getUniqueId()) || viewer.hasPermission(SEE_VANISHED);
    }

    @Override
    public boolean hidden(Player viewer, Player target) {
        Set<UUID> set = applied.get(viewer.getUniqueId());
        return set != null && set.contains(target.getUniqueId());
    }

    // ------------------------------------------------------------------ rules and relations

    @Override
    public void addRule(String owner, String id, Rule rule) {
        rules.removeIf(r -> r.owner().equals(owner) && r.id().equals(id));
        rules.add(new Owned<>(owner, id, rule));
    }

    @Override
    public void addRelation(String owner, BiPredicate<Player, Player> related) {
        relations.add(new Owned<>(owner, "relation", related));
    }

    @Override
    public void removeAll(String owner) {
        boolean removed = rules.removeIf(r -> r.owner().equals(owner)) | relations.removeIf(r -> r.owner().equals(owner));
        if (removed) {
            refreshAll();
        }
    }

    // ------------------------------------------------------------------ the decision

    @Override
    public ShowMode mode(Player player) {
        PlayerSettings s = settings;
        ShowMode fallback = config.get().defaultMode;
        return s == null ? fallback : ShowMode.byId(s.choice(player, SETTING)).orElse(fallback);
    }

    private VisibilityRules.Viewer viewerFacts(Player p) {
        return new VisibilityRules.Viewer(p.hasPermission(SEE_VANISHED), mode(p), zone(p).inSettingZone(), p.getWorld().getName());
    }

    private VisibilityRules.Target targetFacts(Player p) {
        boolean staff = config.get().staffAlwaysShown && p.hasPermission(ALWAYS_SHOWN);
        return new VisibilityRules.Target(vanished.contains(p.getUniqueId()), staff, zone(p).inSettingZone(), p.getWorld().getName());
    }

    private boolean decide(Player viewer, VisibilityRules.Viewer v, Player target, VisibilityRules.Target t) {
        return VisibilityRules.hidden(v, t, () -> ruleHides(viewer, target), () -> related(viewer, target));
    }

    private boolean ruleHides(Player viewer, Player target) {
        for (Owned<Rule> r : rules) {
            try {
                if (r.value().hides(viewer, target)) {
                    return true;
                }
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Visibility rule '" + r.owner() + ":" + r.id() + "' failed", e);
            }
        }
        return false;
    }

    private boolean related(Player viewer, Player target) {
        for (Owned<BiPredicate<Player, Player>> r : relations) {
            try {
                if (r.value().test(viewer, target)) {
                    return true;
                }
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Visibility relation of '" + r.owner() + "' failed", e);
            }
        }
        return false;
    }

    /** Same island (any stored role), or one is a coop player of the other's island. */
    private boolean sameIsland(Player viewer, Player target) {
        IslandService islands = services.optional(IslandService.class).orElse(null);
        if (islands == null) {
            return false;
        }
        Optional<IslandService.IslandInfo> a = islands.islandOf(viewer.getUniqueId());
        Optional<IslandService.IslandInfo> b = islands.islandOf(target.getUniqueId());
        if (a.isPresent() && b.isPresent() && a.get().id().equals(b.get().id())) {
            return true;
        }
        ProfileService profiles = ProfileService.of(services);
        return (a.isPresent() && islands.isCoop(a.get().id(), profiles.profileKey(target)))
                || (b.isPresent() && islands.isCoop(b.get().id(), profiles.profileKey(viewer)));
    }

    // ------------------------------------------------------------------ applying

    @Override
    public void refresh(Player player) {
        refreshAsViewer(player, false);
        refreshAsTarget(player, false);
    }

    @Override
    public void refreshAll() {
        zones.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshAsViewer(p, false);
        }
    }

    /** Who {@code viewer} sees. */
    private void refreshAsViewer(Player viewer, boolean force) {
        if (!viewer.isOnline()) {
            return;
        }
        VisibilityRules.Viewer v = viewerFacts(viewer);
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        Map<UUID, Boolean> wanted = new HashMap<>(online.size() * 2);
        Map<UUID, Player> byId = new HashMap<>(online.size() * 2);
        for (Player target : online) {
            if (target.equals(viewer)) {
                continue;
            }
            wanted.put(target.getUniqueId(), decide(viewer, v, target, targetFacts(target)));
            byId.put(target.getUniqueId(), target);
        }
        apply(viewer, wanted, byId, force);
    }

    /** Who sees {@code target}. */
    private void refreshAsTarget(Player target, boolean force) {
        if (!target.isOnline()) {
            return;
        }
        VisibilityRules.Target t = targetFacts(target);
        Map<UUID, Player> byId = Map.of(target.getUniqueId(), target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            apply(viewer, Map.of(target.getUniqueId(), decide(viewer, viewerFacts(viewer), target, t)), byId, force);
        }
    }

    private void apply(Player viewer, Map<UUID, Boolean> wanted, Map<UUID, Player> byId, boolean force) {
        Set<UUID> set = applied.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
        VisibilityRules.Changes changes = VisibilityRules.diff(set, wanted, force);
        for (UUID id : changes.hide()) {
            viewer.hidePlayer(plugin, byId.get(id));
            set.add(id);
        }
        for (UUID id : changes.show()) {
            viewer.showPlayer(plugin, byId.get(id));
            set.remove(id);
        }
        if (set.isEmpty()) {
            applied.remove(viewer.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ places where the choice applies

    private Zone zone(Player p) {
        Zone z = zones.get(p.getUniqueId());
        if (z == null || !z.world().equals(p.getWorld().getName())) {
            z = computeZone(p, settingRegions());
            zones.put(p.getUniqueId(), z);
            tracker.forget(p.getUniqueId()); // computed outside the position check: the next check computes it again
        }
        return z;
    }

    /** The configured regions (looked up again only when the builds or the ids changed, PERF11). */
    private List<BuildRegion> settingRegions() {
        return tracker.regions(services.optional(BuildPoints.class).orElse(null), config.get().settingRegions);
    }

    private Zone computeZone(Player p, List<BuildRegion> regions) {
        Location at = p.getLocation(scratch);
        String world = at.getWorld() == null ? "" : at.getWorld().getName();
        at.setWorld(null); // no reference to a world kept between checks
        return new Zone(world, ZoneTracker.inSettingZone(world, at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                config.get().settingWorlds, regions));
    }

    /**
     * Every {@code region-check-ticks}: only players on another block or in another world than at their last check (or
     * when the places changed) get a box test; only a changed zone re-applies (PERF11: no Location or zone allocated for
     * a player who did not move, no region lookup while the builds stay the same).
     */
    private void checkZones() {
        List<BuildRegion> regions = settingRegions();
        List<String> worlds = config.get().settingWorlds;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Location at = p.getLocation(scratch);
            String world = at.getWorld() == null ? "" : at.getWorld().getName();
            at.setWorld(null);
            int x = at.getBlockX();
            int y = at.getBlockY();
            int z = at.getBlockZ();
            Zone before = zones.get(id);
            if (!tracker.moved(id, world, x, y, z, regions, worlds) && before != null) {
                continue; // same block, world and places as at the last check: same zone
            }
            boolean inside = ZoneTracker.inSettingZone(world, x, y, z, worlds, regions);
            if (before != null && before.inSettingZone() == inside && before.world().equals(world)) {
                continue;
            }
            zones.put(id, new Zone(world, inside));
            if (before != null) {
                refresh(p);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        zones.remove(p.getUniqueId());
        // Forced: Bukkit may still hold a hide of ours from the joiner's last visit, so every pair is applied again.
        refreshAsViewer(p, true);
        refreshAsTarget(p, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        applied.remove(id); // a new Player object (with no hides) is made on rejoin
        zones.remove(id);
        tracker.forget(id);
        vanished.remove(id); // the essentials module vanishes them again on rejoin when their setting says so
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        zones.remove(event.getPlayer().getUniqueId());
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        refresh(event.getPlayer()); // the choice and the island are per profile
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandJoined(IslandJoinedEvent event) {
        refresh(event.getPlayer());
    }
}
