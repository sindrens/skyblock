package net.skybornmc.skyblock.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /staff}: staff mode. Your inventory, game mode and flight are saved (in memory and in the database, so a crash
 * restores them on the next join) and swapped for the staff kit; vanish, flight, night vision, no damage, no hunger,
 * no item pickup, mobs ignore you. Kit items can't leave the hotbar in any way and are removed on exit.
 */
final class StaffMode extends StaffFeature implements Listener {

    /** The kit. Slots are the hotbar positions. */
    enum Tool {
        INSPECT(0, Material.SPYGLASS),
        FREEZE(1, Material.PACKED_ICE),
        RANDOM_TP(2, Material.COMPASS),
        LOGS(3, Material.CLOCK),
        VANISH(4, Material.LIME_DYE),
        PUNISH(6, Material.BLAZE_ROD),
        REPORTS(7, Material.WRITABLE_BOOK),
        EXIT(8, Material.BARRIER);

        final int slot;
        final Material material;

        Tool(int slot, Material material) {
            this.slot = slot;
            this.material = material;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private record Session(byte[] contents, GameMode gameMode, boolean allowFlight, boolean flying, boolean wasVanished) {
    }

    private final FreezeService freeze;
    private final ReportService reports;
    private final StaffModule.Services services;
    private final NamespacedKey toolKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    /** Players whose crash-recovery check is still running: /staff waits so a saved inventory is never overwritten. */
    private final java.util.Set<UUID> checking = ConcurrentHashMap.newKeySet();

    StaffMode(Core core, StaffConfig config, ModuleMessages msg, StaffStore store, FreezeService freeze, ReportService reports,
              StaffModule.Services services) {
        super(core, config, msg, store);
        this.freeze = freeze;
        this.reports = reports;
        this.services = services;
        this.toolKey = new NamespacedKey(core.plugin(), "staff_tool");
    }

    void register() {
        core.commands().register(CommandSpec.command("staff")
                .description("Toggle staff mode").category("Staff")
                .permission(StaffPerms.MODE).playerOnly().aliases("staffmode", "mod")
                .executes(ctx -> toggle(ctx.player())));
    }

    boolean inStaffMode(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private void toggle(Player player) {
        if (checking.contains(player.getUniqueId())) {
            throw fail("mode.wait");
        }
        if (inStaffMode(player)) {
            exit(player, true);
        } else {
            enter(player);
        }
    }

    // ------------------------------------------------------------------ enter / exit

    /** The essentials module marks vanished players with the conventional "vanished" metadata. */
    @SuppressWarnings("deprecation")
    private static boolean vanished(Player player) {
        return player.hasMetadata("vanished") && player.getMetadata("vanished").stream().anyMatch(v -> v.asBoolean());
    }

    private void enter(Player player) {
        PlayerInventory inv = player.getInventory();
        byte[] contents = ItemStack.serializeItemsAsBytes(inv.getContents());
        Session session = new Session(contents, player.getGameMode(), player.getAllowFlight(), player.isFlying(), vanished(player));
        sessions.put(player.getUniqueId(), session);
        inv.clear();
        player.setItemOnCursor(null);
        // Saved before anything else changes; if the database refuses, staff mode is cancelled and everything restored.
        store.saveSnapshot(new StaffStore.Snapshot(player.getUniqueId(), contents, session.gameMode().name(),
                session.allowFlight(), session.flying(), session.wasVanished())).exceptionally(e -> {
            Bukkit.getScheduler().runTask(core.plugin(), () -> {
                core.plugin().getLogger().severe("Could not save staff-mode inventory of " + player.getName() + ": " + e.getMessage());
                if (player.isOnline() && inStaffMode(player)) {
                    exit(player, false);
                    msg.send(player, "mode.save-failed");
                }
            });
            return null;
        });
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCanPickupItems(false);
        if (config.modeNightVision) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        if (config.modeVanish && !session.wasVanished()) {
            dispatch(player, "vanish");
        }
        giveKit(player);
        msg.send(player, "mode.on");
        core.sounds().play(player, "unlock");
        notifyStaff(StaffPerms.NOTIFY, player, "mode.staff-on", Msg.p("staff", player.getName()));
        audit("staff_mode_on", player, null, null, Map.of());
    }

    /** Leaves staff mode and gives everything back. */
    void exit(Player player, boolean announce) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restore(player, session.contents(), session.gameMode(), session.allowFlight(), session.flying());
        if (config.modeNightVision) {
            PotionEffect nv = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (nv != null && nv.isInfinite()) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
        if (config.modeVanish && !session.wasVanished() && vanished(player)) {
            dispatch(player, "vanish");
        }
        store.deleteSnapshot(player.getUniqueId());
        if (announce) {
            msg.send(player, "mode.off");
            core.sounds().play(player, "success");
            notifyStaff(StaffPerms.NOTIFY, player, "mode.staff-off", Msg.p("staff", player.getName()));
        }
        audit("staff_mode_off", player, null, null, Map.of());
    }

    private void restore(Player player, byte[] contents, GameMode gameMode, boolean allowFlight, boolean flying) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        player.setItemOnCursor(null);
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(contents);
        ItemStack[] sized = new ItemStack[inv.getSize()];
        System.arraycopy(items, 0, sized, 0, Math.min(items.length, sized.length));
        inv.setContents(sized);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setCanPickupItems(true);
    }

    private void giveKit(Player player) {
        for (Tool tool : Tool.values()) {
            player.getInventory().setItem(tool.slot, tool(player, tool));
        }
        player.getInventory().setHeldItemSlot(Tool.INSPECT.slot);
    }

    private ItemStack tool(Player viewer, Tool tool) {
        Material material = tool == Tool.VANISH && !vanished(viewer) ? Material.GRAY_DYE : tool.material;
        ItemStack item = new ItemStack(material);
        String key = "tool." + tool.key() + (tool == Tool.VANISH ? (vanished(viewer) ? ".on" : ".off") : "");
        item.editMeta(meta -> {
            meta.displayName(msg.component(viewer, key + ".name").decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            List<Component> lore = new ArrayList<>();
            String raw = core.messages().raw(MODULE, key + ".lore");
            if (raw != null) {
                for (String line : raw.split("<newline>")) {
                    lore.add(core.messages().parse(viewer, line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                }
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
        });
        return item;
    }

    private Optional<Tool> toolOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Tool.valueOf(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isTool(ItemStack stack) {
        return toolOf(stack).isPresent();
    }

    /** Removes every staff tool from an inventory (after exit, a crash, or a creative clone). */
    private void purge(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTool(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isTool(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        player.getEnderChest().forEach(item -> {
            if (isTool(item)) {
                item.setAmount(0);
            }
        });
    }

    void shutdown() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                exit(p, false);
                try {
                    store.deleteSnapshot(id).join();
                } catch (RuntimeException e) {
                    core.plugin().getLogger().warning("Staff mode snapshot of " + p.getName() + " stays in the database: " + e.getMessage());
                }
            }
        }
    }

    // ------------------------------------------------------------------ tools

    private Optional<Player> target(Player staff, Entity clicked) {
        if (clicked instanceof Player p && !p.equals(staff)) {
            return Optional.of(p);
        }
        Entity looked = staff.getTargetEntity(config.toolRange);
        return looked instanceof Player p && !p.equals(staff) ? Optional.of(p) : Optional.empty();
    }

    private boolean debounce(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastUse.put(player.getUniqueId(), now);
        return last != null && now - last < 250;
    }

    private void use(Player staff, Tool tool, Entity clicked) {
        if (debounce(staff)) {
            return;
        }
        switch (tool) {
            case INSPECT -> target(staff, clicked).ifPresentOrElse(t -> InspectMenu.open(services, staff, t.getName()),
                    () -> msg.actionBar(staff, "tool.need-target"));
            case FREEZE -> target(staff, clicked).ifPresentOrElse(t -> {
                if (staff.hasPermission(StaffPerms.FREEZE)) {
                    try {
                        freeze.toggle(staff, t);
                    } catch (net.skybornmc.skyblock.common.command.CommandFailure f) {
                        core.messages().send(staff, f.module(), f.key(), f.placeholders());
                    }
                }
            }, () -> msg.actionBar(staff, "tool.need-target"));
            case RANDOM_TP -> randomTeleport(staff);
            case LOGS -> dispatch(staff, "logs near");
            case VANISH -> {
                dispatch(staff, "vanish");
                Bukkit.getScheduler().runTask(core.plugin(), () -> staff.getInventory().setItem(Tool.VANISH.slot, tool(staff, Tool.VANISH)));
            }
            case PUNISH -> target(staff, clicked).ifPresentOrElse(t -> dispatch(staff, "punish " + t.getName()),
                    () -> msg.actionBar(staff, "tool.need-target"));
            case REPORTS -> reports.openList(staff, Report.Status.OPEN);
            case EXIT -> exit(staff, true);
        }
    }

    private void randomTeleport(Player staff) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(staff) && !(config.randomTpSkipStaff && p.hasPermission(StaffPerms.MODE)) && !vanished(p)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            msg.actionBar(staff, "tool.random-tp.none");
            return;
        }
        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        staff.teleportAsync(target.getLocation()).thenAccept(ok -> {
            if (ok) {
                msg.actionBar(staff, "tool.random-tp.done", Msg.p("player", target.getName()));
                core.sounds().play(staff, "teleport");
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Optional<Tool> tool = toolOf(event.getItem());
        if (tool.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (inStaffMode(event.getPlayer())) {
            use(event.getPlayer(), tool.get(), null);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<Tool> tool = toolOf(event.getPlayer().getInventory().getItemInMainHand());
        if (tool.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (inStaffMode(event.getPlayer())) {
            use(event.getPlayer(), tool.get(), event.getRightClicked());
        }
    }

    // ------------------------------------------------------------------ protection (nothing leaves, staff are untouchable)

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean tool = isTool(event.getCurrentItem()) || isTool(event.getCursor());
        if (event.getHotbarButton() >= 0) {
            tool |= isTool(p.getInventory().getItem(event.getHotbarButton()));
        }
        if (tool || inStaffMode(p) && event.getClickedInventory() == p.getInventory()) {
            event.setCancelled(true);
            if (!inStaffMode(p)) {
                Bukkit.getScheduler().runTask(core.plugin(), () -> purge(p));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (isTool(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isTool(event.getItemDrop().getItemStack()) || inStaffMode(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isTool(event.getMainHandItem()) || isTool(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isTool(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isTool);
        event.getItemsToKeep().removeIf(this::isTool);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && inStaffMode(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && inStaffMode(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && inStaffMode(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player p && inStaffMode(p)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ quit / crash recovery

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // Restore before the player file is saved, so the real inventory is what gets stored.
        exit(event.getPlayer(), false);
        lastUse.remove(event.getPlayer().getUniqueId());
        checking.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        purge(player);
        if (!player.hasPermission(StaffPerms.MODE)) {
            return;
        }
        checking.add(player.getUniqueId());
        store.snapshot(player.getUniqueId()).whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            try {
                if (error != null) {
                    // Keep /staff blocked for this session: a saved inventory may exist and must not be overwritten.
                    core.plugin().getLogger().warning("Could not check staff-mode snapshot of " + player.getName() + ": " + error.getMessage());
                    msg.send(player, "mode.check-failed");
                    return;
                }
                checking.remove(player.getUniqueId());
                if (snapshot.isEmpty() || !player.isOnline() || inStaffMode(player)) {
                    return;
                }
                StaffStore.Snapshot s = snapshot.get();
                GameMode mode;
                try {
                    mode = GameMode.valueOf(s.gameMode());
                } catch (IllegalArgumentException e) {
                    mode = GameMode.SURVIVAL;
                }
                restore(player, s.contents(), mode, s.allowFlight(), s.flying());
                store.deleteSnapshot(player.getUniqueId());
                msg.send(player, "mode.recovered");
                core.plugin().getLogger().info("Restored the staff-mode inventory of " + player.getName() + " after a crash.");
            } catch (RuntimeException e) {
                core.plugin().getLogger().severe("Staff-mode recovery of " + player.getName() + " failed: " + e.getMessage());
            }
        }));
    }
}
