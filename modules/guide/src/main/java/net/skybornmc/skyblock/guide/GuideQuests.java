package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Kindle's quests and finds at run time: who is offered what, what is done (cached, loaded on demand), and the hand-in.
 *
 * <p>Hand-in order (dupe-safe): the items are taken from the inventory first (plain items only, never named or custom
 * ones), then ONE database transaction records the hand-in (primary key: once per rotation, once per island for the
 * story) and pays money and crystals through the ledger. If that fails or was done already, the items go back (inventory,
 * overflow to /box). Items and keys are handed out after the commit.</p>
 */
final class GuideQuests {

    /** Result shown to the player. */
    enum Result { OK, MISSING, DONE, BUSY, ERROR, LOCKED }

    private final GuideModule m;
    private volatile GuideCatalog catalog = GuideCatalog.empty();
    /** island → done story ids. */
    private final Map<UUID, Set<String>> story = new ConcurrentHashMap<>();
    /** "profile|kind|period" → done ids. */
    private final Map<String, Set<String>> rotation = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    GuideQuests(GuideModule m) {
        this.m = m;
    }

    void catalog(GuideCatalog c) {
        this.catalog = c;
    }

    GuideCatalog catalog() {
        return catalog;
    }

    // ------------------------------------------------------------------ what is offered

    int islandLevel(UUID island) {
        return m.core().services().optional(ProgressionService.class).map(p -> p.islandLevel(island)).orElse(0);
    }

    UUID profile(Player p) {
        return m.core().services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
    }

    long period() {
        return GuideCatalog.period(System.currentTimeMillis(), m.config().errandHours);
    }

    /** Story ids the island finished (empty and loading on the first call; {@code then} runs when loaded). */
    Set<String> storyDone(UUID island, Runnable then) {
        Set<String> s = story.get(island);
        if (s != null) {
            return s;
        }
        String key = "story|" + island;
        if (loading.add(key)) {
            m.store().done(island, GuideCatalog.Kind.STORY, GuideStore.STORY).whenComplete((set, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                loading.remove(key);
                if (e == null) {
                    story.put(island, ConcurrentHashMap.newKeySet());
                    story.get(island).addAll(set);
                    if (then != null) {
                        then.run();
                    }
                }
            }));
        }
        return Set.of();
    }

    boolean storyLoaded(UUID island) {
        return story.containsKey(island);
    }

    /** The island's current story quest, or null (all done, or still loading). */
    GuideCatalog.Entry currentStory(UUID island, Runnable then) {
        Set<String> done = storyDone(island, then);
        return storyLoaded(island) ? GuideCatalog.currentStory(catalog.all(GuideCatalog.Kind.STORY), done) : null;
    }

    List<GuideCatalog.Entry> offers(Player p, UUID island, GuideCatalog.Kind kind) {
        int count = kind == GuideCatalog.Kind.TRADE ? m.config().tradeCount : m.config().errandCount;
        long period = period();
        return GuideCatalog.picks(catalog.all(kind), islandLevel(island), count, GuideCatalog.seed(profile(p), period, kind));
    }

    Set<String> rotationDone(UUID profile, GuideCatalog.Kind kind, long period, Runnable then) {
        String key = profile + "|" + kind.id() + "|" + period;
        Set<String> s = rotation.get(key);
        if (s != null) {
            return s;
        }
        if (loading.add(key)) {
            m.store().done(profile, kind, String.valueOf(period)).whenComplete((set, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                loading.remove(key);
                if (e == null) {
                    Set<String> fresh = ConcurrentHashMap.newKeySet();
                    fresh.addAll(set);
                    rotation.put(key, fresh);
                    if (then != null) {
                        then.run();
                    }
                }
            }));
        }
        return Set.of();
    }

    /** Old rotations leave the cache (hourly). */
    void prune() {
        long now = period();
        rotation.keySet().removeIf(k -> {
            String[] parts = k.split("\\|");
            try {
                return Long.parseLong(parts[2]) < now;
            } catch (RuntimeException e) {
                return true;
            }
        });
        story.keySet().removeIf(island -> m.islands().flatMap(i -> i.island(island)).isEmpty());
    }

    void forget(UUID island) {
        story.remove(island);
    }

    // ------------------------------------------------------------------ inventory

    /** Plain items only (no name, lore or custom data), so tools and custom items are never taken. */
    static boolean plain(ItemStack stack, Material type) {
        return stack != null && stack.getType() == type && !stack.hasItemMeta();
    }

    static int count(PlayerInventory inv, Material type) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (plain(s, type)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    boolean has(Player p, GuideCatalog.Entry e) {
        for (Map.Entry<String, Integer> c : e.cost().entrySet()) {
            Material mat = Material.matchMaterial(c.getKey());
            if (mat == null || count(p.getInventory(), mat) < c.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Takes the cost; returns what was taken (for a refund). Call only after {@link #has}. */
    private List<ItemStack> take(Player p, GuideCatalog.Entry e) {
        List<ItemStack> taken = new ArrayList<>();
        PlayerInventory inv = p.getInventory();
        for (Map.Entry<String, Integer> c : e.cost().entrySet()) {
            Material mat = Material.matchMaterial(c.getKey());
            int left = c.getValue();
            ItemStack[] contents = inv.getStorageContents();
            for (int i = 0; i < contents.length && left > 0; i++) {
                ItemStack s = contents[i];
                if (plain(s, mat)) {
                    int t = Math.min(left, s.getAmount());
                    s.setAmount(s.getAmount() - t);
                    contents[i] = s.getAmount() <= 0 ? null : s;
                    left -= t;
                }
            }
            inv.setStorageContents(contents);
            int got = c.getValue() - left;
            while (got > 0) {
                int n = Math.min(got, mat.getMaxStackSize());
                taken.add(new ItemStack(mat, n));
                got -= n;
            }
        }
        return taken;
    }

    // ------------------------------------------------------------------ hand-in

    void handIn(Player p, UUID island, GuideCatalog.Entry e, java.util.function.Consumer<Result> done) {
        UUID id = p.getUniqueId();
        UUID profile = profile(p);
        UUID scope = e.kind() == GuideCatalog.Kind.STORY ? island : profile;
        String period = e.kind() == GuideCatalog.Kind.STORY ? GuideStore.STORY : String.valueOf(period());
        if (e.level() > islandLevel(island)) {
            done.accept(Result.LOCKED);
            return;
        }
        if (!has(p, e)) {
            done.accept(Result.MISSING);
            return;
        }
        if (!busy.add(id)) {
            done.accept(Result.BUSY);
            return;
        }
        List<ItemStack> taken = take(p, e);
        long now = System.currentTimeMillis();
        List<Transaction> tx = new ArrayList<>();
        for (GuideCatalog.Reward r : e.rewards()) {
            Currency cur = r.type() == GuideCatalog.RewardType.MONEY ? Currency.MONEY : r.type() == GuideCatalog.RewardType.CRYSTALS ? Currency.CRYSTALS : null;
            if (cur != null) {
                tx.add(Transaction.deposit(AccountRef.player(profile), cur, r.amount(), TxReason.QUEST_REWARD)
                        .actor(Actor.player(p)).reference("guide:" + e.kind().id() + ":" + e.id())
                        .idempotencyKey("guide:" + e.kind().id() + ":" + scope + ":" + period + ":" + e.id() + ":" + cur.key())
                        .detail("guide", e.kind().id()).detail("entry", e.id()));
            }
        }
        boolean[] recorded = new boolean[1];
        TxGuard guard = c -> {
            recorded[0] = GuideStore.record(c, scope, e.kind(), period, e.id(), id, now);
            return recorded[0];
        };
        CompletableFuture<Boolean> run;
        EconomyService economy = m.core().services().optional(EconomyService.class).orElse(null);
        if (!tx.isEmpty() && economy == null) {
            run = CompletableFuture.completedFuture(false);
        } else if (tx.isEmpty()) {
            run = m.core().database().transaction(guard::holds);
        } else {
            run = economy.executeBatch(tx, guard).thenApply(BatchResult::ok);
        }
        run.whenComplete((ok, error) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            busy.remove(id);
            boolean success = error == null && Boolean.TRUE.equals(ok) && recorded[0];
            if (!success) {
                giveItems(p, profile, taken); // nothing was paid: everything goes back
                if (error != null) {
                    m.core().plugin().getLogger().log(Level.WARNING, "Guide: hand-in of " + e.id() + " by " + p.getName() + " failed", error);
                }
                done.accept(error == null && !recorded[0] && Boolean.FALSE.equals(ok) ? Result.DONE : Result.ERROR);
                return;
            }
            if (e.kind() == GuideCatalog.Kind.STORY) {
                story.computeIfAbsent(island, k -> ConcurrentHashMap.newKeySet()).add(e.id());
            } else {
                rotation.computeIfAbsent(profile + "|" + e.kind().id() + "|" + period, k -> ConcurrentHashMap.newKeySet()).add(e.id());
            }
            handOut(p, profile, e);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("kind", e.kind().id());
            d.put("entry", e.id());
            d.put("cost", e.cost());
            d.put("rewards", e.rewards().toString());
            d.put("island", String.valueOf(island));
            m.core().audit().logPlayer(AuditCategory.ITEM, "guide_hand_in", Actor.player(p), id, p.getName(), null, p.getLocation(), d);
            done.accept(Result.OK);
        }));
    }

    private void handOut(Player p, UUID profile, GuideCatalog.Entry e) {
        List<ItemStack> items = new ArrayList<>();
        Map<String, Integer> keys = new HashMap<>();
        for (GuideCatalog.Reward r : e.rewards()) {
            switch (r.type()) {
                case ITEM -> {
                    Material mat = Material.matchMaterial(r.what());
                    long left = r.amount();
                    while (mat != null && left > 0) {
                        int n = (int) Math.min(left, mat.getMaxStackSize());
                        items.add(new ItemStack(mat, n));
                        left -= n;
                    }
                }
                case KEY -> keys.merge(r.what(), (int) r.amount(), Integer::sum);
                default -> { }
            }
        }
        giveItems(p, profile, items);
        CrateKeys crates = m.core().services().optional(CrateKeys.class).orElse(null);
        keys.forEach((crate, n) -> {
            if (crates == null) {
                m.core().plugin().getLogger().warning("Guide: " + n + " " + crate + " key(s) for " + p.getName() + " not given (crates module off)");
                return;
            }
            crates.giveKeys(profile, crate, n, "guide", Actor.player(p)).whenComplete((ok, err) -> {
                if (err != null || !Boolean.TRUE.equals(ok)) {
                    m.core().plugin().getLogger().warning("Guide: " + n + " " + crate + " key(s) for " + p.getName() + " could not be stored"
                            + (err == null ? "" : ": " + err.getMessage()));
                }
            });
        });
    }

    private void giveItems(Player p, UUID profile, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        ItemBox box = m.core().services().optional(ItemBox.class).orElse(null);
        if (box != null && p.isOnline()) {
            box.giveItems(p, items, BoxSource.REWARDS);
        } else if (box != null) {
            box.deliver(profile, items, BoxSource.REWARDS, Actor.system("guide"));
        } else {
            p.getInventory().addItem(items.toArray(new ItemStack[0])).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }
}
