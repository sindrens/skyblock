package net.skybornmc.skyblock.common.item;

import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Stagger;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Finds duplicated unique items: the same item UUID twice among all online inventories, ender chests, open
 * containers and cursors. Both copies are flagged ({@link ItemKeys#DUPE_FLAG}), staff with
 * {@code skyblock.staff.notify} are alerted and every {@link DupeReport.Handler} is called (console log by default;
 * the audit log is added as a handler in M0.8).
 *
 * <p>Runs on the main thread as a rolling scan (PERF2): each player is read again once per {@code dupe-check.scan-seconds}
 * (one slice every second), and a player who joins or opens/closes an inventory is read again after a short debounce.
 * Every read is checked against the remembered copies of all online players; a remembered copy only counts while the
 * item is still in that slot, so moved items never cause false alarms. Cost: one PDC lookup per non-empty slot of the
 * players read; text is only built for unique items.</p>
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
    /** The unique-item copies of each online player as last read (PERF2 rolling scan). */
    private final Map<UUID, List<Copy>> remembered = new HashMap<>();
    /** Players to read again on the next event scan. */
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private long rollSecond;
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
        // PERF2 (P-06): a rolling scan. Every second one slice of the players is read again (each player once per
        // scan-seconds), and the duplicate check runs over the remembered copies of everybody.
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::roll, 20L, 20L);
    }

    // ------------------------------------------------------------------ triggers

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        requestScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            requestScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            requestScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        remembered.remove(id);
        dirty.remove(id);
    }

    /** Schedules a scan of every online player (at most once per {@code event-scan-min-ticks}). */
    public void requestScan() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dirty.add(player.getUniqueId());
        }
        queue();
    }

    /**
     * Schedules a scan of this player's inventories (joins, opening and closing inventories), checked against the
     * remembered copies of everybody else; at most once per {@code event-scan-min-ticks}. It used to rescan every online
     * player each time anybody opened or closed an inventory.
     */
    public void requestScan(Player player) {
        dirty.add(player.getUniqueId());
        queue();
    }

    private void queue() {
        if (!config.dupeCheckEnabled || scanQueued) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        long delay = Math.max(1, (long) lastEventScanTick + config.dupeEventScanMinTicks - now);
        scanQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scanQueued = false;
            lastEventScanTick = Bukkit.getCurrentTick();
            List<Player> players = new ArrayList<>();
            for (UUID id : dirty) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    players.add(p);
                }
            }
            dirty.clear();
            rescan(players);
        }, delay);
    }

    // ------------------------------------------------------------------ scan

    /** One second of the rolling scan: the players whose slot is due are read again. */
    private void roll() {
        if (!config.dupeCheckEnabled) {
            return;
        }
        long second = ++rollSecond;
        int period = Math.max(1, config.dupeScanSeconds);
        List<Player> due = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Stagger.due(player.getUniqueId(), second, period)) {
                due.add(player);
            }
        }
        rescan(due);
    }

    /** Full scan of every online player now. Main thread only. */
    public void scan() {
        rescan(new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    /**
     * Reads the given players again, then checks every remembered copy for duplicates. A copy remembered from an earlier
     * read is only counted when it is still where it was seen ({@link #confirm}), so items that moved since then never
     * cause a false alarm. Main thread only.
     */
    private void rescan(List<Player> players) {
        if (!config.dupeCheckEnabled) {
            return;
        }
        try {
            Set<Inventory> seen = new HashSet<>();
            for (Player player : players) {
                if (player.isOnline()) {
                    remembered.put(player.getUniqueId(), collectPlayer(player, seen));
                }
            }
            List<Copy> all = new ArrayList<>();
            remembered.values().forEach(all::addAll);
            Map<UUID, List<Copy>> dupes = findDuplicates(all);
            if (!dupes.isEmpty()) {
                long now = System.currentTimeMillis();
                dupes.forEach((uuid, list) -> {
                    List<Copy> real = confirm(list, DupeDetector::stillThere);
                    if (real.size() >= 2) {
                        handle(uuid, real, now);
                    }
                });
            }
            long expiry = System.currentTimeMillis() - config.dupeAlertCooldownMinutes * 60_000L;
            lastAlert.values().removeIf(t -> t < expiry);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Dupe scan failed", e);
        }
    }

    private List<Copy> collectPlayer(Player player, Set<Inventory> seen) {
        List<Copy> copies = new ArrayList<>();
        String name = player.getName();
        collect(player.getInventory(), name, "inventory", player.getUniqueId(), seen, copies);
        collect(player.getEnderChest(), name, "ender chest", player.getUniqueId(), seen, copies);
        Inventory top = player.getOpenInventory().getTopInventory();
        // Plugin menus only show copies for display (players can't take them), so they are never scanned.
        if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER
                && !(top.getHolder(false) instanceof net.skybornmc.skyblock.common.menu.Menu)) {
            collect(top, name, "open " + top.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                    player.getUniqueId(), seen, copies);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (uniqueId(cursor) != null) {
            Copy copy = copyOf(cursor, name + " cursor", player.getUniqueId(), null, -1);
            if (copy != null) {
                copies.add(copy);
            }
        }
        return copies.isEmpty() ? List.of() : copies;
    }

    private void collect(Inventory inventory, String owner, String what, UUID player, Set<Inventory> seen, List<Copy> out) {
        if (!seen.add(inventory)) {
            return; // the same container viewed by two players is one container
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (uniqueId(stack) == null) {
                continue; // empty or not a unique item: no text is built for it
            }
            Copy copy = copyOf(stack, owner + " " + what + " slot " + slot, player, inventory, slot);
            if (copy != null) {
                out.add(copy);
            }
        }
    }

    /** The raw unique id of an item, or null for empty slots and normal items. */
    private static String uniqueId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(ItemKeys.UUID, PersistentDataType.STRING);
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

    /** Whether the item is still in the slot (or on the cursor) where this copy was seen. */
    private static boolean stillThere(Copy copy) {
        ItemStack now;
        if (copy.inventory() == null) {
            Player player = Bukkit.getPlayer(copy.player());
            now = player == null ? null : player.getItemOnCursor();
        } else {
            now = copy.slot() < 0 || copy.slot() >= copy.inventory().getSize() ? null : copy.inventory().getItem(copy.slot());
        }
        return copy.itemUuid().toString().equals(uniqueId(now));
    }

    /** Where a copy is: a container slot, or a player's cursor. Containers compare by the container they wrap. */
    record Place(Inventory inventory, int slot, UUID cursorOf) {
        static Place of(Copy copy) {
            return copy.inventory() == null ? new Place(null, -1, copy.player()) : new Place(copy.inventory(), copy.slot(), null);
        }
    }

    /**
     * The copies that are real: one per place (the same chest slot seen by two players is one item) and only those still
     * there now (a remembered copy of an item that moved away is dropped). Order kept. Pure apart from {@code stillThere}.
     */
    static List<Copy> confirm(List<Copy> copies, java.util.function.Predicate<Copy> stillThere) {
        Map<Place, Copy> byPlace = new LinkedHashMap<>();
        for (Copy copy : copies) {
            byPlace.putIfAbsent(Place.of(copy), copy);
        }
        List<Copy> out = new ArrayList<>(byPlace.size());
        for (Copy copy : byPlace.values()) {
            if (stillThere.test(copy)) {
                out.add(copy);
            }
        }
        return out;
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
