package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The storages of the island tools, per profile: the hoe storage (T9) and, owner 4 Nov 2026, the pickaxe storage. Loot
 * goes to the inventory first and only lands here when the inventory is full. Rows of {@code island_tool_storage}: the
 * hoe's items under their plain id ({@code wheat}, as before), the pickaxe's as {@code pickaxe:raw_iron}.
 * Harvests land in memory and are written every 10 s (write-behind, additive, so a crash loses at most a few seconds of
 * loot, never duplicates it). Taking out or selling first writes the pending amounts, then takes from the database row in
 * the same transaction as the hand-out or the sale, so nothing is paid or given twice.
 */
final class HoeStorage {

    /** One profile's storages: known amounts plus amounts not written yet (keys as stored). */
    private static final class Box {
        final Map<String, Long> amounts = new ConcurrentHashMap<>();
        final Map<String, Long> pending = new ConcurrentHashMap<>();
        volatile boolean loaded;
    }

    private final IslandToolsModule m;
    private final Map<UUID, Box> boxes = new ConcurrentHashMap<>();
    /** Players with a withdraw or sale in flight (one at a time: no double hand-out). */
    private final java.util.Set<UUID> busy = ConcurrentHashMap.newKeySet();

    HoeStorage(IslandToolsModule m) {
        this.m = m;
    }

    /** The stored key of an item in a tool's storage (pure). */
    static String key(Kind kind, String item) {
        return kind == Kind.HOE ? item : kind.id() + ":" + item;
    }

    /** The item id of a stored key when it belongs to {@code kind}'s storage, else null (pure). */
    static String itemOf(Kind kind, String key) {
        int colon = key.indexOf(':');
        if (kind == Kind.HOE) {
            return colon < 0 ? key : null;
        }
        String prefix = kind.id() + ":";
        return key.startsWith(prefix) ? key.substring(prefix.length()) : null;
    }

    void load(UUID profile) {
        Box box = boxes.computeIfAbsent(profile, k -> new Box());
        if (box.loaded) {
            return;
        }
        m.store().storage(profile).whenComplete((map, e) -> {
            if (e != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Island tools: could not read the tool storage of " + profile, e);
                return;
            }
            if (!box.loaded) {
                map.forEach((item, n) -> box.amounts.merge(item, n, Long::sum));
                box.loaded = true;
            }
        });
    }

    void unload(UUID profile) {
        Box box = boxes.remove(profile);
        if (box != null) {
            flush(profile, box);
        }
    }

    long capacity(Player p, Kind kind) {
        return ToolRules.storage(m.effect(p, kind, Upgrades.Effect.STORAGE), m.config().storageBase, m.config().storagePerLevel);
    }

    long used(UUID profile, Kind kind) {
        Box box = boxes.get(profile);
        if (box == null) {
            return 0;
        }
        long sum = 0;
        for (Map.Entry<String, Long> e : box.amounts.entrySet()) {
            if (itemOf(kind, e.getKey()) != null) {
                sum += Math.max(0, e.getValue());
            }
        }
        return sum;
    }

    /** item id → amount of one tool's storage. */
    Map<String, Long> contents(UUID profile, Kind kind) {
        Box box = boxes.get(profile);
        Map<String, Long> out = new HashMap<>();
        if (box != null) {
            box.amounts.forEach((k, v) -> {
                String item = itemOf(kind, k);
                if (item != null && v > 0) {
                    out.put(item, v);
                }
            });
        }
        return out;
    }

    /** Stores what fits; returns how many of {@code amount} were stored. Main thread. */
    long add(Player p, Kind kind, String item, long amount) {
        return add(p, kind, item, amount, false);
    }

    /** {@link #add(Player, Kind, String, long)}; with {@code compact} every 9 of a compactable item become a block (Auto-Compactor). */
    long add(Player p, Kind kind, String item, long amount, boolean compact) {
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        if (box == null || !box.loaded || amount <= 0) {
            return 0;
        }
        long room = Math.max(0, capacity(p, kind) - used(profile, kind));
        long take = Math.min(room, amount);
        if (take > 0) {
            String k = key(kind, item);
            box.amounts.merge(k, take, Long::sum);
            box.pending.merge(k, take, Long::sum);
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(item);
            org.bukkit.Material block = mat == null ? null : net.skybornmc.skyblock.common.sell.Compact.BLOCK_OF.get(mat);
            long blocks = compact && block != null ? box.amounts.getOrDefault(k, 0L) / net.skybornmc.skyblock.common.sell.Compact.SIZE : 0;
            if (blocks > 0) {
                long used = blocks * net.skybornmc.skyblock.common.sell.Compact.SIZE;
                box.amounts.merge(k, -used, Long::sum);
                box.pending.merge(k, -used, Long::sum);
                String kb = key(kind, block.getKey().getKey());
                box.amounts.merge(kb, blocks, Long::sum);
                box.pending.merge(kb, blocks, Long::sum);
            }
        }
        return take;
    }

    void flushAll() {
        boxes.forEach(this::flush);
    }

    void flushAllBlocking() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        boxes.forEach((profile, box) -> all.add(flush(profile, box)));
        try {
            CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            m.core().plugin().getLogger().warning("Island tools: tool storage writes did not finish in time: " + e.getMessage());
        }
    }

    private CompletableFuture<Void> flush(UUID profile, Box box) {
        if (box.pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Long> batch = new HashMap<>();
        for (String k : List.copyOf(box.pending.keySet())) {
            Long v = box.pending.remove(k);
            if (v != null && v > 0) {
                batch.merge(k, v, Long::sum);
            }
        }
        return m.store().add(profile, batch).exceptionally(e -> {
            batch.forEach((k, v) -> box.pending.merge(k, v, Long::sum)); // try again next time
            return null;
        });
    }

    /** How many of {@code mat} fit in the player's inventory (storage slots only). */
    static int room(Player p, Material mat) {
        int room = 0;
        ItemStack[] slots = p.getInventory().getStorageContents();
        for (ItemStack s : slots) {
            if (s == null || s.isEmpty()) {
                room += mat.getMaxStackSize();
            } else if (s.getType() == mat && !s.hasItemMeta()) {
                room += Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
        }
        return room;
    }

    /**
     * Takes items out into the inventory: one stack, or ({@code all}) as much as fits. Never more than fits (nothing goes
     * to the ground or /box), one take at a time per player.
     */
    void withdraw(Player p, Kind kind, String item, boolean all, Consumer<Boolean> done) {
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        Material mat = Material.matchMaterial(item);
        String k = key(kind, item);
        long have = box == null ? 0 : box.amounts.getOrDefault(k, 0L);
        int fits = mat == null ? 0 : room(p, mat);
        long n = mat == null ? 0 : Math.min(have, Math.min(fits, all ? Integer.MAX_VALUE : mat.getMaxStackSize()));
        if (box == null || mat == null || n <= 0 || !busy.add(p.getUniqueId())) {
            if (mat != null && have > 0 && fits <= 0) {
                m.msg().send(p, "storage.inventory-full");
            }
            done.accept(false);
            return;
        }
        box.amounts.merge(k, -n, Long::sum);
        flush(profile, box).thenCompose(v -> m.core().database().transaction(c -> ToolStore.take(c, profile, k, n)))
                .whenComplete((ok, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                    busy.remove(p.getUniqueId());
                    if (e != null || !Boolean.TRUE.equals(ok)) {
                        box.amounts.merge(k, n, Long::sum);
                        done.accept(false);
                        return;
                    }
                    List<ItemStack> stacks = new ArrayList<>();
                    long left = n;
                    while (left > 0) {
                        int s = (int) Math.min(left, mat.getMaxStackSize());
                        stacks.add(new ItemStack(mat, s));
                        left -= s;
                    }
                    m.giveAll(p, stacks); // what no longer fits (the bag changed meanwhile) goes to /box, never lost
                    done.accept(true);
                }));
    }

    /** Sells everything sellable in one tool's storage in one sale (the rows are taken in the sale's transaction). */
    void sellAll(Player p, Kind kind, Consumer<SellResult> done) {
        SellService sell = m.core().services().optional(SellService.class).orElse(null);
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        if (sell == null || box == null || !busy.add(p.getUniqueId())) {
            done.accept(null);
            return;
        }
        Map<String, Long> snapshot = new HashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        box.amounts.forEach((k, n) -> {
            String item = itemOf(kind, k);
            Material mat = item == null ? null : Material.matchMaterial(item);
            if (mat != null && n > 0 && sell.sellable(new ItemStack(mat))) {
                snapshot.put(k, n);
                long left = n;
                while (left > 0) {
                    int s = (int) Math.min(left, mat.getMaxStackSize());
                    stacks.add(new ItemStack(mat, s));
                    left -= s;
                }
            }
        });
        if (stacks.isEmpty()) {
            busy.remove(p.getUniqueId());
            done.accept(null);
            return;
        }
        snapshot.forEach((k, n) -> box.amounts.merge(k, -n, Long::sum));
        double bonus = sell.bonusPercent(p) + m.sellBonus(p, kind);
        SellRequest request = new SellRequest(p.getUniqueId(), p.getName(), profile, stacks, bonus, kind.id() + "_storage", Actor.player(p));
        flush(profile, box).thenCompose(v -> sell.sell(request, c -> {
            for (Map.Entry<String, Long> e : snapshot.entrySet()) {
                if (!ToolStore.take(c, profile, e.getKey(), e.getValue())) {
                    return false;
                }
            }
            return true;
        })).whenComplete((result, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            busy.remove(p.getUniqueId());
            if (e != null || result == null || result.status() != SellResult.Status.SOLD) {
                snapshot.forEach((k, n) -> box.amounts.merge(k, n, Long::sum));
            } else if (result.giveBack() != null && !result.giveBack().isEmpty()) {
                m.giveAll(p, result.giveBack()); // taken from the storage but not sold: into the inventory
            }
            done.accept(result);
        }));
    }
}
