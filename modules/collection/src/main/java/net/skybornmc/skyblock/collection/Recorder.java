package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Records finds (main thread). Online profiles have a cached {@link ProfileLog}; a find that is new is shown at once and
 * saved in the next batch ({@code save.seconds}). When a find reaches a milestone, the waiting finds of that profile, the
 * milestone rows and their hand-outs are saved together in one transaction right away ({@link CollectionStore#commit}).
 * Finds of offline profiles (a crate open that ended after quitting) are only saved; their milestones are checked when the
 * profile is loaded next, which also catches up after crashes and catalog changes.
 */
final class Recorder {

    private static final long LOAD_RETRY_TICKS = 20L * 30;

    private final CollectionModule m;
    private final Map<UUID, ProfileLog> logs = new HashMap<>();
    private final Map<UUID, List<CollectionStore.EntryRow>> pending = new LinkedHashMap<>();
    /** Page milestones per profile (island level provider; read on the main thread, written here). */
    private final Map<UUID, Integer> pageMilestones = new ConcurrentHashMap<>();
    private long lastSaveWarning;

    Recorder(CollectionModule module) {
        this.m = module;
    }

    // ================================================================== state

    ProfileLog log(UUID profile) {
        return logs.get(profile);
    }

    /** Page milestones of a profile (0 when none). Any thread. */
    int pageMilestones(UUID profile) {
        return pageMilestones.getOrDefault(profile, 0);
    }

    /** Island level points of the Collections source: every member's page milestones × level-points. Main thread. */
    double islandPoints(UUID islandId) {
        IslandService islands = m.islands().orElse(null);
        if (islands == null || m.settings.levelPoints <= 0) {
            return 0;
        }
        long n = 0;
        for (UUID member : islands.members(islandId).keySet()) {
            n += pageMilestones(member);
        }
        return n * m.settings.levelPoints;
    }

    /** Startup: the page milestone counts of every profile (one small grouped query). */
    void loadCounts() {
        pageMilestones.clear();
        pageMilestones.putAll(m.core.database().blocking(CollectionStore::pageMilestoneCounts));
    }

    // ================================================================== loading

    /** Loads the log of the profile the player is on now (join, switch, enable). */
    void load(Player player) {
        UUID profile = m.profiles().profileKey(player);
        ProfileLog log = logs.get(profile);
        if (log == null) {
            start(profile);
        }
    }

    private ProfileLog start(UUID profile) {
        ProfileLog log = new ProfileLog(profile);
        logs.put(profile, log);
        fetch(log);
        return log;
    }

    private void fetch(ProfileLog log) {
        UUID profile = log.profile();
        m.core.database().async(c -> new Stored(CollectionStore.entries(c, profile), CollectionStore.milestones(c, profile)))
                .whenCompleteAsync((stored, e) -> {
                    if (logs.get(profile) != log) {
                        return; // forgotten or replaced meanwhile
                    }
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not load the log of profile " + profile
                                + "; trying again in 30 s (finds wait meanwhile)", Database.cause(e));
                        m.core.scheduler().later("collection:load-retry", LOAD_RETRY_TICKS, () -> {
                            if (logs.get(profile) == log && !log.loaded()) {
                                fetch(log);
                            }
                        });
                        return;
                    }
                    Map<String, ProfileLog.Found> found = new HashMap<>(stored.entries());
                    // Finds saved in the batch that is still waiting (e.g. an offline crate open a moment ago) count as stored.
                    for (CollectionStore.EntryRow row : pending.getOrDefault(profile, List.of())) {
                        found.putIfAbsent(row.entry(), new ProfileLog.Found(row.at(), row.source()));
                    }
                    for (ProfileLog.NewFind f : log.loaded(found, stored.milestones())) {
                        announce(log, f);
                    }
                    checkMilestones(log);
                    m.refreshMenu(profile);
                }, m.main());
    }

    private record Stored(Map<String, ProfileLog.Found> entries, Set<String> milestones) {
    }

    /** The player left this profile (quit or switch): its cache goes; waiting finds are still saved by the batch. */
    void forget(UUID profile) {
        logs.remove(profile);
    }

    /** A deleted profile: cache, waiting finds and level points go (the rows are deleted by the caller). */
    void deleted(UUID profile) {
        logs.remove(profile);
        pending.remove(profile);
        pageMilestones.remove(profile);
    }

    void clear() {
        logs.clear();
    }

    // ================================================================== finds

    /**
     * A find from a source (main thread). Ids that are not in the catalog are ignored.
     *
     * @param player the player who found it when online on that profile, else null
     */
    void found(UUID profile, Player player, String entryId, String source) {
        if (profile == null || m.catalog().entry(entryId) == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ProfileLog log = logs.get(profile);
        if (log == null) {
            if (player == null) {
                queue(new CollectionStore.EntryRow(profile, entryId, now, source)); // offline: milestones checked at the next load
                return;
            }
            log = start(profile);
        }
        ProfileLog.NewFind f = log.add(entryId, now, source);
        if (f != null) {
            announce(log, f);
            checkMilestones(log);
            m.refreshMenu(profile);
        }
    }

    /** A new find: saved in the next batch, logged and shown. */
    private void announce(ProfileLog log, ProfileLog.NewFind f) {
        UUID profile = log.profile();
        queue(new CollectionStore.EntryRow(profile, f.entry(), f.at(), f.source()));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("entry", f.entry());
        d.put("source", f.source());
        d.put("profile", profile.toString());
        m.audit("unlock", Actor.SYSTEM, m.profiles().ownerOf(profile), 1L, d);
        Player p = m.receiver(profile);
        if (p != null) {
            m.notices().find(p, log, f.entry());
        }
    }

    private void queue(CollectionStore.EntryRow row) {
        pending.computeIfAbsent(row.profile(), k -> new ArrayList<>()).add(row);
    }

    // ================================================================== milestones

    /** Writes every milestone the profile reaches now and does not have yet (one transaction with the waiting finds). */
    void checkMilestones(ProfileLog log) {
        if (!log.loaded()) {
            return;
        }
        Catalog catalog = m.catalog();
        List<String> ids = log.startWriting(Milestones.newlyReached(catalog, log.foundIds(), log.milestones(), m.percents()));
        if (ids.isEmpty()) {
            return;
        }
        UUID profile = log.profile();
        UUID player = m.profiles().ownerOf(profile);
        List<CollectionStore.EntryRow> entries = pending.remove(profile);
        List<CollectionStore.EntryRow> rows = entries == null ? List.of() : entries;
        Milestones.Rewards rewards = m.rewards();
        long now = System.currentTimeMillis();
        m.core.database().transaction(c -> CollectionStore.commit(c, profile, player, rows, ids, id -> Milestones.grants(id, rewards), now))
                .whenCompleteAsync((done, e) -> {
                    if (e != null) {
                        log.doneWriting(ids, false);
                        requeue(rows);
                        m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not save milestones " + ids + " of profile "
                                + profile + "; they are checked again on the next find or join", Database.cause(e));
                        return;
                    }
                    log.doneWriting(ids, true); // the ones not written were stored already: reached either way
                    reached(log, player, done);
                }, m.main());
    }

    private void reached(ProfileLog log, UUID player, CollectionStore.Committed done) {
        if (done.milestones().isEmpty()) {
            return;
        }
        UUID profile = log.profile();
        int pageCount = (int) done.milestones().stream().filter(Milestones::isPageMilestone).count();
        if (pageCount > 0) {
            pageMilestones.merge(profile, pageCount, Integer::sum);
            m.islands().flatMap(i -> i.islandOfProfile(profile)).ifPresent(island ->
                    m.core.services().optional(ProgressionService.class).ifPresent(p -> p.requestLevelUpdate(island.id())));
        }
        for (String id : done.milestones()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("milestone", id);
            d.put("profile", profile.toString());
            d.put("rewards", done.grants().stream().filter(g -> g.milestone().equals(id)).map(g -> g.kind() + ":" + g.ref() + "x" + g.amount())
                    .toList().toString());
            m.audit("milestone", Actor.SYSTEM, player, (long) Milestones.percentOf(id), d);
        }
        Player p = m.receiver(profile);
        if (p != null) {
            m.notices().milestones(p, done.milestones());
        }
        m.grants().handOut(done.grants());
        m.refreshMenu(profile);
    }

    // ================================================================== saving

    private void requeue(List<CollectionStore.EntryRow> rows) {
        for (CollectionStore.EntryRow row : rows) {
            queue(row);
        }
    }

    /** Periodic batch save of the waiting finds (one transaction). Main thread. */
    void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<CollectionStore.EntryRow> rows = drain();
        m.core.database().transaction(c -> {
            CollectionStore.insertEntries(c, rows);
            return true;
        }).whenCompleteAsync((v, e) -> {
            if (e != null) {
                requeue(rows);
                long now = System.currentTimeMillis();
                if (now - lastSaveWarning > 60_000L) {
                    lastSaveWarning = now;
                    m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not save " + rows.size()
                            + " find(s); trying again with the next batch", Database.cause(e));
                }
            }
        }, m.main());
    }

    /** Shutdown: saves the waiting finds and waits for it. */
    void flushBlocking() {
        if (pending.isEmpty()) {
            return;
        }
        List<CollectionStore.EntryRow> rows = drain();
        try {
            m.core.database().blockingTransaction(c -> {
                CollectionStore.insertEntries(c, rows);
                return true;
            });
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Collection: could not save " + rows.size() + " find(s) at shutdown "
                    + "(they are found again on the next drop)", Database.cause(e));
        }
    }

    private List<CollectionStore.EntryRow> drain() {
        List<CollectionStore.EntryRow> rows = new ArrayList<>();
        pending.values().forEach(rows::addAll);
        pending.clear();
        return rows;
    }
}
