package net.skybornmc.skyblock.common.item;

import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Finds duplicated unique items: the same item UUID twice among all online inventories, ender chests, open
 * containers and cursors. Both copies are flagged ({@link ItemKeys#DUPE_FLAG}), staff with
 * {@code skyblock.staff.notify} are alerted and every {@link DupeReport.Handler} is called (console log by default;
 * the audit log is added as a handler in M0.8).
 *
 * <p>Runs on the main thread: a full scan every {@code dupe-check.scan-seconds}, plus a debounced scan after joins and
 * inventory open/close. Cost: one PDC lookup per non-empty slot (~100 per player).</p>
 */
public final class DupeDetector implements Listener {

    public static final String PERM_NOTIFY = "skyblock.staff.notify";

    /** One copy of a unique item somewhere. {@code inventory == null} means the player's cursor. */
    record Copy(UUID itemUuid, String itemId, String source, String where, UUID player, Inventory inventory, int slot) {
    }

    private final JavaPlugin plugin;
    private final ItemsConfig config;
    private final ModuleMessages msg;
    private final List<DupeReport.Handler> handlers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private BukkitTask periodic;
    private boolean scanQueued;
    private int lastEventScanTick = Integer.MIN_VALUE / 2;

    public DupeDetector(JavaPlugin plugin, ItemsConfig config, ModuleMessages msg) {
        this.plugin = plugin;
        this.config = config;
        this.msg = msg;
        handlers.add(this::defaultAlert);
    }

    /** Hook for other systems (M0.8 audit log, Discord alerts). */
    public void addHandler(DupeReport.Handler handler) {
        handlers.add(handler);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        schedule();
    }

    /** Re-applies the scan interval after a config reload. */
    public void reload() {
        schedule();
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
    }

    private void schedule() {
        if (periodic != null) {
            periodic.cancel();
        }
        long ticks = config.dupeScanSeconds * 20L;
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, ticks, ticks);
    }

    // ------------------------------------------------------------------ triggers

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        requestScan();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        requestScan();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        requestScan();
    }

    /** Schedules one scan, at most once per {@code event-scan-min-ticks}. */
    public void requestScan() {
        if (!config.dupeCheckEnabled || scanQueued) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        long delay = Math.max(1, (long) lastEventScanTick + config.dupeEventScanMinTicks - now);
        scanQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scanQueued = false;
            lastEventScanTick = Bukkit.getCurrentTick();
            scan();
        }, delay);
    }

    // ------------------------------------------------------------------ scan

    /** Full scan of every online player. Main thread only. */
    public void scan() {
        if (!config.dupeCheckEnabled) {
            return;
        }
        try {
            List<Copy> copies = new ArrayList<>();
            IdentityHashMap<Inventory, Boolean> seen = new IdentityHashMap<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                collect(player.getInventory(), name, "inventory", player.getUniqueId(), seen, copies);
                collect(player.getEnderChest(), name, "ender chest", player.getUniqueId(), seen, copies);
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
                    collect(top, name, "open " + top.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                            player.getUniqueId(), seen, copies);
                }
                Copy cursor = copyOf(player.getItemOnCursor(), name + " cursor", player.getUniqueId(), null, -1);
                if (cursor != null) {
                    copies.add(cursor);
                }
            }
            Map<UUID, List<Copy>> dupes = findDuplicates(copies);
            if (!dupes.isEmpty()) {
                long now = System.currentTimeMillis();
                dupes.forEach((uuid, list) -> handle(uuid, list, now));
            }
            long expiry = System.currentTimeMillis() - config.dupeAlertCooldownMinutes * 60_000L;
            lastAlert.values().removeIf(t -> t < expiry);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Dupe scan failed", e);
        }
    }

    private void collect(Inventory inventory, String owner, String what, UUID player,
                         IdentityHashMap<Inventory, Boolean> seen, List<Copy> out) {
        if (seen.put(inventory, Boolean.TRUE) != null) {
            return; // the same container viewed by two players is one container
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Copy copy = copyOf(inventory.getItem(slot), owner + " " + what + " slot " + slot, player, inventory, slot);
            if (copy != null) {
                out.add(copy);
            }
        }
    }

    private static Copy copyOf(ItemStack stack, String where, UUID player, Inventory inventory, int slot) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        String raw = pdc.get(ItemKeys.UUID, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Copy(uuid, pdc.getOrDefault(ItemKeys.ID, PersistentDataType.STRING, "?"),
                pdc.getOrDefault(ItemKeys.SOURCE, PersistentDataType.STRING, "?"), where, player, inventory, slot);
    }

    /** Groups copies by item UUID and keeps only UUIDs seen more than once (insertion order kept). */
    static Map<UUID, List<Copy>> findDuplicates(List<Copy> copies) {
        Map<UUID, List<Copy>> byUuid = new LinkedHashMap<>();
        for (Copy copy : copies) {
            byUuid.computeIfAbsent(copy.itemUuid(), k -> new ArrayList<>()).add(copy);
        }
        byUuid.values().removeIf(list -> list.size() < 2);
        return byUuid;
    }

    private void handle(UUID uuid, List<Copy> list, long now) {
        for (Copy copy : list) {
            flag(copy, now);
        }
        Long last = lastAlert.get(uuid);
        if (last != null && now - last < config.dupeAlertCooldownMinutes * 60_000L) {
            return;
        }
        lastAlert.put(uuid, now);
        Copy first = list.getFirst();
        DupeReport report = new DupeReport(uuid, first.itemId(), first.source(),
                list.stream().map(Copy::where).toList(),
                List.copyOf(new LinkedHashSet<>(list.stream().map(Copy::player).toList())), now);
        for (DupeReport.Handler handler : handlers) {
            try {
                handler.onDupe(report);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Dupe handler failed", e);
            }
        }
    }

    private static void flag(Copy copy, long now) {
        if (copy.inventory() == null) {
            Player player = Bukkit.getPlayer(copy.player());
            if (player != null) {
                ItemStack cursor = player.getItemOnCursor();
                cursor.editPersistentDataContainer(pdc -> pdc.set(ItemKeys.DUPE_FLAG, PersistentDataType.LONG, now));
                player.setItemOnCursor(cursor);
            }
            return;
        }
        ItemStack stack = copy.inventory().getItem(copy.slot());
        if (stack != null && !stack.isEmpty()) {
            stack.editPersistentDataContainer(pdc -> pdc.set(ItemKeys.DUPE_FLAG, PersistentDataType.LONG, now));
            copy.inventory().setItem(copy.slot(), stack);
        }
    }

    /** Console log + staff chat alert. */
    private void defaultAlert(DupeReport report) {
        plugin.getLogger().severe("[Dupe] " + report.itemId() + " (" + report.itemUuid() + ", source " + report.source()
                + ") found " + report.holders().size() + "x: " + String.join(" | ", report.holders()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission(PERM_NOTIFY)) {
                continue;
            }
            msg.send(staff, "dupe.alert", Msg.p("item", report.itemId()), Msg.p("uuid", report.itemUuid()),
                    Msg.p("count", report.holders().size()), Msg.p("source", report.source()));
            for (String holder : report.holders()) {
                msg.send(staff, "dupe.holder", Msg.p("where", holder));
            }
        }
    }
}
