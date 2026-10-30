package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Book drops from activities (E2, ENCHANTS §3.2–§3.4): Depths ore blocks (Mining pool), logs (Foraging) and combat kills
 * (Combat), with first finds per account and season, pity per profile and activity, rarity and level by tier band and each
 * enchant's minimum tier, and mini-boss books. Depths hooks call {@link #action} / {@link #miniBoss}.
 *
 * <p>Per action: a few array updates and one random number; counters are saved in batches every
 * {@code drops.save-seconds} and on quit / profile switch, never per block. A dropped book is one database transaction
 * (first-find claim + book row + collection + pity reset), audited as {@code book_created}.</p>
 */
final class BookDrops implements Listener {

    static final String ORIGIN_PREFIX = "drop:";
    static final String ORIGIN_MINI_BOSS = "drop:mini_boss";
    /** Pools with Depths book drops (and a "next guaranteed book" line). */
    static final List<Pool> POOLS = List.of(Pool.MINING, Pool.FORAGING, Pool.COMBAT);

    private final EnchantsModule module;
    private final DropStore store;
    private final Map<UUID, DropCounters.Pity> pity = new HashMap<>();
    private final Map<UUID, DropCounters.FirstFinds> firsts = new HashMap<>();
    private volatile DropMath.Rules rules;
    private int season;
    private Scheduler.Task saveTask;

    BookDrops(EnchantsModule module, DropStore store) {
        this.module = module;
        this.store = store;
        this.rules = module.config().dropRules();
        this.season = module.config().seasonId;
    }

    /** After a config (re)load. A new season id starts fresh first-find counters (the old ones are saved first). */
    void reload() {
        rules = module.config().dropRules();
        if (season != module.config().seasonId) {
            save(false, true);
            firsts.clear();
            season = module.config().seasonId;
        }
        start();
    }

    void start() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        long ticks = Math.max(5, module.config().dropSaveSeconds) * 20L;
        saveTask = module.core.scheduler().repeat("enchants-drop-save", ticks, ticks, () -> save(false, false));
    }

    /** Disable: saves every counter right away (waits for the database). */
    void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        save(true, true);
        pity.clear();
        firsts.clear();
    }

    DropMath.Rules rules() {
        return rules;
    }

    // ================================================================== actions

    /**
     * One action of an activity at a tier: {@code actions} is 1 for a block/log/kill plus the yield share of area blocks.
     * Main thread.
     */
    void action(Player player, Pool pool, int tier, double actions, Location at) {
        DropMath.Rules r = rules;
        if (!r.drops(pool) || !(actions > 0)) {
            return;
        }
        UUID profile = profileKey(player);
        DropCounters.Pity p = pity(profile);
        DropCounters.FirstFinds f = firsts(player.getUniqueId());
        f.add(pool, actions, r.firstFinds().size());
        p.add(pool, actions);
        int band = r.band(tier);
        DropMath.Decision d = DropMath.decide(f.loaded() && !f.pending(pool), f.actions(pool), f.granted(pool), r.firstFinds(),
                p.loaded(), p.since(pool), r.pityGap(pool, band), DropMath.chance(r.oneIn(pool, band), actions),
                ThreadLocalRandom.current().nextDouble());
        if (d.drops()) {
            drop(player, profile, pool, tier, band, d, false, at, p, f);
        }
    }

    /** A mini-boss rewarded this player (≥ the damage share): a chance of a Combat book, rarity bands better (§3.2). */
    void miniBoss(Player player, int tier, Location at) {
        DropMath.Rules r = rules;
        if (!r.drops(Pool.COMBAT) || !GatherMath.roll(r.miniBossChance(), ThreadLocalRandom.current())) {
            return;
        }
        UUID profile = profileKey(player);
        drop(player, profile, Pool.COMBAT, tier, r.band(tier), new DropMath.Decision(DropMath.Kind.CHANCE, -1), true, at,
                pity(profile), firsts(player.getUniqueId()));
    }

    private void drop(Player player, UUID profile, Pool pool, int tier, int band, DropMath.Decision decision, boolean miniBoss,
                      Location at, DropCounters.Pity p, DropCounters.FirstFinds f) {
        DropMath.Rules r = rules;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean first = decision.kind() == DropMath.Kind.FIRST;
        Optional<EnchantDef> def;
        int levelBand = miniBoss ? band + Math.max(0, r.miniBossBands()) : band;
        if (first) {
            Rarity rarity = r.firstFinds().get(decision.milestone()).rarity();
            def = Rolls.pick(module.registry(), pool, rarity, tier, random);
        } else {
            def = Rolls.roll(module.registry(), pool, miniBoss ? DropMath.shifted(r, band, r.miniBossBands()) : r.odds(band), tier, random);
        }
        if (def.isEmpty()) {
            return; // nothing in this pool can drop at this tier (all turned off): counters keep running
        }
        String enchant = def.get().id();
        int level = first ? 1 : StackKillMath.bookLevel(r.levelOdds(levelBand), def.get().maxLevel(), random);
        String origin = miniBoss ? ORIGIN_MINI_BOSS : ORIGIN_PREFIX + pool.id();
        ItemStack book = module.books().createBook(enchant, level, origin);
        UUID bookId = BookItems.id(book);
        UUID owner = player.getUniqueId();
        double old = p.reset(pool);
        int from = first ? decision.milestone() : -1;
        if (first) {
            f.begin(pool);
        }
        double accountActions = f.actions(pool);
        int seasonId = season;
        long now = System.currentTimeMillis();
        String reason = miniBoss ? "mini_boss" : decision.kind().name().toLowerCase(java.util.Locale.ROOT);
        module.core.database().transaction(c -> store.mintDrop(c, bookId, enchant, level, seasonId, origin, owner, profile, pool.id(),
                from, accountActions, true, now)).whenComplete((ok, error) -> module.core.scheduler().main("enchants-drop-book", () -> {
            boolean saved = error == null && Boolean.TRUE.equals(ok);
            if (first) {
                f.finish(pool, saved, from);
                if (error == null && !saved) {
                    f.skip(pool, from); // already given out (e.g. on another server): never retry this milestone
                }
            }
            if (!saved) {
                p.restore(pool, old);
                if (error != null) {
                    module.core.plugin().getLogger().log(Level.WARNING, "Could not save a dropped book for " + player.getName(), error);
                }
                return;
            }
            StackEnchants.give(module, player, at, book);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("book", bookId.toString());
            details.put("kind", BookStore.KIND_BOOK);
            details.put("enchant", enchant);
            details.put("level", level);
            details.put("origin", origin);
            details.put("reason", reason);
            details.put("tier", tier);
            details.put("profile", profile.toString());
            module.core.audit().logPlayer(AuditCategory.ITEM, "book_created", Actor.system(EnchantsModule.ID), owner, player.getName(),
                    null, at, details);
            if (player.isOnline()) {
                String key = first ? "drop.first" : miniBoss ? "drop.mini-boss" : decision.kind() == DropMath.Kind.PITY ? "drop.pity" : "drop.book";
                String name = module.text().displayName(enchant, level);
                module.msg().send(player, key, Msg.mm("book", "<rarity:" + def.get().rarity().id() + ">" + LoreBuilder.escape(name)));
                module.core.sounds().play(player, module.config().soundDropBook);
            }
        }));
    }

    // ================================================================== "next guaranteed book" (menu)

    /**
     * Actions until the next guaranteed book per pool (at the player's highest tier in that wing), {@code null} while the
     * counters are still loading, {@link Long#MAX_VALUE} when nothing is guaranteed.
     */
    Map<Pool, Long> nextGuaranteed(Player player) {
        DropMath.Rules r = rules;
        Map<Pool, Long> out = new EnumMap<>(Pool.class);
        DropCounters.Pity p = pity(profileKey(player));
        DropCounters.FirstFinds f = firsts(player.getUniqueId());
        for (Pool pool : POOLS) {
            if (!r.drops(pool)) {
                continue;
            }
            if (!p.loaded() || !f.loaded()) {
                out.put(pool, null);
                continue;
            }
            int band = r.band(module.ops().tier(player, pool));
            out.put(pool, DropMath.nextGuaranteed(p.since(pool), r.pityGap(pool, band), f.actions(pool), f.granted(pool), r.firstFinds()));
        }
        return out;
    }

    // ================================================================== loading and saving

    private DropCounters.Pity pity(UUID profile) {
        DropCounters.Pity state = pity.get(profile);
        if (state != null) {
            return state;
        }
        DropCounters.Pity created = new DropCounters.Pity();
        pity.put(profile, created);
        module.core.database().async(c -> store.loadPity(c, profile)).whenComplete((saved, error) ->
                module.core.scheduler().main("enchants-drop-load", () -> {
                    if (error != null) {
                        module.core.plugin().getLogger().log(Level.WARNING, "Could not load book drop counters", error);
                        pity.remove(profile, created); // never saved while not loaded; the next action tries again
                        return;
                    }
                    created.load(saved);
                }));
        return created;
    }

    private DropCounters.FirstFinds firsts(UUID account) {
        DropCounters.FirstFinds state = firsts.get(account);
        if (state != null) {
            return state;
        }
        DropCounters.FirstFinds created = new DropCounters.FirstFinds();
        firsts.put(account, created);
        int seasonId = season;
        module.core.database().async(c -> store.loadFirstFinds(c, account, seasonId)).whenComplete((saved, error) ->
                module.core.scheduler().main("enchants-drop-load", () -> {
                    if (error != null) {
                        module.core.plugin().getLogger().log(Level.WARNING, "Could not load first-find counters", error);
                        firsts.remove(account, created);
                        return;
                    }
                    created.load(saved);
                }));
        return created;
    }

    /** One row to write. */
    private record PityRow(UUID profile, String activity, double since) {
    }

    private record FirstRow(UUID account, int season, String activity, double actions) {
    }

    /**
     * Writes every changed, loaded counter in one transaction. Unless {@code all}, states of players who left or switched
     * profile (and have nothing left to save) are dropped afterwards.
     */
    private void save(boolean blocking, boolean all) {
        List<PityRow> pityRows = new ArrayList<>();
        List<FirstRow> firstRows = new ArrayList<>();
        Set<UUID> liveProfiles = new HashSet<>();
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            liveProfiles.add(profileKey(player));
        }
        pity.forEach((profile, state) -> {
            if (state.loaded() && state.dirty()) {
                for (Pool pool : POOLS) {
                    pityRows.add(new PityRow(profile, pool.id(), state.since(pool)));
                }
                state.clean();
            }
        });
        firsts.forEach((account, state) -> {
            if (state.loaded() && state.dirty()) {
                for (Pool pool : POOLS) {
                    if (state.actions(pool) > 0) {
                        firstRows.add(new FirstRow(account, season, pool.id(), state.actions(pool)));
                    }
                }
                state.clean();
            }
        });
        if (!all) {
            pity.keySet().removeIf(k -> !liveProfiles.contains(k) && !pity.get(k).dirty());
            firsts.keySet().removeIf(k -> !online.contains(k) && !firsts.get(k).dirty());
        }
        if (pityRows.isEmpty() && firstRows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        net.skybornmc.skyblock.common.db.Sql.SqlFunction<Void> work = c -> {
            for (PityRow row : pityRows) {
                store.savePity(c, row.profile(), row.activity(), row.since(), now);
            }
            for (FirstRow row : firstRows) {
                store.saveFirstActions(c, row.account(), row.season(), row.activity(), row.actions(), now);
            }
            return null;
        };
        if (blocking) {
            try {
                module.core.database().blockingTransaction(work);
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(Level.WARNING, "Could not save book drop counters", e);
            }
            return;
        }
        module.core.database().transaction(work).whenComplete((v, error) -> {
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Could not save book drop counters (retried with the next save)", error);
                module.core.scheduler().main("enchants-drop-save-retry", () -> markDirty(pityRows, firstRows));
            }
        });
    }

    private void markDirty(List<PityRow> pityRows, List<FirstRow> firstRows) {
        for (PityRow row : pityRows) {
            DropCounters.Pity state = pity.get(row.profile());
            if (state != null) {
                state.markDirty();
            }
        }
        for (FirstRow row : firstRows) {
            DropCounters.FirstFinds state = firsts.get(row.account());
            if (state != null) {
                state.markDirty();
            }
        }
    }

    /** Saves and forgets one profile's pity and (on quit) the account's first finds. */
    private void saveAndForget(UUID profile, UUID account) {
        save(false, true);
        DropCounters.Pity p = pity.get(profile);
        if (p != null && !p.dirty()) {
            pity.remove(profile);
        }
        if (account != null) {
            DropCounters.FirstFinds f = firsts.get(account);
            if (f != null && !f.dirty()) {
                firsts.remove(account);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        saveAndForget(profileKey(event.getPlayer()), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitched(ProfileSwitchedEvent event) {
        if (event.from() != null) {
            saveAndForget(event.from().id(), null);
        }
    }

    private UUID profileKey(Player player) {
        return ProfileService.of(module.core.services()).profileKey(player);
    }
}
