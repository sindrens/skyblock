package net.skybornmc.skyblock.staff;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code /freeze <player>}: the player can't change position (looking around is fine), build, interact, use items or
 * inventories, take or deal damage, get hungry, ride, glide, swim away, be pushed or teleport themselves, and can't use
 * commands except {@code freeze.allowed-commands} (none by default); chat goes to staff only. Title + chat
 * instructions repeat.
 *
 * <p>The freeze is stored in {@code staff_frozen} and lasts until a staff member unfreezes the player: across logout
 * and server restarts (re-applied at the very start of the join, then checked again a tick later). Logging out while
 * frozen also runs the configured action. Staff are alerted; everything is audited.</p>
 *
 * <p><b>How movement is stopped:</b> every move is reset to the freeze spot (keeping the new yaw/pitch). Paper turns a
 * changed move destination into a {@code PLUGIN} teleport, so the teleport listener must let teleports to the freeze
 * spot through; refusing every non-{@code UNKNOWN} teleport (the old code) cancelled our own reset and let frozen
 * players walk away. A watchdog task also snaps players back (pistons, vehicles, anything that moves them without a
 * move event). Slowness and the jump block are only visual hints.</p>
 */
final class FreezeService extends StaffFeature implements Listener {

    /** Every frozen player (online or not), mirrored from {@code staff_frozen}. */
    private final Map<UUID, StaffStore.Frozen> frozen = new ConcurrentHashMap<>();
    /** Online frozen players → the spot they are held at. Main thread only. */
    private final Map<UUID, Location> spots = new HashMap<>();
    /** Recently unfrozen players → end of the fall-damage grace (epoch millis). */
    private final Map<UUID, Long> fallGrace = new ConcurrentHashMap<>();
    private final SerialWrites<UUID> writes = new SerialWrites<>();
    private final NamespacedKey jumpKey;
    private Scheduler.Task reminder;
    private Scheduler.Task watchdog;

    FreezeService(Core core, StaffConfig config, ModuleMessages msg, StaffStore store) {
        super(core, config, msg, store);
        this.jumpKey = new NamespacedKey(core.plugin(), "freeze_jump");
    }

    void register() {
        Arg<Player> target = Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("freeze")
                .description("Freeze or unfreeze a player (for checks)").category("Staff")
                .permission(StaffPerms.FREEZE).arg(target).aliases("ss").example("freeze Steve")
                .executes(ctx -> toggle(ctx.sender(), ctx.get(target))));
        // Unfreeze works for offline players too (the freeze is stored), so it completes every frozen name.
        Arg<StaffStore.Frozen> unfreezeTarget = Arg.word("player", (sender, raw) -> byName(raw)
                .orElseThrow(() -> fail("freeze.not-frozen", Msg.p("player", raw))),
                Suggest.of(() -> frozen.values().stream().map(StaffStore.Frozen::name).sorted(String.CASE_INSENSITIVE_ORDER).toList()));
        core.commands().register(CommandSpec.command("unfreeze")
                .description("Unfreeze a player (online or offline)").category("Staff")
                .permission(StaffPerms.FREEZE).arg(unfreezeTarget).example("unfreeze Steve")
                .executes(ctx -> unfreeze(ctx.sender(), ctx.get(unfreezeTarget))));
    }

    /** Loads the stored freezes (module start, before anyone can join) and re-applies them to online players. */
    void load() {
        try {
            for (StaffStore.Frozen f : store.frozenPlayers().get(10, TimeUnit.SECONDS)) {
                frozen.put(f.uuid(), f);
            }
        } catch (Exception e) {
            core.plugin().getLogger().severe("Could not load frozen players (they are not frozen until this works): " + e.getMessage());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            StaffStore.Frozen f = frozen.get(p.getUniqueId());
            if (f != null) {
                hold(p);
                remind(p);
            }
        }
    }

    void start() {
        reminder = core.scheduler().repeat("staff:freeze-reminder", 40L, config.freezeReminderSeconds * 20L, () -> {
            for (UUID id : List.copyOf(spots.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    remind(p);
                    hints(p);
                }
            }
        });
        watchdog = core.scheduler().repeat("staff:freeze-watchdog", config.freezeCheckTicks, config.freezeCheckTicks, this::check);
    }

    /** Module disable: lets go of online players (flight, effects back) but keeps every freeze stored for the next start. */
    void stop() {
        if (reminder != null) {
            reminder.cancel();
        }
        if (watchdog != null) {
            watchdog.cancel();
        }
        for (UUID id : List.copyOf(spots.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                letGo(p);
            }
        }
        spots.clear();
    }

    boolean isFrozen(Player player) {
        return frozen.containsKey(player.getUniqueId());
    }

    private Optional<StaffStore.Frozen> byName(String name) {
        return frozen.values().stream().filter(f -> f.name().equalsIgnoreCase(name)).findFirst();
    }

    void toggle(CommandSender staff, Player target) {
        StaffStore.Frozen record = frozen.get(target.getUniqueId());
        if (record != null) {
            unfreeze(staff, record);
        } else {
            freeze(staff, target);
        }
    }

    void freeze(CommandSender staff, Player target) {
        if (staff instanceof Player p && p.equals(target)) {
            throw fail("freeze.self");
        }
        if (target.hasPermission(StaffPerms.FREEZE_EXEMPT)) {
            throw fail("freeze.exempt", Msg.p("player", target.getName()));
        }
        if (target.isInsideVehicle()) {
            target.leaveVehicle(); // before the freeze, so the dismount teleport isn't refused
        }
        StaffStore.Frozen record = new StaffStore.Frozen(target.getUniqueId(), target.getName(), staff.getName(),
                staff instanceof Player p ? p.getUniqueId() : null, System.currentTimeMillis(),
                target.getAllowFlight(), target.isFlying());
        frozen.put(target.getUniqueId(), record);
        fallGrace.remove(target.getUniqueId());
        persist(staff, record.uuid(), () -> store.saveFrozen(record));
        hold(target);
        remind(target);
        core.sounds().play(target, "warning");
        msg.send(staff, "freeze.done", Msg.p("player", target.getName()));
        notifyStaff(StaffPerms.NOTIFY, staff, "freeze.staff-on", Msg.p("staff", staff.getName()), Msg.p("player", target.getName()));
        audit("freeze", staff, target.getUniqueId(), target.getName(), Map.of());
    }

    void unfreeze(CommandSender staff, StaffStore.Frozen record) {
        if (frozen.remove(record.uuid()) == null) {
            throw fail("freeze.not-frozen", Msg.p("player", record.name()));
        }
        persist(staff, record.uuid(), () -> store.deleteFrozen(record.uuid()));
        Player target = Bukkit.getPlayer(record.uuid());
        if (target != null) {
            letGo(target, record);
            if (config.freezeFallGraceSeconds > 0) {
                fallGrace.put(target.getUniqueId(), System.currentTimeMillis() + config.freezeFallGraceSeconds * 1000L);
            }
            msg.send(target, "freeze.released", Msg.p("staff", staff.getName()));
            core.sounds().play(target, "success");
        }
        msg.send(staff, "freeze.undone", Msg.p("player", record.name()));
        notifyStaff(StaffPerms.NOTIFY, staff, "freeze.staff-off", Msg.p("staff", staff.getName()), Msg.p("player", record.name()));
        audit("unfreeze", staff, record.uuid(), record.name(), Map.of("online", target != null, "frozen_by", record.frozenBy(),
                "frozen_for_seconds", (System.currentTimeMillis() - record.frozenAt()) / 1000));
    }

    /**
     * Owner #4: a ban clears the freeze. Moderation fires this before kicking, so the quit handler no longer sees a freeze
     * and the logout action (e.g. an extra ban) does not run.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBanned(net.skybornmc.skyblock.common.moderation.PlayerBannedEvent event) {
        StaffStore.Frozen record = frozen.remove(event.getPlayer());
        if (record == null) {
            return;
        }
        persist(Bukkit.getConsoleSender(), record.uuid(), () -> store.deleteFrozen(record.uuid()));
        Player target = Bukkit.getPlayer(record.uuid());
        if (target != null) {
            letGo(target, record);
        }
        fallGrace.remove(record.uuid());
        notifyStaff(StaffPerms.NOTIFY, null, "freeze.staff-ban-cleared", Msg.p("player", record.name()));
        core.audit().logPlayer(AuditCategory.PUNISHMENT, "unfreeze_ban", Actor.system("ban"), record.uuid(), record.name(), null,
                target == null ? null : target.getLocation(), Map.of("banned_by", String.valueOf(event.getBannedBy()),
                        "punishment", event.getPunishmentId(), "frozen_by", record.frozenBy(),
                        "frozen_for_seconds", (System.currentTimeMillis() - record.frozenAt()) / 1000));
    }

    /** Saves or deletes in submit order; a failure is logged and told to the staff member (the freeze itself still works). */
    private void persist(CommandSender staff, UUID player, java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> write) {
        writes.submit(player, write).exceptionally(e -> {
            core.plugin().getLogger().severe("Could not store the freeze change of " + player + ": " + e.getMessage());
            core.scheduler().main("staff:freeze-save-failed", () -> {
                if (!(staff instanceof Player p) || p.isOnline()) {
                    msg.send(staff, "freeze.save-failed");
                }
            });
            return null;
        });
    }

    // ------------------------------------------------------------------ holding a player in place

    /** Starts holding an online frozen player at their current spot (freeze, join, module start). */
    private void hold(Player p) {
        if (p.isGliding()) {
            p.setGliding(false);
        }
        p.closeInventory();
        p.setFallDistance(0);
        spots.put(p.getUniqueId(), p.getLocation().clone());
        net.skybornmc.skyblock.common.flight.FlightGrants.grant(p.getUniqueId(), net.skybornmc.skyblock.common.flight.FlightGrants.FREEZE);
        keepHovering(p);
        p.setCollidable(false); // nobody can push them around
        hints(p);
    }

    /** Frozen players hover in place, so neither gravity nor the flight check moves or kicks them. */
    private static void keepHovering(Player p) {
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
        if (!p.isFlying()) {
            p.setFlying(true);
        }
    }

    /** Slowness and no jump: only hints that something is going on. */
    private void hints(Player p) {
        if (config.freezeSlownessLevel > 0) {
            int ticks = (config.freezeReminderSeconds + 5) * 20; // refreshed by the reminder; runs out on its own after a crash
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, config.freezeSlownessLevel - 1, false, false, true));
        }
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (config.freezeBlockJump && jump != null && jump.getModifier(jumpKey) == null) {
            jump.addTransientModifier(new AttributeModifier(jumpKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Stops holding (unfreeze, logout, module stop) and gives back flight; uses the stored pre-freeze flight state. */
    private void letGo(Player p) {
        letGo(p, frozen.get(p.getUniqueId()));
    }

    private void letGo(Player p, StaffStore.Frozen record) {
        spots.remove(p.getUniqueId());
        p.setCollidable(true);
        boolean creativeFlight = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
        // FIXES1: flight after a freeze comes from what still gives it (/fly, island fly, staff mode); the saved flag
        // only counts when a source still exists, so a freeze never leaves flight behind that nobody owns.
        boolean others = !net.skybornmc.skyblock.common.flight.FlightGrants.revoke(p.getUniqueId(),
                net.skybornmc.skyblock.common.flight.FlightGrants.FREEZE);
        boolean allow = creativeFlight || others;
        boolean flying = allow && (creativeFlight ? p.isFlying() : record == null || record.flying());
        p.setFlying(flying);
        p.setAllowFlight(allow);
        p.setFallDistance(0);
        PotionEffect slow = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow != null && !slow.isInfinite() && slow.getAmplifier() == config.freezeSlownessLevel - 1) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(jumpKey);
        }
        p.clearTitle();
    }

    private void remind(Player p) {
        msg.title(p, "freeze.title");
        msg.send(p, "freeze.instructions");
    }

    /** Watchdog: back to the spot if something moved them without a move event, out of vehicles, hovering, not gliding. */
    private void check() {
        for (Map.Entry<UUID, Location> e : List.copyOf(spots.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !frozen.containsKey(e.getKey())) {
                spots.remove(e.getKey());
                continue;
            }
            if (p.isInsideVehicle()) {
                p.leaveVehicle();
            }
            if (p.isGliding()) {
                p.setGliding(false);
            }
            keepHovering(p);
            Location spot = e.getValue();
            Location now = p.getLocation();
            if (!now.getWorld().equals(spot.getWorld()) || moved(spot, now)) {
                p.teleport(back(spot, now));
            }
        }
    }

    private static boolean moved(Location spot, Location to) {
        return FreezeRules.moved(spot.getX(), spot.getY(), spot.getZ(), to.getX(), to.getY(), to.getZ());
    }

    /** The freeze spot with the rotation of {@code look} (frozen players may look around). */
    private static Location back(Location spot, Location look) {
        Location back = spot.clone();
        back.setYaw(look.getYaw());
        back.setPitch(look.getPitch());
        return back;
    }

    private boolean atSpot(Player p, Location to) {
        Location spot = spots.get(p.getUniqueId());
        return spot != null && to != null && spot.getWorld().equals(to.getWorld()) && !moved(spot, to);
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveFirst(PlayerMoveEvent event) {
        holdInPlace(event);
    }

    /** Again at the end, in case a later listener changed the destination. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMoveLast(PlayerMoveEvent event) {
        holdInPlace(event);
    }

    private void holdInPlace(PlayerMoveEvent event) {
        if (frozen.isEmpty() || !isFrozen(event.getPlayer())) {
            return;
        }
        Player p = event.getPlayer();
        Location spot = spots.computeIfAbsent(p.getUniqueId(), id -> event.getFrom().clone());
        Location to = event.getTo();
        if (event.isCancelled() || !spot.getWorld().equals(to.getWorld()) || moved(spot, to)) {
            // setTo (not cancel): Paper teleports to it with the new yaw/pitch, so only the position is held.
            event.setCancelled(false);
            event.setTo(back(spot, to));
            p.setFallDistance(0);
        }
    }

    /** Our own reset to the freeze spot always passes; staff/plugin teleports pass if configured; the rest is refused. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!isFrozen(p)) {
            return;
        }
        if (!FreezeRules.teleportAllowed(event.getCause().name(), config.freezeAllowedTeleportCauses, atSpot(p, event.getTo()))) {
            event.setCancelled(true);
        }
    }

    /** A staff/plugin teleport moved them: hold them at the new spot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (isFrozen(p) && spots.containsKey(p.getUniqueId()) && !atSpot(p, event.getTo())) {
            spots.put(p.getUniqueId(), event.getTo().clone());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(PlayerPortalEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVelocity(PlayerVelocityEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKnockback(EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRiptide(PlayerRiptideEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    /** They stay hovering (no landing, no flying off). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (isFrozen(p)) {
            core.scheduler().later("staff:freeze-respawn", 1L, () -> {
                if (p.isOnline() && isFrozen(p)) {
                    hold(p);
                }
            });
        }
    }

    // ------------------------------------------------------------------ actions

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Also covers PlayerInteractAtEntityEvent (same handler list). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    /** No damage taken or dealt (directly or with projectiles); no fall damage right after an unfreeze. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (isFrozen(p)) {
                event.setCancelled(true);
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && inFallGrace(p)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && dealtByFrozen(byEntity.getDamager())) {
            event.setCancelled(true);
        }
    }

    private boolean dealtByFrozen(Entity damager) {
        if (damager instanceof Player p) {
            return isFrozen(p);
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter && isFrozen(shooter);
    }

    private boolean inFallGrace(Player p) {
        Long until = fallGrace.get(p.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            fallGrace.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        if (!FreezeRules.commandAllowed(event.getMessage(), config.freezeAllowedCommands)) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "freeze.no-commands");
        }
    }

    /** Frozen players talk to staff only. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!isFrozen(p)) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        core.scheduler().main("staff:frozen-chat", () -> {
            msg.send(p, "freeze.chat", Msg.p("player", p.getName()), Msg.p("message", text));
            notifyStaff(StaffPerms.FREEZE, p, "freeze.chat", Msg.p("player", p.getName()), Msg.p("message", text));
            core.audit().logPlayer(AuditCategory.CHAT, "frozen_chat", Actor.player(p), null, null, null, p.getLocation(),
                    Map.of("message", text));
        });
    }

    // ------------------------------------------------------------------ logout / rejoin

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        fallGrace.remove(p.getUniqueId());
        StaffStore.Frozen record = frozen.get(p.getUniqueId());
        if (record == null) {
            return;
        }
        // Quit fires before the player file is saved: give back their own flight so a crash later can't keep it.
        letGo(p, record);
        if (!config.freezeKeepOnRejoin) {
            frozen.remove(p.getUniqueId());
            persist(Bukkit.getConsoleSender(), p.getUniqueId(), () -> store.deleteFrozen(p.getUniqueId()));
        }
        notifyStaff(StaffPerms.FREEZE, null, "freeze.logout", Msg.p("player", p.getName()),
                Msg.p("action", config.freezeLogoutAction.name().toLowerCase(Locale.ROOT)));
        core.audit().logPlayer(AuditCategory.PUNISHMENT, "freeze_logout", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of("frozen_by", record.frozenBy(), "action", config.freezeLogoutAction.name(),
                        "stays_frozen", config.freezeKeepOnRejoin));
        if (config.freezeLogoutAction == StaffConfig.LogoutAction.BAN) {
            String command = config.freezeBanCommand.replace("<player>", p.getName());
            core.scheduler().main("staff:freeze-ban", () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    /** First thing on join, before the player can send a single move; checked again a tick later (after spawn teleports). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        StaffStore.Frozen record = frozen.get(p.getUniqueId());
        if (record == null) {
            return;
        }
        if (!record.name().equals(p.getName())) {
            StaffStore.Frozen renamed = new StaffStore.Frozen(record.uuid(), p.getName(), record.frozenBy(), record.frozenByUuid(),
                    record.frozenAt(), record.allowFlight(), record.flying());
            frozen.put(p.getUniqueId(), renamed);
            persist(Bukkit.getConsoleSender(), p.getUniqueId(), () -> store.saveFrozen(renamed));
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        hold(p);
        remind(p);
        notifyStaff(StaffPerms.FREEZE, null, "freeze.rejoined", Msg.p("player", p.getName()));
        core.audit().logPlayer(AuditCategory.STAFF, "freeze_rejoin", Actor.system("staff"), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of("frozen_by", record.frozenBy()));
        core.scheduler().later("staff:freeze-join-check", 1L, () -> {
            if (p.isOnline() && isFrozen(p)) {
                hold(p); // other join handlers may have changed flight or position meanwhile
            }
        });
    }
}
