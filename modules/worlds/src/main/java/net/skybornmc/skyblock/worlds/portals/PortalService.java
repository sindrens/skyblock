package net.skybornmc.skyblock.worlds.portals;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.combat.CombatTagService;
import net.skybornmc.skyblock.common.island.IslandPortalEnteredEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.worlds.WorldsConfig;
import net.skybornmc.skyblock.worlds.WorldsModule;
import net.skybornmc.skyblock.worlds.builds.BuildPointsStore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Build portals (PORTALS): players who step into a portal trigger of an applied build ({@code build-points.yml},
 * category {@code portals}) are sent to its target ({@link PortalResolver}). The island portal creates the player's
 * island on first use and always takes them there (owner answer 159), then fires {@link IslandPortalEnteredEvent}.
 *
 * <p>Cost: one hash lookup per block-changing move in a world with portals (chunk index). Rules: instant, except a
 * combat-tagged player (or any player in the warzone, owner principle 1) waits {@code portals.combat-delay-seconds}
 * inside the portal; frozen players can't use portals;
 * after a teleport portals ignore the player for {@code portals.grace-millis}; the warzone is reachable only through its
 * portal (a {@link TeleportService} guard).</p>
 */
public final class PortalService implements Listener {

    private static final String MODULE = "worlds";

    private final Core core;
    private final WorldsModule worlds;
    private final WorldsConfig config;
    private final BuildPointsStore store;

    private PortalSettings settings = PortalSettings.DEFAULTS;
    private Map<String, BuildPointsStore.Applied> indexedFrom;
    private PortalIndex index = PortalIndex.EMPTY;
    private List<Puff> puffs = List.of();

    /** Portals ignore a player until this time (grace after a teleport, refusal cooldown, island wait). */
    private final Map<UUID, Long> quietUntil = new HashMap<>();
    /** Combat delay running per player. */
    private final Map<UUID, Scheduler.Task> pending = new HashMap<>();
    /** Players whose warzone teleport is the portal's own (allowed by the warzone guard). */
    private final Set<UUID> warzonePass = new HashSet<>();

    private Scheduler.Task particleTask;
    private TeleportService.Guard guard;

    /** One particle puff of a portal, resolved once per index rebuild. */
    private record Puff(String world, double x, double y, double z, double dx, double dy, double dz, Particle particle, Object data) {
    }

    public PortalService(Core core, WorldsModule worlds, WorldsConfig config, BuildPointsStore store) {
        this.core = core;
        this.worlds = worlds;
        this.config = config;
        this.store = store;
    }

    public void enable() {
        reload();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        guard = (player, destination) -> {
            World to = destination.getWorld();
            boolean denied = PortalResolver.deniesWarzone(config.warzonePortalOnly, config.warzoneWorld,
                    to == null ? null : to.getName(), player.getWorld().getName(), warzonePass.contains(player.getUniqueId()));
            return denied ? Optional.ofNullable(core.messages().raw(MODULE, "portals.warzone-only")) : Optional.empty();
        };
        core.services().optional(TeleportService.class).ifPresent(t -> t.addGuard(guard));
    }

    /** Re-reads the settings and rebuilds the index and particles. */
    public void reload() {
        settings = PortalSettings.of(config.islandPortal, config.portalsForceOpen, config.portalsDisabled, config.portalTargetOverrides,
                config.portalWorldTargets, config.portalsNeedBuild);
        indexedFrom = null;
        index();
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (config.portalParticleTicks > 0) {
            particleTask = core.scheduler().repeat("portal-particles", config.portalParticleTicks, config.portalParticleTicks, this::puff);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (guard != null) {
            core.services().optional(TeleportService.class).ifPresent(t -> t.removeGuard(guard));
            guard = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        pending.values().forEach(Scheduler.Task::cancel);
        pending.clear();
        quietUntil.clear();
        warzonePass.clear();
    }

    /** The current portals; rebuilt when {@code /skyblock builds apply} changed build-points.yml. */
    public PortalIndex index() {
        Map<String, BuildPointsStore.Applied> now = store.all();
        if (now != indexedFrom) {
            index = new PortalIndex(Portals.from(store));
            puffs = puffs(index.all());
            indexedFrom = now;
        }
        return index;
    }

    public PortalSettings settings() {
        return settings;
    }

    /** One line per portal of a build being applied: {@code to_depths → depths, to_events (closed)}. */
    public String describe(List<BuildRegion> regions) {
        StringJoiner out = new StringJoiner(", ");
        for (BuildRegion r : regions) {
            Portal p = Portals.of(r, List.of());
            PortalResolver.Destination d = PortalResolver.resolve(p, index().all(), settings, live());
            String text = switch (d) {
                case PortalResolver.Island ignored -> p.id() + " → island";
                case PortalResolver.Teleport t -> p.id() + " → " + t.target();
                case PortalResolver.Refused f -> p.id() + " (" + f.reason().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + (f.target().isEmpty() ? "" : ": " + f.target()) + ")";
            };
            out.add(text);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !config.portalsEnabled) {
            return;
        }
        Location to = event.getTo();
        World world = to.getWorld();
        PortalIndex portals = index();
        if (world == null || !portals.hasWorld(world.getName())) {
            return;
        }
        Optional<Portal> portal = portals.at(world.getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (portal.isPresent()) {
            enter(event.getPlayer(), portal.get(), event.getFrom(), to);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        quietUntil.remove(id);
        warzonePass.remove(id);
        Scheduler.Task task = pending.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    // ------------------------------------------------------------------ use

    private void enter(Player player, Portal portal, Location from, Location to) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long quiet = quietUntil.get(id);
        if ((quiet != null && now < quiet) || pending.containsKey(id)) {
            return;
        }
        if (frozen(player)) {
            refuse(player, "portals.frozen", "", from, to);
            return;
        }
        PortalResolver.Destination destination = PortalResolver.resolve(portal, index().all(), settings, live());
        if (destination instanceof PortalResolver.Refused refused) {
            refuse(player, refusalKey(refused.reason()), refused.target(), from, to);
            return;
        }
        int delay = config.portalCombatDelaySeconds;
        CombatTagService combat = core.services().optional(CombatTagService.class).orElse(CombatTagService.NONE);
        boolean inWarzone = player.getWorld().getName().equalsIgnoreCase(config.warzoneWorld); // owner principle 1
        if (delay > 0 && (combat.inCombat(id) || inWarzone) && !TeleportReason.PORTAL.bypass(player)) {
            core.messages().actionBar(player, MODULE, "portals.combat-wait", Msg.number("seconds", delay));
            core.sounds().play(player, "warning");
            pending.put(id, core.scheduler().later("portal-combat", delay * 20L, () -> afterCombatDelay(player, portal)));
            return;
        }
        use(player, portal, destination);
    }

    private void afterCombatDelay(Player player, Portal portal) {
        pending.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        Location at = player.getLocation();
        boolean inside = at.getWorld() != null && at.getWorld().getName().equals(portal.world())
                && at.getBlockX() >= portal.box().minX() - 1 && at.getBlockX() <= portal.box().maxX() + 1
                && at.getBlockY() >= portal.box().minY() - 1 && at.getBlockY() <= portal.box().maxY() + 1
                && at.getBlockZ() >= portal.box().minZ() - 1 && at.getBlockZ() <= portal.box().maxZ() + 1;
        if (!inside) {
            core.messages().send(player, MODULE, "portals.combat-left");
            return;
        }
        if (frozen(player)) {
            refuse(player, "portals.frozen", "", null, null);
            return;
        }
        PortalResolver.Destination destination = PortalResolver.resolve(portal, index().all(), settings, live());
        if (destination instanceof PortalResolver.Refused refused) {
            refuse(player, refusalKey(refused.reason()), refused.target(), null, null);
            return;
        }
        use(player, portal, destination);
    }

    private void use(Player player, Portal portal, PortalResolver.Destination destination) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        switch (destination) {
            case PortalResolver.Teleport t -> {
                World world = Bukkit.getWorld(t.world());
                if (world == null) {
                    refuse(player, "portals.no-world", t.target(), null, null);
                    return;
                }
                quietUntil.put(id, now + config.portalGraceMillis);
                Location target = new Location(world, t.pos().x(), t.pos().y(), t.pos().z(), t.pos().yaw(), t.pos().pitch());
                TeleportService teleports = core.services().get(TeleportService.class);
                TeleportOptions options = TeleportOptions.builder()
                        .destination(core.messages().component(player, MODULE, "portals.destination", Msg.p("target", t.target())))
                        .cause(PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .quiet()
                        .onArrive(() -> arrived(player, t.target()))
                        .build();
                warzonePass.add(id);
                try {
                    teleports.now(player, target, TeleportReason.PORTAL, options);
                } finally {
                    warzonePass.remove(id);
                }
            }
            case PortalResolver.Island ignored -> {
                IslandService islands = core.services().optional(IslandService.class).orElse(null);
                if (islands == null) {
                    refuse(player, "portals.no-islands", PortalSettings.ISLAND, null, null);
                    return;
                }
                quietUntil.put(id, now + config.portalIslandWaitMillis);
                long started = now;
                core.sounds().play(player, config.portalSound);
                IslandService.PortalEntry entry = islands.enterFromPortal(player, firstTime -> {
                    if (!player.isOnline() || System.currentTimeMillis() - started > config.portalIslandWaitMillis * 4L) {
                        return; // a late arrival from an old request: not this portal use
                    }
                    quietUntil.put(id, System.currentTimeMillis() + config.portalGraceMillis);
                    Bukkit.getPluginManager().callEvent(new IslandPortalEnteredEvent(player, firstTime, portal.id()));
                });
                if (entry == IslandService.PortalEntry.BUSY || entry == IslandService.PortalEntry.REFUSED) {
                    quietUntil.put(id, System.currentTimeMillis() + config.portalRefusalMillis); // the islands module told them
                }
            }
            case PortalResolver.Refused refused -> refuse(player, refusalKey(refused.reason()), refused.target(), null, null);
        }
    }

    private void arrived(Player player, String target) {
        quietUntil.put(player.getUniqueId(), System.currentTimeMillis() + config.portalGraceMillis);
        String key = core.messages().has(MODULE, "portals.title." + target + ".title") ? "portals.title." + target : "portals.title.default";
        core.messages().title(player, MODULE, key, Msg.p("target", target));
        core.sounds().play(player, config.portalSound);
    }

    /** Tells the player why (at most once per refusal cooldown) and pushes them back out of the portal. */
    private void refuse(Player player, String key, String target, Location from, Location to) {
        quietUntil.put(player.getUniqueId(), System.currentTimeMillis() + config.portalRefusalMillis);
        core.messages().send(player, MODULE, key, Msg.p("target", target));
        core.sounds().play(player, "error");
        if (from != null && to != null && config.portalPushBack > 0) {
            Vector back = from.toVector().subtract(to.toVector()).setY(0);
            if (back.lengthSquared() < 1e-6) {
                back = player.getLocation().getDirection().multiply(-1).setY(0);
            }
            if (back.lengthSquared() > 1e-6) {
                player.setVelocity(back.normalize().multiply(config.portalPushBack).setY(0.25));
            }
        }
    }

    static String refusalKey(PortalResolver.Reason reason) {
        return switch (reason) {
            case CLOSED -> "portals.closed";
            case NO_WORLD -> "portals.no-world";
            case NOT_BUILT -> "portals.not-built";
            case UNKNOWN -> "portals.unknown";
        };
    }

    /** Frozen by staff (the staff module publishes the {@code frozen} placeholder; same check as teleports). */
    private boolean frozen(Player player) {
        return core.placeholders().has("frozen")
                && core.placeholders().text(player, "frozen").map("true"::equalsIgnoreCase).orElse(false);
    }

    // ------------------------------------------------------------------ live world view

    private PortalResolver.World live() {
        return new PortalResolver.World() {
            @Override
            public boolean loaded(String world) {
                return Bukkit.getWorld(world) != null;
            }

            @Override
            public Optional<Portal.Pos> spawn(String world) {
                return worlds.spawnOf(world).map(l -> new Portal.Pos(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
            }

            @Override
            public boolean hasBuild(String world) {
                return store.all().values().stream().anyMatch(a -> a.world().equalsIgnoreCase(world));
            }

            @Override
            public Optional<PortalResolver.Located> point(String category, String id) {
                Optional<BuildPoint> p = store.point(category, id);
                return p.map(b -> {
                    boolean block = b.x() == Math.rint(b.x()) && b.z() == Math.rint(b.z());
                    double x = block ? b.x() + 0.5 : b.x();
                    double z = block ? b.z() + 0.5 : b.z();
                    return new PortalResolver.Located(b.world(), new Portal.Pos(x, b.y(), z, b.yaw(), b.pitch()));
                });
            }
        };
    }

    // ------------------------------------------------------------------ particles

    private List<Puff> puffs(List<Portal> portals) {
        List<Puff> out = new ArrayList<>();
        for (Portal p : portals) {
            Portal.Particle spec = p.particle();
            if (spec == null || !spec.enabled() || PortalSettings.names(settings.disabled(), p)) {
                continue;
            }
            NamespacedKey key = NamespacedKey.fromString(spec.type());
            Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
            if (particle == null) {
                core.plugin().getLogger().warning("Portals: " + p.key() + " has an unknown particle '" + spec.type() + "'; no particles.");
                continue;
            }
            Object data = null;
            if (particle.getDataType() == Particle.DustOptions.class) {
                data = new Particle.DustOptions(color(spec.color()), 1.2f);
            } else if (particle.getDataType() != Void.class) {
                core.plugin().getLogger().warning("Portals: particle '" + spec.type() + "' of " + p.key() + " needs data; no particles.");
                continue;
            }
            boolean block = spec.x() == Math.rint(spec.x()) && spec.z() == Math.rint(spec.z());
            double off = block ? 0.5 : 0;
            out.add(new Puff(p.world(), spec.x() + off, spec.y() + 0.5, spec.z() + off,
                    (p.box().maxX() - p.box().minX() + 1) / 3.0, (p.box().maxY() - p.box().minY() + 1) / 3.0,
                    (p.box().maxZ() - p.box().minZ() + 1) / 3.0, particle, data));
        }
        return List.copyOf(out);
    }

    private void puff() {
        if (!config.portalsEnabled) {
            return;
        }
        index(); // picks up a new apply
        for (Puff p : puffs) {
            World world = Bukkit.getWorld(p.world());
            if (world == null || world.getPlayers().isEmpty() || !world.isChunkLoaded((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4)) {
                continue;
            }
            world.spawnParticle(p.particle(), p.x(), p.y(), p.z(), config.portalParticleCount, p.dx(), p.dy(), p.dz(), 0, p.data());
        }
    }

    static Color color(String hex) {
        if (hex != null) {
            String h = hex.trim().replace("#", "");
            try {
                return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
            } catch (NumberFormatException ignored) {
                // fall through to white
            }
        }
        return Color.WHITE;
    }
}
