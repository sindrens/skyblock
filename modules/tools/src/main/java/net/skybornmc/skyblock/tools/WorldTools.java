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
 * world-change event (LOWEST, so every other module already sees the new set), and the side just left is written after.</p>
 *
 * <p><b>Dupe safety:</b> the row of the live side is stale but is only ever read after it was overwritten by the next swap
 * out of that side, so no item exists twice. A crash between the swap and the write can only roll back the side just
 * left to its previous row (never a copy). The player file is saved once the write is done.</p>
 */
final class WorldTools implements Listener {

    static final String SURFACE = "surface";
    static final String DEPTHS = "depths";

    private static final NamespacedKey SIDE = ItemKeys.key("inventory_side");
    private static final NamespacedKey ACTION = ItemKeys.key("depths_kit_action");

    private final ToolsModule module;
    /** Player → profile → side → items (as read on join, updated by every swap). Main thread. */
    private final Map<UUID, Cached> cache = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();

    private record Cached(UUID profile, Map<String, byte[]> rows) {
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

    /** Reads both rows of the player's profile once; then puts the right set in place. */
    private void load(Player player, boolean placeAfter) {
        if (!active(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        UUID profile = profile(player);
        if (!loading.add(id)) {
            return;
        }
        module.core.database().async(c -> rows(c, profile)).whenComplete((rows, error) -> module.core.scheduler().main("tools-world-load", () -> {
            loading.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                return;
            }
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: could not read the world inventories of " + p.getName()
                        + " (no swap until they load)", Database.cause(error));
                return;
            }
            if (!profile.equals(profile(p))) {
                return; // switched profile meanwhile; that switch started its own load
            }
            cache.put(id, new Cached(profile, rows));
            if (placeAfter) {
                place(p);
            }
        }));
    }

    private static Map<String, byte[]> rows(Connection c, UUID profile) throws SQLException {
        Map<String, byte[]> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT side, items FROM world_inventory WHERE profile_key = ?",
                rs -> new Object[]{rs.getString("side"), rs.getBytes("items")}, profile)) {
            out.put((String) r[0], (byte[]) r[1]);
        }
        return out;
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
        byte[] out = ItemStack.serializeItemsAsBytes(nonNull(leaving));
        byte[] in = cached.rows().get(target);
        boolean fresh = in == null;

        inv.clear();
        player.setItemOnCursor(null);
        if (!fresh) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(in);
            for (int i = 0; i < items.length && i < inv.getSize(); i++) {
                ItemStack s = items[i];
                inv.setItem(i, s == null || s.isEmpty() ? null : s);
            }
        }
        player.getPersistentDataContainer().set(SIDE, PersistentDataType.STRING, profile + ":" + target);
        cached.rows().put(live, out);
        player.updateInventory();
        player.removeMetadata(InventorySwappedEvent.PENDING, module.core.plugin());

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        String name = player.getName();
        module.core.database().transaction(c -> {
            Sql.update(c, "DELETE FROM world_inventory WHERE profile_key = ? AND side = ?", profile, live);
            Sql.update(c, "INSERT INTO world_inventory (profile_key, side, items, saved_at) VALUES (?, ?, ?, ?)", profile, live, out, now);
            return null;
        }).whenComplete((ok, error) -> module.core.scheduler().main("tools-world-saved", () -> {
            if (error != null) {
                module.core.plugin().getLogger().log(Level.SEVERE, "Tools: could not save the " + live + " inventory of " + name
                        + " (kept in memory; written again on the next swap)", Database.cause(error));
                return;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                PlayerSaves.save(p, "world inventory swap");
            }
        }));
        module.core.audit().logPlayer(AuditCategory.ITEM, "inventory_swap", Actor.SYSTEM, id, name, (long) countItems(leaving),
                null, Map.of("profile", profile.toString(), "from", live, "to", target, "fresh", String.valueOf(fresh)));

        if (fresh && DEPTHS.equals(target)) {
            giveDepthsKit(player);
        }
        module.msg().actionBar(player, DEPTHS.equals(target) ? "world-inventory.to-depths" : "world-inventory.to-surface");
        Bukkit.getPluginManager().callEvent(new InventorySwappedEvent(player, DEPTHS.equals(target)));
    }

    /** First visit: the Depths kit (satchel, dungeon map, torches) and the starter tools (once per profile). */
    private void giveDepthsKit(Player player) {
        for (String line : module.config().worldInventoryDepthsItems) {
            KitItem.parse(line).ifPresentOrElse(k -> module.forge().give(player, kitItem(player, k)),
                    () -> module.core.plugin().getLogger().warning("Tools: bad world-inventory.depths-items entry '" + line + "'"));
        }
        if (module.config().worldToolsAutoStarter) {
            module.forge().claimStarter(player);
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
