package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.DupeDetector;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wardrobe Tomes (WARDROBE §3.1, WD13): unique, database-backed consumables (crates, quests, events, staff; never sold
 * for credits). Used on a set in its set screen: +2,500 set XP (greater: +10,000), never past level 40. The database
 * decides first while the inventory is locked; the item is taken after the commit. A tome item whose row is used up is a
 * copy and is taken (staff told). Every change is audited.
 */
final class Tomes {

    static final String NORMAL = "normal";
    static final String GREATER = "greater";

    private final WardrobeModule module;

    Tomes(WardrobeModule module) {
        this.module = module;
    }

    /** XP of a tome kind from wardrobe.yml. */
    long xpOf(String kind) {
        return GREATER.equals(kind) ? module.config().greaterTomeXp : module.config().tomeXp;
    }

    static String kind(String raw) {
        return GREATER.equalsIgnoreCase(raw) ? GREATER : NORMAL;
    }

    /** First inventory slot holding a tome, or -1. */
    static int tomeSlot(PlayerInventory inv) {
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (WardrobeItems.isTome(storage[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Makes a tome: the item is created (unique id), its row written, then the future completes on the main thread. A
     * failed write completes exceptionally and the item is never handed out.
     */
    CompletableFuture<ItemStack> create(String rawKind, String origin, UUID owner, Actor actor) {
        String kind = kind(rawKind);
        long xp = xpOf(kind);
        ItemStack item = module.items().tome(kind, xp, origin);
        UUID id = ItemRegistry.uuidOf(item).orElseThrow();
        CompletableFuture<ItemStack> out = new CompletableFuture<>();
        module.ops().mintTome(id, kind, xp, origin, owner).thenAccept(ok -> module.core.scheduler().main("wardrobe-tome", () -> {
            if (!ok) {
                out.completeExceptionally(new IllegalStateException("Could not save the tome"));
                return;
            }
            module.core.audit().log(AuditCategory.ITEM, "wardrobe_tome_created", actor, id.toString(), kind, xp, null,
                    Map.of("kind", kind, "xp", xp, "origin", origin, "owner", owner == null ? "" : owner.toString()));
            out.complete(item);
        }));
        return out;
    }

    /** Uses the first tome in the inventory on an entry (after the confirm dialog). */
    void use(Player player, UUID entryId) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded) {
            module.fail(player, "loading");
            return;
        }
        if (s.busy || module.equipment().profiles().isSwitching(player.getUniqueId())) {
            module.fail(player, "busy");
            return;
        }
        Optional<WardrobeStore.Entry> entry = s.entry(entryId);
        if (entry.isEmpty()) {
            module.fail(player, "not-owned");
            return;
        }
        SetLevels levels = module.config().levels();
        if (!module.config().levelsEnabled || !levels.tomeUsable(entry.get().xp(), module.config().tomeMaxLevel, entry.get().isMerged())) {
            module.fail(player, "tome-capped", Msg.p("level", module.config().tomeMaxLevel));
            return;
        }
        int slot = tomeSlot(player.getInventory());
        ItemStack item = slot < 0 ? null : player.getInventory().getItem(slot);
        Optional<UUID> tomeId = ItemRegistry.uuidOf(item);
        if (tomeId.isEmpty()) {
            module.fail(player, "tome-gone");
            return;
        }
        s.busy = true;
        UUID profile = s.profile;
        UUID id = tomeId.get();
        double pending = s.pendingXp.getOrDefault(entryId, 0.0);
        s.pendingXp.remove(entryId);
        WardrobeOps.Who who = new WardrobeOps.Who(player.getUniqueId(), player.getName(), profile);
        int before = entry.get().level();
        module.ops().useTome(who, id, entryId, pending, levels, module.config().tomeMaxLevel).thenAccept(r -> module.core.scheduler().main("wardrobe-tome-use", () -> {
            s.busy = false;
            if (!r.ok() && pending > 0) {
                s.pendingXp.merge(entryId, pending, Double::sum); // rolled back: save it with the next flush
            }
            if (r.ok()) {
                module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_tome_used", who.actor(), who.player(), who.name(), null, null,
                        Map.of("tome", id.toString(), "entry", entryId.toString(), "set", entry.get().setId(), "from_level", before,
                                "to_level", r.entry().level(), "xp", r.entry().xp(), "profile", profile.toString()));
            }
            Player p = Bukkit.getPlayer(who.player());
            if (p == null) {
                return;
            }
            switch (r.status()) {
                case OK -> {
                    Shop.removeItem(p.getInventory(), id);
                    if (module.equipment().sessionOrNull(p.getUniqueId()) == s && s.profile.equals(profile)) {
                        // The row now holds the saved total; XP earned meanwhile is still pending on top of it.
                        double extra = s.pendingXp.getOrDefault(entryId, 0.0);
                        double xp = levels.clamp(r.entry().xp() + extra, r.entry().isMerged());
                        s.replace(r.entry().withXp(xp, levels.level(xp, r.entry().isMerged())));
                        module.leveling().afterChange(p, s, entryId, before);
                    }
                    module.msg().send(p, "tome-used", Msg.p("set", module.text().entryName(module.registry(), r.entry())),
                            Msg.p("level", r.entry().level()));
                    module.core.sounds().play(p, "enchant");
                }
                case TOKEN_INVALID -> confiscate(p, id, "use");
                case TOME_CAPPED -> module.fail(p, "tome-capped", Msg.p("level", module.config().tomeMaxLevel));
                case GONE -> module.fail(p, "not-owned");
                default -> module.fail(p, "error");
            }
            module.menus().refreshOpen(p);
        }));
    }

    /** A copied tome was used: it is taken, staff are told, audited. */
    private void confiscate(Player player, UUID tomeId, String where) {
        Shop.removeItem(player.getInventory(), tomeId);
        module.fail(player, "tome-copy");
        module.core.audit().logPlayer(AuditCategory.ITEM, "wardrobe_tome_confiscated", Actor.system("wardrobe"), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("tome", tomeId.toString(), "where", where));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(DupeDetector.PERM_NOTIFY)) {
                module.msg().send(staff, "staff-tome-copy", Msg.p("player", player.getName()), Msg.p("id", tomeId.toString()));
            }
        }
    }

    /** On join: tomes in the inventory whose row is not active any more are copies and are taken. */
    void clean(Player player) {
        Set<UUID> tomes = new LinkedHashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (WardrobeItems.isTome(item)) {
                ItemRegistry.uuidOf(item).ifPresent(tomes::add);
            }
        }
        if (tomes.isEmpty()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        module.core.database().async(c -> {
            Map<UUID, Boolean> dead = new LinkedHashMap<>();
            for (UUID id : tomes) {
                Optional<WardrobeStore.Tome> row = module.store().tome(c, id, false);
                if (row.isEmpty() || row.get().state() != WardrobeStore.TokenState.ACTIVE) {
                    dead.put(id, true);
                }
            }
            return dead.keySet();
        }).whenComplete((dead, error) -> module.core.scheduler().main("wardrobe-tomes", () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || error != null || dead.isEmpty()) {
                return;
            }
            for (UUID id : dead) {
                confiscate(p, id, "join");
            }
        }));
    }
}
