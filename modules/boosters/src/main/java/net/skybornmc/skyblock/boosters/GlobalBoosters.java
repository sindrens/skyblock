package net.skybornmc.skyblock.boosters;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Global boosters (main thread): everyone online gets the bonus, one runs per type and the rest wait in a queue. A boss
 * bar counts down for everyone (unless hidden in /settings). {@code /thanks} or saying thanks in chat gives the starter
 * {@code thanks.gems} gems, once per booster per player (RW2). Time left counts down while the server runs and is saved
 * every {@code global.save-seconds}; every database step runs in order on one chain.
 */
final class GlobalBoosters {

    private final BoostersModule m;
    private final GlobalQueue queue = new GlobalQueue();
    private final List<GlobalQueue.Entry> recent = new ArrayList<>();
    private final Map<Long, BossBar> bars = new HashMap<>();
    /** What each shown bar's text was built from (PERF6). */
    private final Map<Long, BarText> barTexts = new HashMap<>();
    private final Map<Long, String> starterIps = new HashMap<>();
    private CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
    private volatile boolean ready;
    private boolean busy;
    private long lastTick;
    private long lastSave;

    GlobalBoosters(BoostersModule module) {
        this.m = module;
    }

    GlobalQueue queue() {
        return queue;
    }

    boolean ready() {
        return ready;
    }

    /** Percent the running global booster of a type adds to everyone (0 for none). Main thread. */
    int percent(BoosterType type) {
        return ready ? queue.percent(type) : 0;
    }

    private synchronized <T> CompletableFuture<T> chain(Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> next = chain.handle((v, e) -> null).thenCompose(v -> {
            try {
                return work.get();
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
        chain = next;
        return next;
    }

    // ================================================================== load

    void load() {
        chain(() -> m.core.database().async(c -> {
            List<BoosterStore.GlobalRow> rows = BoosterStore.globals(c);
            Map<Long, java.util.Set<UUID>> thanked = new HashMap<>();
            for (BoosterStore.GlobalRow r : rows) {
                if (BoosterStore.STATE_ACTIVE.equals(r.state())) {
                    thanked.put(r.id(), BoosterStore.thanked(c, r.id()));
                }
            }
            return Map.entry(rows, thanked);
        })).whenCompleteAsync((data, error) -> m.core.scheduler().timer().time("boosters:global-load", () -> {
            // PERF8: this main-thread step (queue, promotions, first bars for everyone) was not timed before.
            if (error != null) {
                m.core.plugin().getLogger().log(Level.SEVERE, "Boosters: could not load global boosters (they resume at the next start)",
                        Database.cause(error));
                ready = true;
                return;
            }
            queue.clear();
            for (BoosterStore.GlobalRow r : data.getKey()) {
                boolean active = BoosterStore.STATE_ACTIVE.equals(r.state());
                if (active && queue.active(r.type()).isPresent()) {
                    active = false; // never two of a type: the newer waits
                }
                GlobalQueue.Entry e = new GlobalQueue.Entry(r.id(), r.type(), r.percent(), r.duration(), r.remaining(), r.starter(),
                        r.starterProfile(), r.starterName(), active);
                e.thanked.addAll(data.getValue().getOrDefault(r.id(), java.util.Set.of()));
                queue.add(e);
            }
            for (GlobalQueue.Entry started : queue.promote()) {
                persistStart(started);
            }
            ready = true;
            refreshBars();
            retryUnpaid();
        }), m.main());
    }

    // ================================================================== tick

    void tick() {
        if (!ready) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean first = lastTick == 0;
        long elapsed = first ? 0 : Math.min(10_000, Math.max(0, now - lastTick));
        lastTick = now;
        if (first) {
            lastSave = now; // PERF8: the next save is one period from now (nothing to save yet, see BoosterRules.saveDue)
        }
        GlobalQueue.Tick t = queue.tick(elapsed, now);
        for (GlobalQueue.Entry e : t.ended()) {
            long id = e.id;
            chain(() -> m.core.database().run(c -> BoosterStore.endGlobal(c, id, now))).exceptionally(err -> {
                m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not end global booster " + id, Database.cause(err));
                return null;
            });
            recent.add(e);
            hideBar(id);
            m.audit("global_end", Actor.SYSTEM, e.starter, e.starterName, (long) e.percent, Map.of("id", id, "type", e.type.id(),
                    "thanks", e.thanked.size()));
            if (m.settings.announce) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    m.msg.send(p, "global.ended", Msg.p("type", m.typeName(e.type)), Msg.number("percent", e.percent),
                            Msg.p("name", e.starterName), Msg.number("thanks", e.thanked.size()));
                }
            }
        }
        for (GlobalQueue.Entry e : t.started()) {
            persistStart(e);
            announceStart(e, false);
        }
        long grace = m.settings.thanksGraceSeconds * BoosterRules.SECOND;
        recent.removeIf(e -> now - e.endedAt > grace);
        if (BoosterRules.saveDue(first, lastSave, now, m.settings.globalSaveSeconds * BoosterRules.SECOND)) {
            lastSave = now;
            save();
        }
        m.core.scheduler().timer().time("boosters:tick-bars", this::updateBars);
    }

    private void persistStart(GlobalQueue.Entry e) {
        long id = e.id;
        long now = System.currentTimeMillis();
        chain(() -> m.core.database().run(c -> BoosterStore.activateGlobal(c, id, now))).exceptionally(err -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not start queued global booster " + id, Database.cause(err));
            return null;
        });
        m.audit("global_start", Actor.SYSTEM, e.starter, e.starterName, (long) e.percent, Map.of("id", id, "type", e.type.id(),
                "minutes", e.duration / BoosterRules.MINUTE, "from_queue", true));
    }

    private void save() {
        List<long[]> rows = new ArrayList<>();
        for (GlobalQueue.Entry e : queue.active()) {
            rows.add(new long[]{e.id, e.remaining});
        }
        if (rows.isEmpty()) {
            return;
        }
        chain(() -> m.core.database().run(c -> {
            for (long[] r : rows) {
                BoosterStore.saveGlobal(c, r[0], r[1]);
            }
        })).exceptionally(err -> {
            m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not save global booster time", Database.cause(err));
            return null;
        });
    }

    /** Server stop: saves the time left now and hides the bars. */
    void stopBlocking() {
        try {
            m.core.database().blocking(c -> {
                for (GlobalQueue.Entry e : queue.active()) {
                    BoosterStore.saveGlobal(c, e.id, e.remaining);
                }
                return null;
            });
        } catch (RuntimeException e) {
            m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not save global booster time", e);
        }
        for (Long id : List.copyOf(bars.keySet())) {
            hideBar(id);
        }
        ready = false;
    }

    // ================================================================== boss bars

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(m.settings.bossBarColor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.YELLOW;
        }
    }

    /** Every input of {@link #barName} (PERF6: the bar text is parsed again only when one of them changes). */
    record BarText(BoosterType type, int percent, String time, String name, int queued, long messages) {
    }

    private BarText barText(GlobalQueue.Entry e) {
        return new BarText(e.type, e.percent, BoosterRules.time(e.remaining), e.starterName, queue.queued(e.type).size(),
                m.core.messages().generation());
    }

    private Component barName(GlobalQueue.Entry e) {
        return m.msg.component(Audience.empty(), "global.bar", Msg.p("type", m.typeName(e.type)), Msg.number("percent", e.percent),
                Msg.p("time", BoosterRules.time(e.remaining)), Msg.p("name", e.starterName),
                Msg.number("queued", queue.queued(e.type).size()));
    }

    /** Shows the bars of every running global booster to the players who want them. */
    void refreshBars() {
        if (!m.settings.bossBar) {
            for (Long id : List.copyOf(bars.keySet())) {
                hideBar(id);
            }
            return;
        }
        for (GlobalQueue.Entry e : queue.active()) {
            BossBar bar = bars.computeIfAbsent(e.id, id -> {
                // PERF8: remember what the new bar shows, so the next tick doesn't parse the same text a second time.
                barTexts.put(id, barText(e));
                return BossBar.bossBar(barName(e), BoosterRules.progress(e.remaining, e.duration), color(), BossBar.Overlay.PROGRESS);
            });
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (m.wantsBar(p)) {
                    p.showBossBar(bar);
                } else {
                    p.hideBossBar(bar);
                }
            }
        }
    }

    private void updateBars() {
        if (!m.settings.bossBar) {
            return;
        }
        boolean added = false;
        for (GlobalQueue.Entry e : queue.active()) {
            BossBar bar = bars.get(e.id);
            if (bar == null) {
                added = true;
                continue;
            }
            BarText text = barText(e);
            if (!text.equals(barTexts.get(e.id))) { // PERF6: parse the bar text only when what it shows changed
                barTexts.put(e.id, text);
                bar.name(barName(e));
            }
            bar.progress(BoosterRules.progress(e.remaining, e.duration));
        }
        if (added) {
            refreshBars();
        }
    }

    private void hideBar(long id) {
        barTexts.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
    }

    void joined(Player p) {
        if (m.settings.bossBar && m.wantsBar(p)) {
            bars.values().forEach(p::showBossBar);
        }
    }

    void setting(Player p, boolean show) {
        for (BossBar bar : bars.values()) {
            if (show && m.settings.bossBar) {
                p.showBossBar(bar);
            } else {
                p.hideBossBar(bar);
            }
        }
    }

    // ================================================================== starting an item

    /** Starts a global booster item (taken first; back on refusal or error). Queued when one of the type runs. */
    void start(Player p, ItemStack stack, BoosterItems.Spec spec) {
        if (!ready) {
            m.msg.send(p, "loading");
            return;
        }
        if (!m.settings.globalEnabled) {
            m.msg.send(p, "global.disabled");
            return;
        }
        if (busy) {
            m.msg.send(p, "busy");
            return;
        }
        int maxQueue = m.settings.globalMaxQueue;
        if (queue.active(spec.type()).isPresent() && queue.queued(spec.type()).size() >= maxQueue) {
            m.msg.send(p, "global.queue-full", Msg.p("type", m.typeName(spec.type())), Msg.number("max", maxQueue));
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        Optional<ItemStack> taken = m.take(p, stack, spec.itemUuid());
        if (taken.isEmpty()) {
            m.msg.send(p, "moved");
            return;
        }
        busy = true;
        UUID player = p.getUniqueId();
        String name = p.getName();
        String ip = ip(p);
        long now = System.currentTimeMillis();
        chain(() -> m.core.database().transaction(c -> BoosterStore.startGlobal(c, spec.itemUuid(), spec.type(), spec.percent(),
                spec.millis(), player, profile, name, maxQueue, now))).whenCompleteAsync((r, error) -> {
            busy = false;
            Player online = Bukkit.getPlayer(player);
            if (error != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not start a global booster of " + name, Database.cause(error));
                m.giveBack(online, profile, taken.get());
                if (online != null) {
                    m.msg.send(online, "error");
                }
                return;
            }
            if (r.outcome() == BoosterRules.Outcome.ALREADY_USED) {
                m.reuse(online, player, name, spec, profile);
                return;
            }
            if (r.row() == null) {
                m.giveBack(online, profile, taken.get());
                if (online != null) {
                    m.msg.send(online, "global.queue-full", Msg.p("type", m.typeName(spec.type())), Msg.number("max", maxQueue));
                }
                return;
            }
            BoosterStore.GlobalRow row = r.row();
            boolean active = BoosterStore.STATE_ACTIVE.equals(row.state());
            if (active && queue.active(row.type()).isPresent()) {
                active = false; // cannot happen with one chain; keep one per type anyway
            }
            GlobalQueue.Entry e = new GlobalQueue.Entry(row.id(), row.type(), row.percent(), row.duration(), row.remaining(), player,
                    profile, row.starterName(), active);
            queue.add(e);
            if (ip != null) {
                starterIps.put(row.id(), ip);
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", row.id());
            d.put("type", row.type().id());
            d.put("minutes", spec.minutes());
            d.put("item", spec.itemUuid().toString());
            d.put("profile", profile.toString());
            d.put("queued", !active);
            m.audit(active ? "global_start" : "global_queue", online != null ? Actor.player(online) : Actor.player(player, name), player,
                    name, (long) spec.percent(), d);
            if (online != null && m.settings.savePlayerData) {
                PlayerSaves.save(online, "a global booster start");
            }
            if (active) {
                announceStart(e, true);
                refreshBars();
            } else {
                long wait = waitBefore(e);
                if (online != null) {
                    m.msg.send(online, "global.queued", Msg.p("type", m.typeName(row.type())), Msg.number("percent", row.percent()),
                            Msg.number("position", queue.queued(row.type()).indexOf(e) + 1), Msg.p("wait", BoosterRules.time(wait)));
                    m.sound(online, m.settings.startSound);
                }
                if (m.settings.announce) {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (!other.getUniqueId().equals(player)) {
                            m.msg.send(other, "global.queued-all", Msg.p("name", name), Msg.p("type", m.typeName(row.type())),
                                    Msg.number("percent", row.percent()), Msg.p("wait", BoosterRules.time(wait)));
                        }
                    }
                }
            }
            if (online != null) {
                m.refreshMenu(online);
            }
        }, m.main());
    }

    /** Time until a queued booster starts: the running one plus everything before it in the queue. */
    long waitBefore(GlobalQueue.Entry e) {
        long wait = queue.active(e.type).map(a -> a.remaining).orElse(0L);
        for (GlobalQueue.Entry q : queue.queued(e.type)) {
            if (q == e) {
                break;
            }
            wait += q.remaining;
        }
        return wait;
    }

    private void announceStart(GlobalQueue.Entry e, boolean fresh) {
        Player starter = Bukkit.getPlayer(e.starter);
        if (starter != null) {
            m.msg.send(starter, "global.started-own", Msg.p("type", m.typeName(e.type)), Msg.number("percent", e.percent),
                    Msg.p("time", BoosterRules.time(e.remaining)));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (m.settings.announce && !p.getUniqueId().equals(e.starter)) {
                m.msg.send(p, fresh ? "global.started" : "global.started-queued", Msg.p("name", e.starterName),
                        Msg.p("type", m.typeName(e.type)), Msg.number("percent", e.percent), Msg.p("time", BoosterRules.time(e.remaining)));
            }
            m.sound(p, m.settings.globalSound);
        }
    }

    // ================================================================== thanks

    private static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null || a.getAddress() == null ? null : a.getAddress().getHostAddress();
    }

    /** Global boosters this player can still thank (running or just ended, not their own, not thanked yet). */
    List<GlobalQueue.Entry> thankable(Player p) {
        List<GlobalQueue.Entry> out = new ArrayList<>();
        UUID id = p.getUniqueId();
        for (GlobalQueue.Entry e : queue.active()) {
            if (!e.starter.equals(id) && !e.thanked.contains(id)) {
                out.add(e);
            }
        }
        for (GlobalQueue.Entry e : recent) {
            if (!e.starter.equals(id) && !e.thanked.contains(id)) {
                out.add(e);
            }
        }
        return out;
    }

    /** True while any global booster runs or just ended (for the command's feedback). */
    boolean anyRecent() {
        return !queue.active().isEmpty() || !recent.isEmpty();
    }

    /** {@code /thanks} or a thanks in chat ({@code quiet}: say nothing when there is nothing to thank). */
    void thank(Player p, boolean quiet) {
        if (!m.settings.thanksEnabled) {
            if (!quiet) {
                m.msg.send(p, "thanks.disabled");
            }
            return;
        }
        List<GlobalQueue.Entry> targets = thankable(p);
        if (targets.isEmpty()) {
            if (!quiet) {
                boolean own = queue.active().stream().anyMatch(e -> e.starter.equals(p.getUniqueId()));
                m.msg.send(p, !anyRecent() ? "thanks.none" : own && queue.active().size() == 1 ? "thanks.own" : "thanks.already");
            }
            return;
        }
        UUID thanker = p.getUniqueId();
        String thankerName = p.getName();
        String myIp = ip(p);
        long now = System.currentTimeMillis();
        for (GlobalQueue.Entry e : targets) {
            e.thanked.add(thanker);
            Player starter = Bukkit.getPlayer(e.starter);
            String starterIp = starter != null ? ip(starter) : starterIps.get(e.id);
            boolean sameIp = myIp != null && myIp.equals(starterIp);
            boolean gem = m.settings.thanksGems > 0 && (!sameIp || m.settings.thanksSameIp);
            long id = e.id;
            int maxGems = m.settings.thanksMaxGems;
            m.core.database().transaction(c -> BoosterStore.thank(c, id, thanker, gem, maxGems, now)).whenCompleteAsync((state, error) -> {
                if (error != null) {
                    e.thanked.remove(thanker);
                    m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not store a thanks", Database.cause(error));
                    Player online = Bukkit.getPlayer(thanker);
                    if (online != null) {
                        m.msg.send(online, "error");
                    }
                    return;
                }
                if (state < 0) {
                    return; // already thanked
                }
                boolean pays = state == BoosterStore.GEM_PENDING;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", id);
                d.put("type", e.type.id());
                d.put("starter", e.starter.toString());
                d.put("gem", pays);
                d.put("same_ip", sameIp);
                m.audit("thanks", Actor.player(thanker, thankerName), e.starter, e.starterName, pays ? (long) m.settings.thanksGems : 0L, d);
                Player online = Bukkit.getPlayer(thanker);
                if (online != null) {
                    m.msg.send(online, pays ? "thanks.sent" : "thanks.sent-no-gem", Msg.p("name", e.starterName),
                            Msg.p("type", m.typeName(e.type)), Msg.number("gems", m.settings.thanksGems));
                }
                Player st = Bukkit.getPlayer(e.starter);
                if (st != null) {
                    m.msg.actionBar(st, pays ? "thanks.received" : "thanks.received-no-gem", Msg.p("name", thankerName),
                            Msg.number("gems", m.settings.thanksGems));
                }
                if (pays) {
                    pay(id, thanker, e.starterProfile);
                }
            }, m.main());
        }
    }

    /** Pays one thanks gem (idempotent key, so a retry after a crash never pays twice), then marks it paid. */
    private void pay(long globalId, UUID thanker, UUID starterProfile) {
        Optional<EconomyService> eco = m.core.services().optional(EconomyService.class);
        if (eco.isEmpty() || m.settings.thanksGems <= 0) {
            return; // stays pending, paid at a later start
        }
        Transaction tx = Transaction.deposit(AccountRef.player(starterProfile), Currency.GEMS, m.settings.thanksGems, TxReason.BOOSTER_THANKS)
                .actor(Actor.system(BoostersModule.ID))
                .reference("booster_global:" + globalId)
                .idempotencyKey("booster_thanks:" + globalId + ":" + thanker)
                .detail("thanker", thanker.toString());
        eco.get().execute(tx).thenAccept(r -> {
            if (r.status() == TxResult.Status.SUCCESS || r.status() == TxResult.Status.DUPLICATE) {
                m.core.database().run(c -> BoosterStore.markPaid(c, globalId, thanker)).exceptionally(err -> null);
            } else {
                m.core.plugin().getLogger().warning("Boosters: a thanks gem for global booster " + globalId + " was not paid (" + r.status()
                        + "); tried again at the next start.");
            }
        });
    }

    private void retryUnpaid() {
        m.core.database().async(c -> BoosterStore.unpaid(c, 500)).whenComplete((rows, error) -> {
            if (error != null || rows == null) {
                return;
            }
            for (BoosterStore.UnpaidThanks u : rows) {
                pay(u.globalId(), u.thanker(), u.starterProfile());
            }
        });
    }

    /** Used by the menu: the active booster and its queue of a type. */
    Optional<GlobalQueue.Entry> active(BoosterType type) {
        return queue.active(type);
    }

    List<GlobalQueue.Entry> queued(BoosterType type) {
        return queue.queued(type);
    }
}
