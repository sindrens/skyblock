package net.skybornmc.skyblock.common.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.combat.CombatTagService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * The core's {@link TeleportService}: one warmup implementation for every module. State per warmup lives in a pure
 * {@link WarmupState}; this class feeds it Bukkit events (move, damage, world change, quit) and a once-per-second tick
 * (freeze, combat tag, position after vehicles/teleports), shows the countdown on the action bar with a click sound
 * and teleports after an async chunk preload. Texts: {@code messages/core.yml → teleport.*}; settings
 * {@code teleport.yml}.
 */
public final class CoreTeleportService implements TeleportService, Listener {

    /** Staff keep their {@code /back} location across logouts (node owned by the essentials module). */
    static final String BACK_PERMISSION = "skyblock.staff.back";
    private static final String MODULE = "core";

    private record Active(WarmupState state, Location destination, TeleportReason reason, TeleportOptions options,
                          Scheduler.Task task) {
    }

    private final Core core;
    private final TeleportConfig config;
    private final List<Guard> guards = new CopyOnWriteArrayList<>();
    private final List<DangerZone> dangerZones = new CopyOnWriteArrayList<>();
    /** Main thread writes; {@link #hasWarmup} may read from anywhere. */
    private final Map<UUID, Active> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();
    private final TeleportCooldowns cooldowns = new TeleportCooldowns(System::currentTimeMillis);

    public CoreTeleportService(Core core, TeleportConfig config) {
        this.core = core;
        this.config = config;
    }

    // ------------------------------------------------------------------ guards

    @Override
    public void addGuard(Guard guard) {
        guards.add(guard);
    }

    @Override
    public void removeGuard(Guard guard) {
        guards.remove(guard);
    }

    @Override
    public Optional<String> denied(Player player, Location destination) {
        for (Guard guard : guards) {
            Optional<String> reason = guard.deny(player, destination);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ danger (combat tag, combat zones)

    @Override
    public void addDangerZone(DangerZone zone) {
        dangerZones.add(zone);
    }

    @Override
    public void removeDangerZone(DangerZone zone) {
        dangerZones.remove(zone);
    }

    @Override
    public boolean inDanger(Player player) {
        if (config.dangerWhenTagged && combat().remainingMillis(player.getUniqueId()) > 0) {
            return true;
        }
        if (config.dangerWorlds.contains(player.getWorld().getName())) {
            return true;
        }
        for (DangerZone zone : dangerZones) {
            try {
                if (zone.contains(player)) {
                    return true;
                }
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Teleport: a combat zone check failed", e);
            }
        }
        return false;
    }

    /** Owner answer 413: worlds like the warzone are entered through their portal only (staff bypass skips it). */
    private boolean refusedPortalOnly(Player player, Location destination, TeleportReason reason) {
        World target = destination.getWorld();
        if (target == null || reason.bypass(player) || !config.portalOnlyWorlds.contains(target.getName())
                || target.equals(player.getWorld())) {
            return false;
        }
        refuse(player, "teleport.portal-only", Msg.p("world", target.getName()));
        return true;
    }

    // ------------------------------------------------------------------ requests

    @Override
    public boolean request(Player player, Location destination, TeleportReason reason, TeleportOptions options) {
        if ((options.guards() && refusedPortalOnly(player, destination, reason)) || refusedByGuardOrCheck(player, destination, options)) {
            return false;
        }
        UUID id = player.getUniqueId();
        boolean bypass = reason.bypass(player);
        int base = config.warmupSeconds(reason);
        // Owner answers 285/341/345: instant, but 3 s while combat-tagged or in a combat zone (never blocked there).
        boolean danger = reason != TeleportReason.PROFILE && reason != TeleportReason.STAFF && inDanger(player);
        int seconds = TeleportRules.warmup(base, config.dangerWarmupSeconds, danger);
        long cooldown = cooldowns.left(id, reason);
        CombatTagService combat = combat();
        long combatLeft = combat.remainingMillis(id);
        TeleportRules rules = config.rules();
        switch (rules.start(seconds, bypass, cooldown, frozen(player), combatLeft)) {
            case FROZEN -> {
                return refuse(player, "teleport.frozen");
            }
            case COOLDOWN -> {
                return refuse(player, "teleport.cooldown", Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, cooldown)))));
            }
            case IN_COMBAT -> {
                return refuse(player, "teleport.in-combat", Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000, combatLeft)))));
            }
            case INSTANT -> {
                cancel(id);
                if (!bypass) {
                    cooldowns.start(id, reason, config.cooldownSeconds(reason));
                }
                deliver(player, destination, options, false);
                return true;
            }
            case WARMUP -> {
                cancel(id);
                cooldowns.start(id, reason, config.cooldownSeconds(reason));
                startWarmup(player, destination, reason, options, seconds, danger && seconds > base);
                return true;
            }
        }
        return false;
    }

    @Override
    public void now(Player player, Location destination, TeleportReason reason, TeleportOptions options) {
        cancel(player.getUniqueId());
        if (options.guards() && refusedPortalOnly(player, destination, reason)) {
            return;
        }
        if (options.guards() && !reason.bypass(player)) {
            Optional<String> denial = denied(player, destination);
            if (denial.isPresent()) {
                player.sendMessage(core.messages().parse(player, denial.get()));
                core.sounds().play(player, "error");
                return;
            }
        }
        deliver(player, destination, options, false);
    }

    private boolean refusedByGuardOrCheck(Player player, Location destination, TeleportOptions options) {
        if (options.guards()) {
            Optional<String> denial = denied(player, destination);
            if (denial.isPresent()) {
                player.sendMessage(core.messages().parse(player, denial.get()));
                core.sounds().play(player, "error");
                return true;
            }
        }
        Optional<Component> refusal = options.check(player);
        if (refusal.isPresent()) {
            player.sendMessage(refusal.get());
            core.sounds().play(player, "error");
            return true;
        }
        return false;
    }

    private boolean refuse(Player player, String key, TagResolver... placeholders) {
        core.messages().send(player, MODULE, key, placeholders);
        core.sounds().play(player, "error");
        return false;
    }

    // ------------------------------------------------------------------ warmup

    private void startWarmup(Player player, Location destination, TeleportReason reason, TeleportOptions options, int seconds,
                             boolean becauseOfDanger) {
        UUID id = player.getUniqueId();
        WarmupState state = new WarmupState(pos(player.getLocation()), System.currentTimeMillis(), seconds);
        core.messages().send(player, MODULE, becauseOfDanger ? "teleport.warmup-danger" : "teleport.warmup", Msg.p("seconds", seconds), Msg.c("destination", options.destination()));
        Scheduler.Task task = core.scheduler().repeat("teleport-warmup", 0L, 20L, () -> tick(id));
        warmups.put(id, new Active(state, destination.clone(), reason, options, task));
    }

    private void tick(UUID id) {
        Active active = warmups.get(id);
        Player player = core.plugin().getServer().getPlayer(id);
        if (active == null || player == null || !player.isOnline()) {
            cancel(id);
            return;
        }
        WarmupState.Step step = active.state().tick(pos(player.getLocation()), frozen(player), combat().lastCombat(id), config.rules());
        switch (step) {
            case WarmupState.Countdown countdown -> {
                core.messages().actionBar(player, MODULE, "teleport.countdown", Msg.p("seconds", countdown.seconds()),
                        Msg.c("destination", active.options().destination()));
                core.sounds().play(player, "click");
            }
            case WarmupState.Done done -> {
                stop(id, active);
                if (!refusedByGuardOrCheck(player, active.destination(), active.options())) {
                    deliver(player, active.destination(), active.options(), true);
                } else {
                    cooldowns.clear(id, active.reason());
                }
            }
            case WarmupState.Cancelled cancelled -> cancelWith(player, active, cancelled.reason());
        }
    }

    private void stop(UUID id, Active active) {
        warmups.remove(id, active);
        active.task().cancel();
    }

    @Override
    public boolean cancel(UUID player) {
        Active active = warmups.remove(player);
        if (active == null) {
            return false;
        }
        active.task().cancel();
        return true;
    }

    /** Cancels with the reason's message (and a [Try again] click when the caller gave a command). */
    private void cancelWith(Player player, Active active, WarmupState.Cancel why) {
        stop(player.getUniqueId(), active);
        // A cancelled warmup doesn't use up the cooldown: trying again right away is fine.
        cooldowns.clear(player.getUniqueId(), active.reason());
        String command = active.options().retryCommand();
        Component retry = command == null ? Component.empty()
                : core.messages().component(player, MODULE, "teleport.retry", Msg.p("command", command));
        core.messages().send(player, MODULE, "teleport.cancelled." + why.name().toLowerCase(Locale.ROOT), Msg.c("retry", retry),
                Msg.c("destination", active.options().destination()));
        core.sounds().play(player, "error");
    }

    // ------------------------------------------------------------------ the teleport itself

    /** Loads the destination chunk async, runs the safe-destination hook, teleports and reports the result. */
    private void deliver(Player player, Location destination, TeleportOptions options, boolean afterWarmup) {
        World world = destination.getWorld();
        if (world == null) {
            failed(player, options);
            return;
        }
        Location target = destination.clone();
        world.getChunkAtAsync(target).whenComplete((chunk, error) -> core.scheduler().ensureMain("teleport-arrive", () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Teleport: could not load the chunk at " + target, error);
                failed(player, options);
                return;
            }
            Optional<Location> safe = options.safe(target);
            if (safe.isEmpty()) {
                if (!options.quiet()) {
                    core.messages().send(player, MODULE, "teleport.unsafe", Msg.c("destination", options.destination()));
                    core.sounds().play(player, "error");
                }
                return;
            }
            player.setFallDistance(0);
            player.teleportAsync(safe.get(), options.cause()).whenComplete((ok, e) -> core.scheduler().ensureMain("teleport-arrived", () -> {
                if (Boolean.TRUE.equals(ok) && e == null) {
                    if (!options.quiet()) {
                        core.sounds().play(player, "teleport");
                    }
                    if (options.onArrive() != null && player.isOnline()) {
                        options.onArrive().run();
                    }
                } else if (player.isOnline()) {
                    failed(player, options);
                }
            }));
        }));
    }

    private void failed(Player player, TeleportOptions options) {
        if (options.quiet()) {
            return;
        }
        core.messages().send(player, MODULE, "teleport.failed", Msg.c("destination", options.destination()));
        core.sounds().play(player, "error");
    }

    // ------------------------------------------------------------------ queries

    @Override
    public boolean hasWarmup(UUID player) {
        return warmups.containsKey(player);
    }

    @Override
    public Duration cooldownLeft(UUID player, TeleportReason reason) {
        return Duration.ofMillis(cooldowns.left(player, reason));
    }

    @Override
    public int warmupSeconds(TeleportReason reason) {
        return config.warmupSeconds(reason);
    }

    @Override
    public Optional<Location> back(UUID player) {
        return Optional.ofNullable(back.get(player)).map(Location::clone);
    }

    /** Plugin disable: stops every warmup. */
    public void shutdown() {
        warmups.values().forEach(a -> a.task().cancel());
        warmups.clear();
    }

    private CombatTagService combat() {
        return core.services().optional(CombatTagService.class).orElse(CombatTagService.NONE);
    }

    /** Frozen by staff (the staff module publishes the {@code frozen} placeholder). */
    private boolean frozen(Player player) {
        return core.placeholders().has("frozen")
                && core.placeholders().text(player, "frozen").map("true"::equalsIgnoreCase).orElse(false);
    }

    private static WarmupState.Pos pos(Location location) {
        World world = location.getWorld();
        return new WarmupState.Pos(world == null ? "" : world.getName(), location.getX(), location.getY(), location.getZ());
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (warmups.isEmpty() || !event.hasChangedPosition()) {
            return;
        }
        Active active = warmups.get(event.getPlayer().getUniqueId());
        if (active != null) {
            active.state().moved(pos(event.getTo()), config.rules()).ifPresent(why -> cancelWith(event.getPlayer(), active, why));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (warmups.isEmpty() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Active active = warmups.get(player.getUniqueId());
        if (active != null) {
            active.state().damaged(config.rules()).ifPresent(why -> cancelWith(player, active, why));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Active active = warmups.get(event.getPlayer().getUniqueId());
        if (active != null) {
            cancelWith(event.getPlayer(), active, WarmupState.Cancel.WORLD);
        }
    }

    /** Remembers where staff and plugin teleports started, for {@code /back}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            back.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (config.backRememberDeath) {
            back.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancel(id);
        cooldowns.prune(id);
        if (!event.getPlayer().hasPermission(BACK_PERMISSION)) {
            back.remove(id);
        }
    }
}
