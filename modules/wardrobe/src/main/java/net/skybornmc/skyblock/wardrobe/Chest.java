package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Wardrobe Chest (WARDROBE §4.4, WD21): unique, tracked items only, per profile, with the auction house's escrow
 * rules. The database always decides first while the player's inventory is locked:
 * <ul>
 *   <li><b>Store</b>: insert the row ({@code stored}) → then take the item out of the inventory. A crash in between
 *   leaves the item in both places; the join recovery removes the inventory copy (the stored copy wins).</li>
 *   <li><b>Take</b>: {@code stored → releasing} (plan id) → give the item → {@code released}. A crash in between leaves a
 *   {@code releasing} row; the join recovery gives the item again unless it is already in the inventory.</li>
 * </ul>
 * Item ids are unique, so a double is always visible.
 */
final class Chest {

    static final String PERM_STORAGE_PREFIX = "skyblock.wardrobe.storage.";
    static final int MAX_GRANTS = 3;

    private final WardrobeModule module;

    Chest(WardrobeModule module) {
        this.module = module;
    }

    /** Chest size of this player: base + per-grant × the highest storage grant (1-3). */
    int capacity(Player player) {
        int grants = 0;
        for (int n = MAX_GRANTS; n >= 1; n--) {
            if (player.hasPermission(PERM_STORAGE_PREFIX + n)) {
                grants = n;
                break;
            }
        }
        return Math.max(0, module.config().storageSize + grants * module.config().storagePerGrant);
    }

    /** Whether an item may go in the chest: a unique custom item of an allowed type or id (never set pieces). */
    boolean storable(ItemStack item) {
        if (item == null || item.isEmpty() || WardrobeItems.isPiece(item) || ItemRegistry.uuidOf(item).isEmpty()) {
            return false;
        }
        Optional<ItemDefinition> def = module.core.items().definitionOf(item);
        return def.isPresent() && def.get().unique() && module.config().storable(def.get().type().name(), def.get().id());
    }

    /** Inventory slots (0-35) holding storable items. */
    List<Integer> storableSlots(Player player) {
        List<Integer> out = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (storable(contents[i])) {
                out.add(i);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ store

    void store(Player player, int slot) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!ready(player, s)) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (!storable(item)) {
            module.fail(player, "chest.not-storable");
            return;
        }
        UUID id = ItemRegistry.uuidOf(item).orElseThrow();
        ItemDefinition def = module.core.items().definitionOf(item).orElseThrow();
        String name = PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
        UUID profile = s.profile;
        WardrobeStore.Stored row = new WardrobeStore.Stored(id, profile, def.type().id(), def.id(), name, item.serializeAsBytes(),
                WardrobeStore.StorageState.STORED, null, System.currentTimeMillis());
        s.busy = true;
        UUID uuid = player.getUniqueId();
        module.ops().store(row, capacity(player)).thenAccept(r -> module.core.scheduler().main("wardrobe-store", () -> {
            s.busy = false;
            Player p = Bukkit.getPlayer(uuid);
            if (r.ok() || r.status() == WardrobeOps.Status.STALE) {
                boolean removed = p != null && Shop.removeItem(p.getInventory(), id);
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("item", id.toString());
                details.put("item_id", def.id());
                details.put("name", name);
                details.put("profile", profile.toString());
                details.put("removed", removed);
                module.core.audit().logPlayer(AuditCategory.ITEM, r.ok() ? "wardrobe_store" : "wardrobe_stale_copy",
                        p == null ? Actor.system("wardrobe") : Actor.player(p), uuid, p == null ? null : p.getName(), null, null, details);
            }
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    module.msg().send(p, "chest.stored", Msg.p("item", name));
                    module.core.sounds().play(p, "click");
                }
                case FULL -> module.fail(p, "chest.full", Msg.p("size", capacity(p)));
                case STALE -> module.fail(p, "chest.stale");
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    // ------------------------------------------------------------------ take

    void take(Player player, UUID itemId) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (!ready(player, s)) {
            return;
        }
        if (Equipment.freeSlots(player.getInventory()) < 1) {
            module.fail(player, "chest.inventory-full");
            return;
        }
        s.busy = true;
        UUID profile = s.profile;
        UUID plan = UUID.randomUUID();
        UUID uuid = player.getUniqueId();
        module.ops().take(itemId, profile, plan).thenAccept(r -> module.core.scheduler().main("wardrobe-take", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (!r.ok()) {
                s.busy = false;
                if (p != null) {
                    module.fail(p, r.status() == WardrobeOps.Status.GONE ? "chest.gone" : "error");
                    module.menus().refreshOpen(p);
                }
                return;
            }
            if (p == null || module.equipment().sessionOrNull(uuid) != s || !module.equipment().profiles().profileKey(p).equals(profile)) {
                s.busy = false;
                return; // the join recovery hands it out
            }
            ItemStack item;
            try {
                item = ItemStack.deserializeBytes(r.stored().item());
            } catch (RuntimeException e) {
                s.busy = false;
                module.core.plugin().getLogger().log(Level.SEVERE, "Wardrobe: stored item " + itemId + " can't be read; back in the chest", e);
                module.ops().backToStored(itemId, plan);
                module.fail(p, "error");
                return;
            }
            int free = p.getInventory().firstEmpty();
            if (free < 0 || free >= p.getInventory().getStorageContents().length) {
                s.busy = false;
                module.ops().backToStored(itemId, plan);
                module.fail(p, "chest.inventory-full");
                return;
            }
            p.getInventory().setItem(free, item);
            module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_take", Actor.player(p), uuid, p.getName(), null, null,
                    Map.of("item", itemId.toString(), "item_id", r.stored().itemId(), "name", r.stored().itemName(), "profile", profile.toString()));
            module.msg().send(p, "chest.taken", Msg.p("item", r.stored().itemName()));
            module.core.sounds().play(p, "click");
            // Stay locked until the row says released, so the item can't be stored again while it still says releasing.
            module.ops().released(itemId, plan).whenComplete((ok, error) -> module.core.scheduler().main("wardrobe-take-done", () -> {
                s.busy = false;
                Player again = Bukkit.getPlayer(uuid);
                if (again != null) {
                    module.menus().refreshOpen(again);
                }
            }));
        }));
    }

    // ------------------------------------------------------------------ recovery (join, profile load)

    /** Gives back items left "releasing" by a crash and removes inventory copies of items that are in the chest. */
    void recover(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (storable(item)) {
                ids.add(ItemRegistry.uuidOf(item).orElseThrow());
            }
        }
        UUID profile = s.profile;
        UUID uuid = player.getUniqueId();
        module.ops().recovery(profile, ids).whenComplete((rec, error) -> module.core.scheduler().main("wardrobe-recover", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || error != null || module.equipment().sessionOrNull(uuid) != s || !s.profile.equals(profile)) {
                if (error != null) {
                    module.core.plugin().getLogger().log(Level.WARNING, "Wardrobe: chest recovery failed", error);
                }
                return;
            }
            for (UUID stale : rec.stale()) {
                if (Shop.removeItem(p.getInventory(), stale)) {
                    module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_stale_copy", Actor.system("wardrobe"), uuid, p.getName(), null,
                            null, Map.of("item", stale.toString(), "profile", profile.toString(), "where", "join"));
                }
            }
            int given = 0;
            int kept = 0;
            for (WardrobeStore.Stored row : rec.releasing()) {
                if (inInventory(p.getInventory(), row.itemUuid())) {
                    module.ops().released(row.itemUuid(), row.planId());
                    continue;
                }
                int free = p.getInventory().firstEmpty();
                ItemStack item = null;
                try {
                    item = ItemStack.deserializeBytes(row.item());
                } catch (RuntimeException e) {
                    module.core.plugin().getLogger().log(Level.SEVERE, "Wardrobe: stored item " + row.itemUuid() + " can't be read", e);
                }
                if (item == null || free < 0 || free >= p.getInventory().getStorageContents().length) {
                    module.ops().backToStored(row.itemUuid(), row.planId());
                    kept++;
                    continue;
                }
                p.getInventory().setItem(free, item);
                module.ops().released(row.itemUuid(), row.planId());
                given++;
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_take_recovered", Actor.system("wardrobe"), uuid, p.getName(), null,
                        null, Map.of("item", row.itemUuid().toString(), "item_id", row.itemId(), "profile", profile.toString()));
            }
            if (given > 0) {
                module.msg().send(p, "chest.recovered", Msg.p("count", given));
            }
            if (kept > 0) {
                module.msg().send(p, "chest.kept", Msg.p("count", kept));
            }
        }));
    }

    private static boolean inInventory(PlayerInventory inv, UUID id) {
        for (ItemStack item : inv.getContents()) {
            if (ItemRegistry.uuidOf(item).filter(id::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean ready(Player player, Session s) {
        String problem = module.equipment().problem(player, s);
        if ("blocked-world".equals(problem)) {
            problem = null; // the chest works everywhere
        }
        if (problem != null) {
            module.fail(player, problem);
            return false;
        }
        return true;
    }
}
