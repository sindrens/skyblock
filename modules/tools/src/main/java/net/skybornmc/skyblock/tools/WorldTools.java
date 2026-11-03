package net.skybornmc.skyblock.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.player.InventorySwappedEvent;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owner 3 Nov 2026 ("depth and island and spawn have different inventory. spawn has same as island and depth is only in
 * depth"): two inventory sets per profile, <b>surface</b> (island, spawn, everything else) and <b>depths</b>
 * ({@code tools.yml world-tools.depths-worlds}). Crossing between them swaps the whole inventory (storage, armor,
 * offhand); the ender chest is shared. Depths tools therefore never reach the island, and island items never reach the
 * Depths. The first visit to the Depths starts with an empty set plus the Depths kit ({@code world-inventory.depths-items}:
 * the satchel, the dungeon map, torches) and the starter tools (once per profile).
 *
 * <p><b>Which set is live</b> is a marker on the player ({@code skyblock:inventory_side} = {@code <profile>:<side>}), saved
 * in the same player file as the items, so the two can never disagree after a crash. The other side's items live in the
 * {@code world_inventory} table. Both rows are read when the player joins (cache); the swap itself is synchronous in the
 * world-change event (LOWEST, so every other module already sees the new set).</p>
 *
 * <p><b>Crash and dupe safety:</b> the side just left is also kept in the player file ({@code inventory_pending}, with a
 * token) until its row is committed. Order: swap in memory, save the player file, then write the row; a confirmed write
 * removes the pending copy. A crash before the save keeps the old file (nothing changed anywhere); a crash after it keeps
 * the pending copy, which wins over the database on the next load and is written again. A failed write (or a quit before
 * it finished) therefore never loses the side just left. Entering a side clears its pending copy, and a row is only
 * replaced by a newer one ({@code saved_at}), so writes that finish out of order cannot bring back an old set. The live
 * side's row is stale but is only read after the next swap out of that side overwrote it, so no item exists twice.</p>
 *
 * <p><b>Players from the previous release</b> (no marker, no Depths row yet): their first Depths set is built from the Depths
 * tools they carry plus the tools the previous release put away in {@code tool_stash} (a tool id already carried is never
 * given twice); the put-away rows are deleted with the first surface write. They get the Depths kit and only the starter
 * tool types they are missing.</p>
 */
final class WorldTools implements Listener {

    static final String SURFACE = "surface";
    static final String DEPTHS = "depths";

    private static final NamespacedKey SIDE = ItemKeys.key("inventory_side");
    private static final NamespacedKey ACTION = ItemKeys.key("depths_kit_action");
    /** The side just left, until its row is committed: items (bytes) and "{@code <profile>:<side>:<stamp>}". */
    private static final NamespacedKey PENDING_ITEMS = ItemKeys.key("inventory_pending");
    private static final NamespacedKey PENDING_TOKEN = ItemKeys.key("inventory_pending_token");
    private static final long RETRY_TICKS = 200;

    private final ToolsModule module;
    /** Player → profile → side → items (as read on join, updated by every swap). Main thread. */
    private final Map<UUID, Cached> cache = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();
    /** Last {@code saved_at} handed out (main thread): strictly increasing, so a newer write always wins. */
    private long lastStamp;

    /** {@code stash}: Depths tools the previous release put away (moved into the first Depths set). */
    private record Cached(UUID profile, Map<String, byte[]> rows, List<ItemStack> stash) {
    }

    private record Loaded(Map<String, byte[]> rows, List<ItemStack> stash) {
    }

    WorldTools(ToolsModule module) {
        this.module = module;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, module.core.plugin());
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p, true);
        }
    }

    void stop() {
        HandlerList.unregisterAll(this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());
        }
        cache.clear();
        loading.clear();
    }

    /** Pure: true when {@code world} is one of the Depths worlds (ignoring case). */
    static boolean depthsWorld(List<String> depthsWorlds, String world) {
        return world != null && depthsWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    /** Pure: the live side a marker says for {@code profile} (a marker of another profile or none = surface). */
    static String liveSide(String marker, UUID profile) {
        if (marker == null || profile == null) {
            return SURFACE;
        }
        int cut = marker.lastIndexOf(':');
        if (cut <= 0 || !marker.substring(0, cut).equals(profile.toString())) {
            return SURFACE;
        }
        return DEPTHS.equals(marker.substring(cut + 1)) ? DEPTHS : SURFACE;
    }

    /** Pure: one {@code world-inventory.depths-items} entry, "MATERIAL amount action" (action "-" = a plain item). */
    record KitItem(Material material, int amount, String action) {

        static Optional<KitItem> parse(String line) {
            if (line == null) {
                return Optional.empty();
            }
            String[] p = line.trim().split("\\s+");
            if (p.length < 1 || p[0].isEmpty()) {
                return Optional.empty();
            }
            Material m = Material.matchMaterial(p[0]);
            if (m == null || !m.isItem() || m.isAir()) {
                return Optional.empty();
            }
            int amount = 1;
            if (p.length > 1) {
                try {
                    amount = Math.max(1, Math.min(m.getMaxStackSize(), Integer.parseInt(p[1])));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
            String action = p.length > 2 && !p[2].equals("-") ? p[2].toLowerCase(Locale.ROOT) : "";
            return Optional.of(new KitItem(m, amount, action));
        }
    }

    private boolean active(Player player) {
        return module.config().worldToolsEnabled && !player.hasPermission("skyblock.tools.world-bypass");
    }

    private UUID profile(Player player) {
        return ProfileService.of(module.core.services()).profileKey(player);
    }

    private String targetSide(Player player) {
        return depthsWorld(module.config().worldToolsDepthsWorlds, player.getWorld().getName()) ? DEPTHS : SURFACE;
    }

    private String marker(Player player) {
        return player.getPersistentDataContainer().get(SIDE, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorld(PlayerChangedWorldEvent event) {
        place(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // After the profile's inventory is loaded (profiles module) and the join teleports are done.
        module.core.scheduler().later("tools-world-join", 20, () -> {
            if (player.isOnline()) {
                load(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProfile(ProfileSwitchedEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
        load(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cache.remove(id);
        loading.remove(id);
        event.getPlayer().removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());
    }

    /** Depths kit items: a right click runs their action (the satchel, the dungeon map). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String action = kitAction(event.getItem());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.OFF_HAND && kitAction(event.getPlayer().getInventory().getItemInMainHand()) != null) {
            return; // the main hand already ran a kit item this click
        }
        event.getPlayer().performCommand(action);
    }

    /** Kit items stay with their set (a dropped satchel would only be litter). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (kitAction(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    private static String kitAction(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        String a = item.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING);
        return a == null || a.isBlank() ? null : a;
    }

    // ------------------------------------------------------------------ loading

    /** Reads both rows of the player's profile once (plus a pending copy from the player file); then puts the right set in place. */
    private void load(Player player, boolean placeAfter) {
        if (!active(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        UUID profile = profile(player);
        if (!loading.add(id)) {
            return;
        }
        module.core.database().async(c -> rows(c, profile)).whenComplete((loaded, error) -> module.core.scheduler().main("tools-world-load", () -> {
            loading.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return;
            }
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: could not read the world inventories of " + p.getName()
                        + " (no swap until they load; trying again in " + RETRY_TICKS / 20 + " s)", Database.cause(error));
                p.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());
                module.core.scheduler().later("tools-world-retry", RETRY_TICKS, () -> {
                    Player again = Bukkit.getPlayer(id);
                    if (again != null && !cache.containsKey(id)) {
                        load(again, true);
                    }
                });
                return;
            }
            if (!profile.equals(profile(p))) {
                return; // switched profile meanwhile; that switch started its own load
            }
            Map<String, byte[]> rows = loaded.rows();
            Pending pending = pending(p, profile);
            if (pending != null) {
                // The last swap's write was not confirmed: the copy in the player file is the newest; write it again.
                rows.put(pending.side(), pending.items());
                lastStamp = Math.max(lastStamp, pending.stamp());
                write(p, profile, pending.side(), pending.items(), pending.stamp(), pending.token());
            }
            cache.put(id, new Cached(profile, rows, loaded.stash()));
            if (placeAfter) {
                place(p);
            }
        }));
    }

    private static Loaded rows(Connection c, UUID profile) throws SQLException {
        Map<String, byte[]> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT side, items FROM world_inventory WHERE profile_key = ?",
                rs -> new Object[]{rs.getString("side"), rs.getBytes("items")}, profile)) {
            out.put((String) r[0], (byte[]) r[1]);
        }
        List<ItemStack> stash = new ArrayList<>();
        if (out.get(DEPTHS) == null) {
            // Previous release: tools put away outside the Depths (V002). Only needed until the first Depths set exists.
            for (byte[] b : Sql.query(c, "SELECT item FROM tool_stash WHERE profile_key = ? ORDER BY stashed_at",
                    rs -> rs.getBytes("item"), profile)) {
                stash.add(ItemStack.deserializeBytes(b));
            }
        }
        return new Loaded(out, stash);
    }

    private record Pending(UUID profile, String side, byte[] items, long stamp, String token) {
    }

    /** The pending copy in the player file for {@code profile}, or null. */
    private static Pending pending(Player player, UUID profile) {
        Pending any = pendingAny(player);
        return any != null && any.profile().equals(profile) ? any : null;
    }

    /** The pending copy in the player file, of any profile (the container is shared by the profiles), or null. */
    private static Pending pendingAny(Player player) {
        var pdc = player.getPersistentDataContainer();
        String token = pdc.get(PENDING_TOKEN, PersistentDataType.STRING);
        byte[] items = pdc.get(PENDING_ITEMS, PersistentDataType.BYTE_ARRAY);
        if (token == null || items == null) {
            return null;
        }
        String[] t = token.split(":");
        if (t.length != 3 || !(SURFACE.equals(t[1]) || DEPTHS.equals(t[1]))) {
            return null;
        }
        try {
            return new Pending(UUID.fromString(t[0]), t[1], items, Long.parseLong(t[2]), token);
        } catch (IllegalArgumentException e) { // also NumberFormatException
            return null;
        }
    }

    private static void clearPending(Player player) {
        player.getPersistentDataContainer().remove(PENDING_ITEMS);
        player.getPersistentDataContainer().remove(PENDING_TOKEN);
    }

    // ------------------------------------------------------------------ the swap

    /** Puts the set of the player's current world in place. Main thread. */
    void place(Player player) {
        if (!active(player)) {
            player.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());
            return;
        }
        UUID profile = profile(player);
        String target = targetSide(player);
        String live = liveSide(marker(player), profile);
        if (live.equals(target)) {
            player.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());
            return;
        }
        Cached cached = cache.get(player.getUniqueId());
        if (cached == null || !cached.profile().equals(profile)) {
            // Not read yet: the swap happens as soon as the rows are in; until then other modules wait (PENDING).
            player.setMetadata(InventorySwappedEvent.PENDING, new FixedMetadataValue(module.core.plugin(), true));
            load(player, true);
            return;
        }
        swap(player, profile, cached, live, target);
    }

    private void swap(Player player, UUID profile, Cached cached, String live, String target) {
        player.closeInventory(); // the cursor item goes back into the inventory first
        PlayerInventory inv = player.getInventory();
        ItemStack[] leaving = inv.getContents();
        byte[] in = cached.rows().get(target);
        boolean fresh = in == null;

        // First Depths set: the Depths tools the player carries (previous release) and the ones it put away come along.
        List<ItemStack> carriedOver = new ArrayList<>();
        if (fresh && DEPTHS.equals(target)) {
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < leaving.length; i++) {
                ItemStack s = leaving[i];
                var data = ToolItems.read(s);
                if (data.isPresent()) {
                    if (ids.add(data.get().id())) {
                        carriedOver.add(s);
                    }
                    leaving[i] = null;
                }
            }
            for (ItemStack s : cached.stash()) {
                var data = ToolItems.read(s);
                if (data.isPresent() && ids.add(data.get().id())) {
                    carriedOver.add(s);
                }
            }
        }
        byte[] out = ItemStack.serializeItemsAsBytes(nonNull(leaving));
        long stamp = Math.max(System.currentTimeMillis(), lastStamp + 1);
        lastStamp = stamp;
        String token = profile + ":" + live + ":" + stamp;

        inv.clear();
        player.setItemOnCursor(null);
        if (!fresh) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(in);
            for (int i = 0; i < items.length && i < inv.getSize(); i++) {
                ItemStack s = items[i];
                inv.setItem(i, s == null || s.isEmpty() ? null : s);
            }
        }
        for (ItemStack s : carriedOver) {
            module.forge().give(player, s);
        }
        Pending other = pendingAny(player);
        if (other != null && !other.profile().equals(profile)) {
            // Another profile's unconfirmed write is about to be replaced: send it again first (it is still the newest).
            write(player, other.profile(), other.side(), other.items(), other.stamp(), other.token());
        }
        var pdc = player.getPersistentDataContainer();
        pdc.set(SIDE, PersistentDataType.STRING, profile + ":" + target);
        // The side just left stays in the player file until its row is committed (replaces any older pending copy:
        // that one was of the side we are entering, which is live again now).
        pdc.set(PENDING_ITEMS, PersistentDataType.BYTE_ARRAY, out);
        pdc.set(PENDING_TOKEN, PersistentDataType.STRING, token);
        cached.rows().put(live, out);
        if (DEPTHS.equals(target)) {
            cached.stash().clear();
        }
        player.updateInventory();
        player.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());

        UUID id = player.getUniqueId();
        String name = player.getName();
        module.core.audit().logPlayer(AuditCategory.ITEM, "inventory_swap", Actor.SYSTEM, id, name, (long) countItems(leaving),
                null, Map.of("profile", profile.toString(), "from", live, "to", target, "fresh", String.valueOf(fresh),
                        "carried_over", String.valueOf(carriedOver.size())));

        if (fresh && DEPTHS.equals(target)) {
            giveDepthsKit(player);
        }
        // Player file first (marker, new set and the pending copy), the database second (PlayerSaves rule).
        if (PlayerSaves.save(player, "world inventory swap")) {
            write(player, profile, live, out, stamp, token);
        } else {
            module.core.plugin().getLogger().severe("Tools: " + name + "'s player file was not saved after the inventory swap;"
                    + " the " + live + " set stays in it and is written on the next load");
        }
        module.msg().actionBar(player, DEPTHS.equals(target) ? "world-inventory.to-depths" : "world-inventory.to-surface");
        Bukkit.getPluginManager().callEvent(new InventorySwappedEvent(player, DEPTHS.equals(target)));
    }

    /**
     * Writes one side's row unless a newer one is already there. A surface row is only written when the player enters
     * the Depths, when the put-away tools of the previous release are already in the Depths set, so those rows go too.
     * On success the pending copy with {@code token} is removed from the player file.
     */
    private void write(Player player, UUID profile, String side, byte[] items, long stamp, String token) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        module.core.database().transaction(c -> {
            boolean newer = !Sql.query(c, "SELECT saved_at FROM world_inventory WHERE profile_key = ? AND side = ? AND saved_at > ?",
                    rs -> rs.getLong("saved_at"), profile, side, stamp).isEmpty();
            if (!newer) {
                Sql.update(c, "DELETE FROM world_inventory WHERE profile_key = ? AND side = ?", profile, side);
                Sql.update(c, "INSERT INTO world_inventory (profile_key, side, items, saved_at) VALUES (?, ?, ?, ?)", profile, side, items, stamp);
            }
            if (SURFACE.equals(side)) {
                Sql.update(c, "DELETE FROM tool_stash WHERE profile_key = ?", profile);
            }
            return null;
        }).whenComplete((ok, error) -> module.core.scheduler().main("tools-world-saved", () -> {
            Player p = Bukkit.getPlayer(id);
            if (error != null) {
                module.core.plugin().getLogger().log(Level.SEVERE, "Tools: could not save the " + side + " inventory of " + name
                        + " (kept in the player file; written again on the next load)", Database.cause(error));
                return;
            }
            if (p != null && token.equals(p.getPersistentDataContainer().get(PENDING_TOKEN, PersistentDataType.STRING))) {
                clearPending(p); // persisted by the next save (a leftover copy is harmless: it equals the row)
            }
        }));
    }

    /** First visit: the Depths kit (satchel, dungeon map, torches) and the starter tool types still missing (once per profile). */
    private void giveDepthsKit(Player player) {
        for (String line : module.config().worldInventoryDepthsItems) {
            KitItem.parse(line).ifPresentOrElse(k -> module.forge().give(player, kitItem(player, k)),
                    () -> module.core.plugin().getLogger().warning("Tools: bad world-inventory.depths-items entry '" + line + "'"));
        }
        if (module.config().worldToolsAutoStarter) {
            module.forge().giveDepthsStarter(player);
        }
    }

    private ItemStack kitItem(Player player, KitItem k) {
        ItemStack item = new ItemStack(k.material(), k.amount());
        if (k.action().isEmpty()) {
            return item;
        }
        String key = "world-inventory.item." + k.action();
        item.editMeta(meta -> {
            if (module.msg().has(key + ".name")) {
                meta.displayName(module.msg().component(player, key + ".name").decoration(TextDecoration.ITALIC, false));
            }
            if (module.msg().has(key + ".lore")) {
                List<Component> lore = new ArrayList<>();
                lore.add(module.msg().component(player, key + ".lore").decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
            }
            meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, k.action());
        });
        return item;
    }

    private static ItemStack[] nonNull(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? ItemStack.empty() : items[i];
        }
        return out;
    }

    private static int countItems(ItemStack[] items) {
        int n = 0;
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
