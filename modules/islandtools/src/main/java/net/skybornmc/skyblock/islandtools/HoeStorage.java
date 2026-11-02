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
 * T9: the hoe's storage. Harvests land in memory and are written every 10 s (write-behind, additive, so a crash loses at
 * most a few seconds of crops, never duplicates them). Taking out or selling first writes the pending amounts, then takes
 * from the database row in the same transaction as the hand-out record or the sale (the satchel's guard), so nothing is
 * paid or given twice.
 */
final class HoeStorage {

    /** One profile's storage: known amounts plus amounts not written yet. */
    private static final class Box {
        final Map<String, Long> amounts = new ConcurrentHashMap<>();
        final Map<String, Long> pending = new ConcurrentHashMap<>();
        volatile boolean loaded;
    }

    private final IslandToolsModule m;
    private final Map<UUID, Box> boxes = new ConcurrentHashMap<>();

    HoeStorage(IslandToolsModule m) {
        this.m = m;
    }

    void load(UUID profile) {
        Box box = boxes.computeIfAbsent(profile, k -> new Box());
        m.store().storage(profile).whenComplete((map, e) -> {
            if (e != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Island tools: could not read the hoe storage of " + profile, e);
                return;
            }
            map.forEach((item, n) -> box.amounts.merge(item, n, Long::sum));
            box.loaded = true;
        });
    }

    void unload(UUID profile) {
        Box box = boxes.remove(profile);
        if (box != null) {
            flush(profile, box);
        }
    }

    long capacity(Player p) {
        ToolStore.Rec hoe = m.rec(p, Kind.HOE);
        return ToolRules.storage(m.effectLevel(hoe, Upgrades.Effect.STORAGE), m.config().storageBase, m.config().storageFactor);
    }

    long used(UUID profile) {
        Box box = boxes.get(profile);
        return box == null ? 0 : box.amounts.values().stream().mapToLong(Long::longValue).sum();
    }

    Map<String, Long> contents(UUID profile) {
        Box box = boxes.get(profile);
        return box == null ? Map.of() : Map.copyOf(box.amounts);
    }

    boolean ready(UUID profile) {
        Box box = boxes.get(profile);
        return box != null && box.loaded;
    }

    /** Stores what fits; returns how many of {@code amount} were stored. Main thread. */
    long add(Player p, String item, long amount) {
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        if (box == null || !box.loaded || amount <= 0) {
            return 0;
        }
        long room = Math.max(0, capacity(p) - used(profile));
        long take = Math.min(room, amount);
        if (take > 0) {
            box.amounts.merge(item, take, Long::sum);
            box.pending.merge(item, take, Long::sum);
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
            m.core().plugin().getLogger().warning("Island tools: hoe storage writes did not finish in time: " + e.getMessage());
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

    /** Takes up to one stack (or {@code max}) of an item out into the inventory (overflow to /box). */
    void withdraw(Player p, String item, long max, Consumer<Boolean> done) {
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        Material mat = Material.matchMaterial(item);
        long have = box == null ? 0 : box.amounts.getOrDefault(item, 0L);
        long n = Math.min(have, max);
        if (box == null || mat == null || n <= 0) {
            done.accept(false);
            return;
        }
        box.amounts.merge(item, -n, Long::sum);
        flush(profile, box).thenCompose(v -> m.core().database().transaction(c -> ToolStore.take(c, profile, item, n)))
                .whenComplete((ok, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                    if (e != null || !Boolean.TRUE.equals(ok)) {
                        box.amounts.merge(item, n, Long::sum);
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
                    m.giveAll(p, stacks);
                    done.accept(true);
                }));
    }

    /** Sells everything sellable in the storage in one sale (the rows are taken in the sale's transaction). */
    void sellAll(Player p, Consumer<SellResult> done) {
        SellService sell = m.core().services().optional(SellService.class).orElse(null);
        UUID profile = m.profile(p);
        Box box = boxes.get(profile);
        if (sell == null || box == null) {
            done.accept(null);
            return;
        }
        Map<String, Long> snapshot = new HashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        box.amounts.forEach((item, n) -> {
            Material mat = Material.matchMaterial(item);
            if (mat != null && n > 0 && sell.sellable(new ItemStack(mat))) {
                snapshot.put(item, n);
                long left = n;
                while (left > 0) {
                    int s = (int) Math.min(left, mat.getMaxStackSize());
                    stacks.add(new ItemStack(mat, s));
                    left -= s;
                }
            }
        });
        if (stacks.isEmpty()) {
            done.accept(null);
            return;
        }
        snapshot.forEach((item, n) -> box.amounts.merge(item, -n, Long::sum));
        SellRequest request = new SellRequest(p.getUniqueId(), p.getName(), profile, stacks, sell.bonusPercent(p), "hoe_storage", Actor.player(p));
        flush(profile, box).thenCompose(v -> sell.sell(request, c -> {
            for (Map.Entry<String, Long> e : snapshot.entrySet()) {
                if (!ToolStore.take(c, profile, e.getKey(), e.getValue())) {
                    return false;
                }
            }
            return true;
        })).whenComplete((result, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            if (e != null || result == null || result.status() != SellResult.Status.SOLD) {
                snapshot.forEach((item, n) -> box.amounts.merge(item, n, Long::sum));
            } else if (result.giveBack() != null && !result.giveBack().isEmpty()) {
                m.giveAll(p, result.giveBack()); // taken from the storage but not sold: into the inventory
            }
            done.accept(result);
        }));
    }
}
