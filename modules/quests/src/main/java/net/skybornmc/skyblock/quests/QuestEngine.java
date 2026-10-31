package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Quest state and rules on the main thread (PHASE-10 M10.7).
 * <ul>
 *   <li><b>Sets</b> are loaded for the active profile on join and profile switch (daily + weekly) and for its island
 *       (missions). A window without a set is rolled from the pool on the main thread (eligibility needs the live player)
 *       and stored in one transaction; if another roll stored one first, that one wins.</li>
 *   <li><b>Progress</b> comes only from server events ({@link QuestSources}, {@link DepthsQuestListener}) and is kept in
 *       memory, saved every {@code save.seconds} (only the difference, never lowering it). A crash loses at most that much
 *       progress, never a reward.</li>
 *   <li><b>Completion</b> pays at once ({@link Payouts}: one transaction, idempotent per quest instance), then hands out
 *       keys and pass XP ({@link QuestGrants}). A failed payout is tried again by the tick.</li>
 * </ul>
 */
final class QuestEngine {

    /** The daily and weekly set of one profile. */
    static final class Boards {
        QuestSet daily;
        QuestSet weekly;
        UUID player;

        QuestSet get(QuestKind kind) {
            return kind == QuestKind.DAILY ? daily : weekly;
        }

        void put(QuestSet set) {
            if (set.kind == QuestKind.DAILY) {
                daily = set;
            } else {
                weekly = set;
            }
        }
    }

    private final QuestsModule m;
    private final Map<UUID, Boards> profiles = new HashMap<>();
    private final Map<UUID, QuestSet> islands = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    /** Sets rolled with no quest (nothing unlocked yet): not stored, rolled again later. key → time of the roll. */
    private final Map<String, Long> emptyRolls = new HashMap<>();
    private final Set<UUID> swapping = new HashSet<>();
    private int today;
    private int week;
    private long lastErrorLog;

    QuestEngine(QuestsModule module) {
        this.m = module;
        updateWindows();
    }

    // ================================================================== windows

    /** Updates the cached windows (every second; counting compares against these, never computes dates per block). */
    void updateWindows() {
        long now = System.currentTimeMillis();
        today = QuestWindows.day(now, m.zone());
        week = QuestWindows.weekStart(now, m.zone());
    }

    int period(QuestKind kind) {
        return kind.weekly() ? week : today;
    }

    // ================================================================== access

    Optional<QuestSet> set(UUID profile, QuestKind kind) {
        Boards b = profiles.get(profile);
        QuestSet s = b == null ? null : b.get(kind);
        return s == null || s.period != period(kind) ? Optional.empty() : Optional.of(s);
    }

    Optional<QuestSet> island(UUID island) {
        QuestSet s = island == null ? null : islands.get(island);
        return s == null || s.period != period(QuestKind.ISLAND) ? Optional.empty() : Optional.of(s);
    }

    boolean loading(QuestKind kind, UUID owner) {
        return loading.contains(key(kind, owner, period(kind)));
    }

    boolean swapping(UUID player) {
        return swapping.contains(player);
    }

    private static String key(QuestKind kind, UUID owner, int period) {
        return kind.id() + ":" + owner + ":" + period;
    }

    // ================================================================== loading and rolling

    /** Loads (or rolls) the player's daily, weekly and island sets for the current windows. Main thread. */
    void load(Player player) {
        if (!m.settings.enabled || !player.isOnline()) {
            return;
        }
        UUID profile = m.profileKey(player);
        Boards b = profiles.computeIfAbsent(profile, k -> new Boards());
        b.player = player.getUniqueId();
        ensure(player, QuestKind.DAILY, profile);
        ensure(player, QuestKind.WEEKLY, profile);
        UUID island = m.countingIsland(player);
        if (island != null) {
            ensure(player, QuestKind.ISLAND, island);
        }
    }

    private QuestSet current(QuestKind kind, UUID owner) {
        if (kind == QuestKind.ISLAND) {
            return islands.get(owner);
        }
        Boards b = profiles.get(owner);
        return b == null ? null : b.get(kind);
    }

    private void ensure(Player player, QuestKind kind, UUID owner) {
        int period = period(kind);
        QuestSet have = current(kind, owner);
        if (have != null && have.period == period) {
            return;
        }
        String key = key(kind, owner, period);
        Long empty = emptyRolls.get(key);
        if ((empty != null && System.currentTimeMillis() - empty < 60_000L) || !loading.add(key)) {
            return;
        }
        if (have != null) {
            save(have); // the old window's last progress
        }
        UUID playerId = player.getUniqueId();
        m.core.database().async(c -> QuestStore.load(c, kind, owner, period)).whenCompleteAsync((found, error) -> {
            if (error != null) {
                loading.remove(key);
                logError("loading " + kind.id() + " quests", error);
                return;
            }
            if (found.isPresent()) {
                loading.remove(key);
                install(found.get());
                return;
            }
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !owns(online, kind, owner)) {
                loading.remove(key);
                return;
            }
            roll(online, kind, owner, period, key);
        }, m.main());
    }

    /** True while {@code player} still stands for {@code owner} (profile not switched, still on that island). */
    private boolean owns(Player player, QuestKind kind, UUID owner) {
        return kind == QuestKind.ISLAND ? owner.equals(m.countingIsland(player)) : owner.equals(m.profileKey(player));
    }

    private void roll(Player player, QuestKind kind, UUID owner, int period, String key) {
        int tier = m.tier(player);
        QuestSet rolled = new QuestSet(kind, owner, period, 0, false);
        List<QuestPool.Entry> picks = QuestPool.pick(m.eligible(player, kind), m.count(kind), Set.of(), ThreadLocalRandom.current());
        for (int i = 0; i < picks.size(); i++) {
            rolled.quests.add(create(player, kind, i, picks.get(i), tier));
        }
        if (rolled.quests.isEmpty()) {
            loading.remove(key);
            emptyRolls.put(key, System.currentTimeMillis());
            m.refreshMenus();
            return;
        }
        long now = System.currentTimeMillis();
        m.core.database().transaction(c -> QuestStore.insertOrLoad(c, rolled, tier, now)).whenCompleteAsync((stored, error) -> {
            loading.remove(key);
            if (error != null) {
                logError("storing new " + kind.id() + " quests", error);
                return;
            }
            emptyRolls.remove(key);
            install(stored);
            if (stored == rolled) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("kind", kind.id());
                d.put("period", period);
                d.put("tier", tier);
                d.put("quests", describe(rolled));
                if (kind == QuestKind.ISLAND) {
                    d.put("island", owner.toString());
                }
                m.audit(kind == QuestKind.ISLAND ? "missions_rolled" : "quests_rolled", Actor.SYSTEM, player.getUniqueId(), player.getName(),
                        null, d);
            }
        }, m.main());
    }

    /** A new quest for slot {@code slot} from a pool entry, sized for {@code player}. */
    Quest create(Player player, QuestKind kind, int slot, QuestPool.Entry entry, int stageTier) {
        int wingTier = entry.type().depths() ? QuestMath.wingTier(m.unlockedTiers(player, entry.type().wing()), m.settings.tierOffset) : 0;
        long target = QuestMath.target(entry, m.rates(entry.type()), m.refPerMinute(), wingTier, stageTier, m.minutes(kind),
                m.settings.sellShare);
        long money = kind == QuestKind.ISLAND ? 0 : QuestMath.money(m.refPerMinute(), stageTier, m.moneyMinutes(kind));
        long crystals = QuestMath.crystals(m.crystalBase(kind), stageTier, m.settings.crystalGrowth);
        return new Quest(UUID.randomUUID(), slot, entry.type(), wingTier, target, money, crystals, m.passXp(kind), 0, 0);
    }

    private void install(QuestSet set) {
        if (set.kind == QuestKind.ISLAND) {
            islands.put(set.owner, set);
        } else {
            profiles.computeIfAbsent(set.owner, k -> new Boards()).put(set);
        }
        m.refreshMenus();
        for (Quest q : set.quests) {
            if (!q.done() && q.reached()) {
                complete(set, q); // a payout a crash interrupted before its commit
            }
        }
    }

    private static String describe(QuestSet set) {
        List<String> parts = new ArrayList<>();
        for (Quest q : set.quests) {
            parts.add(q.type.id() + (q.tierFrom > 0 ? "@t" + q.tierFrom : "") + " " + q.target);
        }
        return String.join(", ", parts);
    }

    // ================================================================== progress

    private static boolean playing(Player player) {
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /**
     * Counts an action of the player: {@code tier} is the Depths tier for mine/chop/kill (0 otherwise). Creative and
     * spectator never count; island missions only for stored members (never coop players). Main thread, O(quests).
     */
    void count(Player player, QuestType type, int tier, long amount) {
        if (!m.settings.enabled || amount <= 0 || !playing(player)) {
            return;
        }
        UUID profile = m.profileKey(player);
        Boards b = profiles.get(profile);
        if (b != null) {
            add(player, b.daily, type, tier, amount, null);
            add(player, b.weekly, type, tier, amount, null);
        }
        if (!islands.isEmpty()) {
            UUID island = m.countingIsland(player);
            if (island != null) {
                add(player, islands.get(island), type, tier, amount, profile);
            }
        }
    }

    private void add(Player player, QuestSet set, QuestType type, int tier, long amount, UUID helper) {
        if (set == null || set.period != period(set.kind)) {
            return;
        }
        for (Quest q : set.quests) {
            if (!q.counts(type, tier)) {
                continue;
            }
            long before = q.add(amount);
            long added = q.progress - before;
            if (added <= 0) {
                continue;
            }
            set.lastUsed = System.currentTimeMillis();
            if (helper != null) {
                set.contribute(q.uid, helper, player.getUniqueId(), added);
            }
            if (q.reached()) {
                complete(set, q);
            } else {
                int step = QuestMath.stepCrossed(before, q.progress, q.target, m.settings.stepPercent);
                if (step > 0) {
                    m.text().step(player, set.kind, q, step);
                }
            }
        }
    }

    // ================================================================== completion

    /** Pays a reached quest (once; a running payout is never started twice). Main thread. */
    void complete(QuestSet set, Quest q) {
        if (q.completing || q.done() || !q.reached()) {
            return;
        }
        q.completing = true;
        long now = System.currentTimeMillis();
        Payouts.Plan plan;
        Map<UUID, UUID> helpers = new LinkedHashMap<>();
        if (set.kind == QuestKind.ISLAND) {
            Map<UUID, Long> contributions = new HashMap<>(set.contributions(q.uid));
            for (Map.Entry<UUID, Long> e : contributions.entrySet()) {
                if (QuestMath.helped(e.getValue(), q.target, m.settings.helperMinPercent) && m.member(set.owner, e.getKey())) {
                    helpers.put(e.getKey(), set.helperPlayers.getOrDefault(e.getKey(), e.getKey()));
                }
            }
            plan = Payouts.island(set, q, helpers, contributions, m.passXpLive(), Actor.SYSTEM, now);
        } else {
            Boards b = profiles.get(set.owner);
            UUID player = b == null || b.player == null ? m.ownerOf(set.owner) : b.player;
            plan = Payouts.personal(set, q, player, m.bonus(set.kind), m.passXpLive(), Actor.SYSTEM, now);
        }
        Payouts.commit(m.runner(), plan).whenCompleteAsync((status, error) -> {
            q.completing = false;
            if (status == Payouts.Status.OK) {
                q.doneAt = now;
                q.progress = q.target;
                q.saved = q.target;
                if (plan.outcome().bonus) {
                    set.bonusPaid = true;
                }
                if (set.kind == QuestKind.ISLAND) {
                    Map<UUID, Long> saved = set.savedContributions.computeIfAbsent(q.uid, k -> new HashMap<>());
                    saved.putAll(set.contributions(q.uid));
                }
                m.grants().handOut(plan.outcome().grants);
                m.text().done(set, q, plan.outcome());
                audit(set, q, plan.outcome(), helpers);
                m.refreshMenus();
            } else if (status == Payouts.Status.CHANGED) {
                reload(set); // finished elsewhere (staff) or swapped: read the truth again
            } else {
                logError("paying a " + set.kind.id() + " quest (" + q.type.id() + ", will retry)", null);
            }
        }, m.main());
    }

    private void audit(QuestSet set, Quest q, Payouts.Outcome outcome, Map<UUID, UUID> helpers) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", set.kind.id());
        d.put("type", q.type.id());
        d.put("target", q.target);
        if (q.tierFrom > 0) {
            d.put("tier_from", q.tierFrom);
        }
        d.put("quest", q.uid.toString());
        d.put("crystals", q.rewardCrystals);
        d.put("pass_xp", q.rewardXp);
        if (set.kind == QuestKind.ISLAND) {
            d.put("island", set.owner.toString());
            d.put("helpers", helpers.size());
            d.put("helper_profiles", String.join(",", helpers.keySet().stream().map(UUID::toString).toList()));
            m.audit("mission_done", Actor.SYSTEM, null, null, null, d);
            return;
        }
        d.put("profile", set.owner.toString());
        d.put("bonus", outcome.bonus);
        Boards b = profiles.get(set.owner);
        UUID player = b == null ? null : b.player;
        Player online = player == null ? null : Bukkit.getPlayer(player);
        m.audit("quest_done", Actor.SYSTEM, player, online == null ? null : online.getName(), q.rewardMoney, d);
    }

    /** Re-reads a set from the database (after a payout found it changed). */
    private void reload(QuestSet set) {
        String key = key(set.kind, set.owner, set.period);
        if (!loading.add(key)) {
            return;
        }
        m.core.database().async(c -> QuestStore.load(c, set.kind, set.owner, set.period)).whenCompleteAsync((found, error) -> {
            loading.remove(key);
            if (error != null) {
                logError("reloading quests", error);
                return;
            }
            found.ifPresent(this::installQuiet);
        }, m.main());
    }

    private void installQuiet(QuestSet set) {
        if (set.kind == QuestKind.ISLAND) {
            islands.put(set.owner, set);
        } else {
            profiles.computeIfAbsent(set.owner, k -> new Boards()).put(set);
        }
        m.refreshMenus();
    }

    // ================================================================== swaps

    /** Free swaps left today for the daily set of a profile. */
    int swapsLeft(QuestSet daily) {
        return Math.max(0, m.settings.dailyRerolls - daily.rerollsUsed);
    }

    /** Swaps an open daily quest for another one (free swaps per day). Tells the player the result. Main thread. */
    void swap(Player player, int slot) {
        UUID profile = m.profileKey(player);
        Optional<QuestSet> found = set(profile, QuestKind.DAILY);
        if (found.isEmpty()) {
            m.fail(player, "swap.gone");
            return;
        }
        QuestSet set = found.get();
        Optional<Quest> oldQuest = set.slot(slot);
        if (oldQuest.isEmpty() || oldQuest.get().done() || oldQuest.get().completing) {
            m.fail(player, "swap.done");
            return;
        }
        if (swapsLeft(set) <= 0) {
            m.fail(player, "swap.none-left");
            return;
        }
        if (!swapping.add(player.getUniqueId())) {
            m.fail(player, "busy");
            return;
        }
        Quest old = oldQuest.get();
        Set<QuestType> taken = new HashSet<>();
        for (Quest q : set.quests) {
            taken.add(q.type);
        }
        List<QuestPool.Entry> picks = QuestPool.pick(m.eligible(player, QuestKind.DAILY), 1, taken, ThreadLocalRandom.current());
        if (picks.isEmpty()) {
            swapping.remove(player.getUniqueId());
            m.fail(player, "swap.no-other");
            return;
        }
        Quest fresh = create(player, QuestKind.DAILY, slot, picks.getFirst(), m.tier(player));
        int before = set.rerollsUsed;
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        m.core.database().transaction(c -> QuestStore.swap(c, set, before, old, fresh, now)).whenCompleteAsync((ok, error) -> {
            swapping.remove(playerId);
            Player p = Bukkit.getPlayer(playerId);
            if (error != null || !Boolean.TRUE.equals(ok)) {
                if (error != null) {
                    logError("swapping a quest", error);
                }
                if (p != null) {
                    m.fail(p, error != null ? "error" : "swap.changed");
                }
                reload(set);
                return;
            }
            set.rerollsUsed = before + 1;
            set.replace(fresh);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("profile", profile.toString());
            d.put("slot", slot);
            d.put("old", old.type.id() + " " + old.progress + "/" + old.target);
            d.put("new", fresh.type.id() + " " + fresh.target);
            d.put("swaps_used", set.rerollsUsed);
            m.audit("quest_swapped", p == null ? Actor.SYSTEM : Actor.player(p), playerId, p == null ? null : p.getName(), null, d);
            if (p != null) {
                m.text().swapped(p, fresh);
            }
            m.refreshMenus();
        }, m.main());
    }

    // ================================================================== saving

    /** Saves every changed progress (and island contributions). Main thread; the writes run async. */
    void saveAll() {
        for (Boards b : profiles.values()) {
            save(b.daily);
            save(b.weekly);
        }
        for (QuestSet s : islands.values()) {
            save(s);
        }
    }

    private void save(QuestSet set) {
        if (set == null) {
            return;
        }
        Map<UUID, Long> progress = new HashMap<>();
        for (Quest q : set.quests) {
            if (q.dirty()) {
                progress.put(q.uid, q.progress);
            }
        }
        Map<UUID, Map<UUID, Long>> contributions = new HashMap<>();
        if (set.kind == QuestKind.ISLAND) {
            for (Map.Entry<UUID, Map<UUID, Long>> e : set.contributions.entrySet()) {
                Map<UUID, Long> saved = set.savedContributions.getOrDefault(e.getKey(), Map.of());
                for (Map.Entry<UUID, Long> h : e.getValue().entrySet()) {
                    if (!h.getValue().equals(saved.get(h.getKey()))) {
                        contributions.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(h.getKey(), h.getValue());
                    }
                }
            }
        }
        if (progress.isEmpty() && contributions.isEmpty()) {
            return;
        }
        Map<UUID, UUID> players = new HashMap<>(set.helperPlayers);
        long now = System.currentTimeMillis();
        m.core.database().transaction(c -> {
            for (Map.Entry<UUID, Long> e : progress.entrySet()) {
                QuestStore.saveProgress(c, e.getKey(), e.getValue(), now);
            }
            for (Map.Entry<UUID, Map<UUID, Long>> e : contributions.entrySet()) {
                for (Map.Entry<UUID, Long> h : e.getValue().entrySet()) {
                    QuestStore.saveContribution(c, e.getKey(), h.getKey(), players.getOrDefault(h.getKey(), h.getKey()), h.getValue());
                }
            }
            return true;
        }).whenCompleteAsync((ok, error) -> {
            if (error != null) {
                logError("saving quest progress", error);
                return;
            }
            for (Quest q : set.quests) {
                Long p = progress.get(q.uid);
                if (p != null) {
                    q.saved = Math.max(q.saved, p);
                }
            }
            for (Map.Entry<UUID, Map<UUID, Long>> e : contributions.entrySet()) {
                set.savedContributions.computeIfAbsent(e.getKey(), k -> new HashMap<>()).putAll(e.getValue());
            }
        }, m.main());
    }

    /** Saves everything synchronously (module disable). */
    void saveAllBlocking() {
        long now = System.currentTimeMillis();
        List<QuestSet> sets = new ArrayList<>(islands.values());
        for (Boards b : profiles.values()) {
            if (b.daily != null) {
                sets.add(b.daily);
            }
            if (b.weekly != null) {
                sets.add(b.weekly);
            }
        }
        try {
            m.core.database().blockingTransaction(c -> {
                for (QuestSet set : sets) {
                    for (Quest q : set.quests) {
                        if (q.dirty()) {
                            QuestStore.saveProgress(c, q.uid, q.progress, now);
                        }
                    }
                    for (Map.Entry<UUID, Map<UUID, Long>> e : set.contributions.entrySet()) {
                        for (Map.Entry<UUID, Long> h : e.getValue().entrySet()) {
                            QuestStore.saveContribution(c, e.getKey(), h.getKey(), set.helperPlayers.getOrDefault(h.getKey(), h.getKey()),
                                    h.getValue());
                        }
                    }
                }
                return true;
            });
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Quests: could not save progress on shutdown", e);
        }
    }

    // ================================================================== tick

    /**
     * Every save interval: saves progress, rolls new windows for online players (midnight, Monday), retries payouts that
     * failed and drops sets nobody online uses any more. Main thread.
     */
    void tick() {
        updateWindows();
        saveAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        for (Boards b : profiles.values()) {
            retry(b.daily);
            retry(b.weekly);
        }
        islands.values().forEach(this::retry);
        cleanup();
    }

    private void retry(QuestSet set) {
        if (set == null || set.period != period(set.kind)) {
            return;
        }
        for (Quest q : set.quests) {
            if (!q.done() && !q.completing && q.reached()) {
                complete(set, q);
            }
        }
    }

    /** Forgets profiles that are not online any more and islands with no online member (after saving them). */
    private void cleanup() {
        Set<UUID> onlineProfiles = new HashSet<>();
        Set<UUID> onlineIslands = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlineProfiles.add(m.profileKey(p));
            UUID island = m.countingIsland(p);
            if (island != null) {
                onlineIslands.add(island);
            }
        }
        cleanup(profiles.entrySet().iterator(), onlineProfiles, true);
        cleanup(islands.entrySet().iterator(), onlineIslands, false);
        long now = System.currentTimeMillis();
        emptyRolls.values().removeIf(t -> now - t > 3_600_000L);
    }

    private <T> void cleanup(Iterator<Map.Entry<UUID, T>> it, Set<UUID> keep, boolean boards) {
        while (it.hasNext()) {
            Map.Entry<UUID, T> e = it.next();
            if (keep.contains(e.getKey())) {
                continue;
            }
            boolean busy;
            if (boards) {
                Boards b = (Boards) e.getValue();
                busy = busy(b.daily) || busy(b.weekly);
            } else {
                busy = busy((QuestSet) e.getValue());
            }
            if (!busy) {
                it.remove();
            }
        }
    }

    /** True while a set still has unsaved progress or a running payout. */
    private boolean busy(QuestSet set) {
        if (set == null) {
            return false;
        }
        for (Quest q : set.quests) {
            if (q.dirty() || q.completing) {
                return true;
            }
        }
        for (Map.Entry<UUID, Map<UUID, Long>> e : set.contributions.entrySet()) {
            if (!e.getValue().equals(set.savedContributions.getOrDefault(e.getKey(), Map.of()))) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== player leaves, staff, deletes

    /** Saves the quitting player's sets now (they stay in memory until the next cleanup). */
    void quit(Player player) {
        Boards b = profiles.get(m.profileKey(player));
        if (b != null) {
            save(b.daily);
            save(b.weekly);
        }
    }

    /** Forgets a profile or island (deleted): its rows are removed by the module. */
    void forget(UUID owner) {
        profiles.remove(owner);
        islands.remove(owner);
    }

    /** Staff reset: removes the current set of a board and rolls a new one. */
    CompletableFuture<Integer> reset(Player target, QuestKind kind) {
        UUID owner = kind == QuestKind.ISLAND ? m.countingIsland(target) : m.profileKey(target);
        if (owner == null) {
            return CompletableFuture.completedFuture(-1);
        }
        int period = period(kind);
        QuestSet have = current(kind, owner);
        if (have != null && have.period == period) {
            for (Quest q : have.quests) {
                if (q.completing) {
                    return CompletableFuture.completedFuture(-2);
                }
            }
        }
        if (kind == QuestKind.ISLAND) {
            islands.remove(owner);
        } else {
            Boards b = profiles.get(owner);
            if (b != null && b.get(kind) != null) {
                if (kind == QuestKind.DAILY) {
                    b.daily = null;
                } else {
                    b.weekly = null;
                }
            }
        }
        emptyRolls.remove(key(kind, owner, period));
        return m.core.database().transaction(c -> QuestStore.deleteSet(c, kind, owner, period)).whenCompleteAsync((n, e) -> {
            if (target.isOnline()) {
                load(target);
            }
        }, m.main());
    }

    /** Staff complete: sets a quest's progress to its target (the normal payout follows). False when there is no such open quest. */
    boolean forceComplete(Player target, QuestKind kind, int slot) {
        Optional<QuestSet> set = kind == QuestKind.ISLAND ? island(m.countingIsland(target)) : set(m.profileKey(target), kind);
        if (set.isEmpty()) {
            return false;
        }
        Optional<Quest> q = set.get().slot(slot);
        if (q.isEmpty() || q.get().done()) {
            return false;
        }
        Quest quest = q.get();
        long before = quest.add(quest.target);
        if (kind == QuestKind.ISLAND) {
            set.get().contribute(quest.uid, m.profileKey(target), target.getUniqueId(), quest.progress - before);
        }
        complete(set.get(), quest);
        return true;
    }

    private void logError(String what, Throwable error) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog < 30_000L) {
            return;
        }
        lastErrorLog = now;
        if (error == null) {
            m.core.plugin().getLogger().warning("Quests: failed " + what);
        } else {
            m.core.plugin().getLogger().log(Level.WARNING, "Quests: failed " + what, Database.cause(error));
        }
    }
}
