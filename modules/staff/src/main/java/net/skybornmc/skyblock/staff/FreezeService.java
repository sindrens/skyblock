package net.skybornmc.skyblock.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /freeze <player>}: the player can't move (looking around is fine), interact, build, drop, teleport, take or
 * deal damage, or use commands except the allowed list; chat goes to staff only. Title + chat instructions repeat.
 * Logging out while frozen runs the configured action. Staff are alerted; everything is audited.
 */
final class FreezeService extends StaffFeature implements Listener {

    /** frozen player → who froze them. */
    private final Map<UUID, String> frozen = new ConcurrentHashMap<>();
    /** players who logged out frozen (kept frozen on rejoin if configured). */
    private final Set<UUID> pendingRejoin = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> flightBefore = new ConcurrentHashMap<>();
    private BukkitTask reminder;

    FreezeService(Core core, StaffConfig config, ModuleMessages msg, StaffStore store) {
        super(core, config, msg, store);
    }

    void register() {
        Arg<Player> target = Args.onlinePlayer("player");
        core.commands().register(CommandSpec.command("freeze")
                .description("Freeze or unfreeze a player (for checks)").category("Staff")
                .permission(StaffPerms.FREEZE).arg(target).aliases("ss").example("freeze Steve")
                .executes(ctx -> toggle(ctx.sender(), ctx.get(target))));
        Arg<Player> unfreezeTarget = Arg.word("player", (sender, raw) -> {
            Player p = Bukkit.getPlayerExact(raw);
            if (p == null || !isFrozen(p)) {
                throw fail("freeze.not-frozen", Msg.p("player", raw));
            }
            return p;
        }, net.skybornmc.skyblock.common.command.Suggest.of(() -> frozen.keySet().stream()
                .map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).map(Player::getName).toList()));
        core.commands().register(CommandSpec.command("unfreeze")
                .description("Unfreeze a player").category("Staff")
                .permission(StaffPerms.FREEZE).arg(unfreezeTarget)
                .executes(ctx -> unfreeze(ctx.sender(), ctx.get(unfreezeTarget))));
    }

    void start() {
        reminder = Bukkit.getScheduler().runTaskTimer(core.plugin(), () -> frozen.keySet().forEach(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                remind(p);
            }
        }), 40L, config.freezeReminderSeconds * 20L);
    }

    void stop() {
        if (reminder != null) {
            reminder.cancel();
        }
        for (UUID id : Set.copyOf(frozen.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                release(p);
            }
        }
        frozen.clear();
    }

    boolean isFrozen(Player player) {
        return frozen.containsKey(player.getUniqueId());
    }

    void toggle(CommandSender staff, Player target) {
        if (isFrozen(target)) {
            unfreeze(staff, target);
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
        apply(target, staff.getName());
        msg.send(staff, "freeze.done", Msg.p("player", target.getName()));
        notifyStaff(StaffPerms.NOTIFY, staff, "freeze.staff-on", Msg.p("staff", staff.getName()), Msg.p("player", target.getName()));
        audit("freeze", staff, target.getUniqueId(), target.getName(), Map.of());
    }

    void unfreeze(CommandSender staff, Player target) {
        if (!isFrozen(target)) {
            throw fail("freeze.not-frozen", Msg.p("player", target.getName()));
        }
        release(target);
        msg.send(target, "freeze.released", Msg.p("staff", staff.getName()));
        core.sounds().play(target, "success");
        msg.send(staff, "freeze.undone", Msg.p("player", target.getName()));
        notifyStaff(StaffPerms.NOTIFY, staff, "freeze.staff-off", Msg.p("staff", staff.getName()), Msg.p("player", target.getName()));
        audit("unfreeze", staff, target.getUniqueId(), target.getName(), Map.of());
    }

    private void apply(Player target, String by) {
        frozen.put(target.getUniqueId(), by);
        flightBefore.put(target.getUniqueId(), target.getAllowFlight());
        target.setAllowFlight(true); // hovering in place must not get them kicked for flying
        target.closeInventory();
        remind(target);
        core.sounds().play(target, "warning");
    }

    private void release(Player target) {
        frozen.remove(target.getUniqueId());
        Boolean flight = flightBefore.remove(target.getUniqueId());
        if (flight != null && !flight) {
            target.setFlying(false);
            target.setAllowFlight(false);
        }
        target.clearTitle();
    }

    private void remind(Player p) {
        msg.title(p, "freeze.title");
        msg.send(p, "freeze.instructions");
    }

    // ------------------------------------------------------------------ restrictions

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (frozen.isEmpty() || !isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isFrozen(event.getPlayer()) && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
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
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
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
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player p && isFrozen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!config.freezeAllowedCommands.contains(label)) {
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
        Bukkit.getScheduler().runTask(core.plugin(), () -> {
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
        String by = frozen.get(p.getUniqueId());
        if (by == null) {
            return;
        }
        release(p);
        if (config.freezeKeepOnRejoin) {
            pendingRejoin.add(p.getUniqueId());
        }
        notifyStaff(StaffPerms.FREEZE, null, "freeze.logout", Msg.p("player", p.getName()),
                Msg.p("action", config.freezeLogoutAction.name().toLowerCase(Locale.ROOT)));
        core.audit().logPlayer(AuditCategory.PUNISHMENT, "freeze_logout", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of("frozen_by", by, "action", config.freezeLogoutAction.name()));
        if (config.freezeLogoutAction == StaffConfig.LogoutAction.BAN) {
            String command = config.freezeBanCommand.replace("<player>", p.getName());
            Bukkit.getScheduler().runTask(core.plugin(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (pendingRejoin.remove(p.getUniqueId())) {
            apply(p, "rejoin");
            notifyStaff(StaffPerms.FREEZE, null, "freeze.rejoined", Msg.p("player", p.getName()));
        }
    }
}
