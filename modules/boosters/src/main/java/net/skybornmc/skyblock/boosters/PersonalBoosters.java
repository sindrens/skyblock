package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Personal boosters of online players (main thread). One state per online player for their <b>active profile</b>: the
 * running booster of each type (time left counts down only while they are online on that profile, so a booster is never
 * wasted offline) and banked time. Time used is saved every {@code personal.save-seconds} as a relative subtraction;
 * every database step of a profile runs in order on that profile's chain.
 */
final class PersonalBoosters {

    /** A running booster in memory. */
    static final class Run {
        int percent;
        long remaining;

        Run(int percent, long remaining) {
            this.percent = percent;
            this.remaining = remaining;
        }

        BoosterRules.Run view() {
            return new BoosterRules.Run(percent, remaining);
        }
    }

    /** One online player's boosters on their active profile. */
    static final class State {
        final UUID player;
        final UUID profile;
        final Map<BoosterType, Run> runs = new EnumMap<>(BoosterType.class);
        final Map<BoosterType, Long> pending = new EnumMap<>(BoosterType.class);
        final Map<BoosterType, BoosterStore.Bank> banks = new EnumMap<>(BoosterType.class);
        final Set<BoosterType> bankStarting = java.util.EnumSet.noneOf(BoosterType.class);
        boolean loaded;
        boolean busy;

        State(UUID player, UUID profile) {
            this.player = player;
            this.profile = profile;
        }

        Run run(BoosterType type) {
            Run r = runs.get(type);
            return r != null && r.remaining > 0 ? r : null;
        }

        /** Takes the unsaved usage (the caller saves it or gives it back). */
        Map<BoosterType, Long> takePending() {
            Map<BoosterType, Long> out = new EnumMap<>(pending);
            pending.clear();
            return out;
        }

        void giveBack(Map<BoosterType, Long> use) {
            use.forEach((t, v) -> pending.merge(t, v, Long::sum));
        }
    }

    private final BoostersModule m;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> chains = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<String, Predicate<Player>> holds = new ConcurrentHashMap<>();
    private long lastTick;
    private long lastSave;

    PersonalBoosters(BoostersModule module) {
        this.m = module;
    }

    /** Runs {@code work} after every earlier database step of this profile (never in parallel with them). */
    <T> CompletableFuture<T> chain(UUID profile, Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        chains.compute(profile, (k, prev) -> {
            CompletableFuture<?> before = prev == null ? CompletableFuture.completedFuture(null) : prev;
            before.handle((v, e) -> null).thenCompose(v -> {
                try {
                    return work.get();
                } catch (RuntimeException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }).whenComplete((v, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                } else {
                    result.complete(v);
                }
            });
            return result;
        });
        result.whenComplete((v, e) -> chains.remove(profile, result));
        return result;
    }

    State state(Player player) {
        State s = states.get(player.getUniqueId());
        return s != null && s.loaded && s.profile.equals(m.profiles().profileKey(player)) ? s : null;
    }

    /** Percent the player's running booster of this type adds (0 for none or while loading). Main thread. */
    int percent(Player player, BoosterType type) {
        State s = state(player);
        if (s == null) {
            return 0;
        }
        Run r = s.run(type);
        return r == null ? 0 : r.percent;
    }

    void hold(String owner, Predicate<Player> hold) {
        holds.put(owner, hold);
    }

    void release(String owner) {
        holds.remove(owner);
    }

    boolean held(Player p) {
        for (Predicate<Player> h : holds.values()) {
            try {
                if (h.test(p)) {
                    return true;
                }
            } catch (RuntimeException e) {
                // a broken hold never blocks boosters
            }
        }
        return false;
    }

    void joined(Player p) {
        joinedAt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    void quit(Player p) {
        joinedAt.remove(p.getUniqueId());
        State s = states.remove(p.getUniqueId());
        if (s != null && s.loaded) {
            save(s);
        }
    }

    // ================================================================== tick (every second)

    void tick() {
        long now = System.currentTimeMillis();
        long elapsed = lastTick == 0 ? 0 : Math.min(10_000, Math.max(0, now - lastTick));
        lastTick = now;
        boolean saveNow = now - lastSave >= m.settings.saveSeconds * BoosterRules.SECOND;
        if (saveNow) {
            lastSave = now;
        }
        long delay = m.settings.joinDelayTicks * 50L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (m.profiles().isSwitching(id)) {
                continue; // paused during a switch
            }
            Long joined = joinedAt.get(id);
            if (joined != null && now - joined < delay) {
                continue;
            }
            UUID profile = m.profiles().profileKey(p);
            State s = states.get(id);
            if (s == null || !s.profile.equals(profile)) {
                if (s != null && s.loaded) {
                    save(s); // profile switched: the old profile's time is saved and its boosters pause
                }
                load(p, profile);
                continue;
            }
            if (!s.loaded) {
                continue;
            }
            count(p, s, elapsed);
            startBanks(p, s);
            if (saveNow && !s.pending.isEmpty()) {
                save(s);
            }
        }
    }

    private void count(Player p, State s, long elapsed) {
        if (elapsed <= 0 || s.runs.isEmpty()) {
            return;
        }
        boolean ended = false;
        for (Map.Entry<BoosterType, Run> e : Map.copyOf(s.runs).entrySet()) {
            Run r = e.getValue();
            if (r.remaining <= 0) {
                continue;
            }
            r.remaining -= elapsed;
            s.pending.merge(e.getKey(), elapsed, Long::sum);
            if (r.remaining <= 0) {
                s.runs.remove(e.getKey());
                ended = true;
                m.msg.send(p, "personal.ended", Msg.p("type", m.typeName(e.getKey())), Msg.number("percent", r.percent));
                m.sound(p, m.settings.endSound);
                m.audit("personal_end", Actor.SYSTEM, p.getUniqueId(), p.getName(), (long) r.percent,
                        Map.of("type", e.getKey().id(), "profile", s.profile.toString()));
            }
        }
        if (ended) {
            save(s);
        }
    }

    private void startBanks(Player p, State s) {
        if (s.banks.isEmpty() || s.busy || held(p)) {
            return;
        }
        long max = m.settings.personalMaxMillis();
        for (Map.Entry<BoosterType, BoosterStore.Bank> e : Map.copyOf(s.banks).entrySet()) {
            BoosterType type = e.getKey();
            if (s.bankStarting.contains(type)) {
                continue;
            }
            Run r = s.run(type);
            if (BoosterRules.bankMove(r == null ? null : r.view(), e.getValue().percent(), e.getValue().banked(), max) <= 0) {
                continue;
            }
            s.bankStarting.add(type);
            Map<BoosterType, Long> use = s.takePending();
            UUID player = p.getUniqueId();
            long now = System.currentTimeMillis();
            chain(s.profile, () -> m.core.database().transaction(c -> BoosterStore.startBank(c, s.profile, type, max, use, now)))
                    .whenCompleteAsync((r2, error) -> {
                        s.bankStarting.remove(type);
                        if (error != null) {
                            s.giveBack(use);
                            m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not start the banked booster of " + player,
                                    Database.cause(error));
                            return;
                        }
                        if (r2.bankLeft() > 0) {
                            s.banks.put(type, new BoosterStore.Bank(r2.percent(), r2.bankLeft(), e.getValue().source()));
                        } else {
                            s.banks.remove(type);
                        }
                        if (r2.moved() <= 0) {
                            return;
                        }
                        Run run = s.run(type);
                        if (run != null && run.percent == r2.percent()) {
                            run.remaining += r2.moved();
                        } else {
                            s.runs.put(type, new Run(r2.percent(), r2.moved()));
                            s.pending.remove(type);
                        }
                        Player online = Bukkit.getPlayer(player);
                        String name = online != null ? online.getName() : null;
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("type", type.id());
                        d.put("profile", s.profile.toString());
                        d.put("moved_ms", r2.moved());
                        d.put("remaining_ms", r2.remainingAfter());
                        d.put("bank_left_ms", r2.bankLeft());
                        d.put("source", e.getValue().source());
                        m.audit("bank_start", Actor.SYSTEM, player, name, (long) r2.percent(), d);
                        if (online != null && states.get(player) == s) {
                            m.msg.send(online, "personal.bank-started", Msg.p("type", m.typeName(type)), Msg.number("percent", r2.percent()),
                                    Msg.p("time", BoosterRules.time(r2.moved())), Msg.p("total", BoosterRules.time(s.run(type) == null ? r2.moved()
                                            : s.run(type).remaining)));
                            m.sound(online, m.settings.startSound);
                        }
                    }, m.main());
        }
    }

    // ================================================================== load / save

    private void load(Player p, UUID profile) {
        State s = new State(p.getUniqueId(), profile);
        states.put(p.getUniqueId(), s);
        chain(profile, () -> m.core.database().async(c -> new Object[]{BoosterStore.runs(c, profile), BoosterStore.banks(c, profile)}))
                .whenCompleteAsync((data, error) -> {
                    if (states.get(s.player) != s) {
                        return; // left or switched meanwhile
                    }
                    if (error != null) {
                        states.remove(s.player); // tried again next second
                        m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not load the boosters of " + p.getName(),
                                Database.cause(error));
                        return;
                    }
                    @SuppressWarnings("unchecked") Map<BoosterType, BoosterRules.Run> runs = (Map<BoosterType, BoosterRules.Run>) data[0];
                    @SuppressWarnings("unchecked") Map<BoosterType, BoosterStore.Bank> banks = (Map<BoosterType, BoosterStore.Bank>) data[1];
                    runs.forEach((t, r) -> s.runs.put(t, new Run(r.percent(), r.remaining())));
                    s.banks.putAll(banks);
                    s.loaded = true;
                    Player online = Bukkit.getPlayer(s.player);
                    if (online != null && !s.runs.isEmpty()) {
                        m.msg.send(online, "personal.resumed", Msg.number("count", s.runs.size()));
                    }
                }, m.main());
    }

    /** Saves the usage of a state (relative), removing finished runs in the database. */
    void save(State s) {
        Map<BoosterType, Long> use = s.takePending();
        if (use.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        chain(s.profile, () -> m.core.database().transaction(c -> {
            BoosterStore.applyUse(c, s.profile, use, now);
            return null;
        })).whenComplete((v, error) -> {
            if (error != null) {
                m.core.scheduler().ensureMain("boosters-save", () -> s.giveBack(use));
                m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not save booster time of profile " + s.profile
                        + " (tried again at the next save)", Database.cause(error));
            }
        });
    }

    /** Server stop: saves everyone's usage now (blocking, at most a few rows per player). */
    void saveAllBlocking() {
        long now = System.currentTimeMillis();
        for (State s : states.values()) {
            Map<BoosterType, Long> use = s.takePending();
            if (use.isEmpty()) {
                continue;
            }
            try {
                m.core.database().blockingTransaction(c -> {
                    BoosterStore.applyUse(c, s.profile, use, now);
                    return null;
                });
            } catch (RuntimeException e) {
                m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not save booster time of profile " + s.profile, e);
            }
        }
        states.clear();
    }

    /** After the AFK pool (or another giver) banked time: reload that profile's banks. */
    void reloadBanks(UUID profile) {
        for (State s : states.values()) {
            if (!s.profile.equals(profile) || !s.loaded) {
                continue;
            }
            chain(profile, () -> m.core.database().async(c -> BoosterStore.banks(c, profile))).whenCompleteAsync((banks, error) -> {
                if (error == null && states.get(s.player) == s) {
                    s.banks.clear();
                    s.banks.putAll(banks);
                }
            }, m.main());
        }
    }

    // ================================================================== starting an item

    /**
     * Starts a personal booster item the player holds: the item is taken first, then one transaction marks its unique id
     * used and adds the time. On any refusal or error the item comes back (inventory, else /box). A copy of an item that
     * already started is kept (it is worthless) and reported.
     */
    void start(Player p, ItemStack stack, BoosterItems.Spec spec) {
        State s = state(p);
        if (s == null) {
            m.msg.send(p, "loading");
            return;
        }
        if (s.busy) {
            m.msg.send(p, "busy");
            return;
        }
        long add = spec.millis();
        long max = m.settings.personalMaxMillis();
        Run current = s.run(spec.type());
        BoosterRules.Decision pre = BoosterRules.activate(current == null ? null : current.view(), spec.percent(), add, max);
        if (!pre.ok()) {
            refuse(p, spec, pre.outcome(), current);
            return;
        }
        Optional<ItemStack> taken = m.take(p, stack, spec.itemUuid());
        if (taken.isEmpty()) {
            m.msg.send(p, "moved");
            return;
        }
        s.busy = true;
        Map<BoosterType, Long> use = s.takePending();
        UUID player = p.getUniqueId();
        long now = System.currentTimeMillis();
        chain(s.profile, () -> m.core.database().transaction(c -> BoosterStore.activate(c, s.profile, spec.itemUuid(), spec.type(),
                spec.percent(), add, max, use, now))).whenCompleteAsync((d, error) -> {
            s.busy = false;
            Player online = Bukkit.getPlayer(player);
            if (error != null) {
                s.giveBack(use);
                m.core.plugin().getLogger().log(Level.WARNING, "Boosters: could not start a booster of " + p.getName(), Database.cause(error));
                m.giveBack(online, s.profile, taken.get());
                if (online != null) {
                    m.msg.send(online, "error");
                }
                return;
            }
            if (d.outcome() == BoosterRules.Outcome.ALREADY_USED) {
                m.reuse(online, player, p.getName(), spec, s.profile);
                return;
            }
            if (!d.ok()) {
                m.giveBack(online, s.profile, taken.get());
                if (online != null) {
                    refuse(online, spec, d.outcome(), s.run(spec.type()));
                }
                return;
            }
            Run run = s.run(spec.type());
            if (d.outcome() == BoosterRules.Outcome.STARTED || run == null) {
                s.runs.put(spec.type(), new Run(spec.percent(), add));
                s.pending.remove(spec.type());
            } else {
                run.remaining += add;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("type", spec.type().id());
            details.put("minutes", spec.minutes());
            details.put("item", spec.itemUuid().toString());
            details.put("profile", s.profile.toString());
            details.put("outcome", d.outcome().name().toLowerCase(java.util.Locale.ROOT));
            details.put("remaining_ms", d.remaining());
            m.audit("personal_start", online != null ? Actor.player(online) : Actor.player(player, p.getName()), player, p.getName(),
                    (long) spec.percent(), details);
            if (online != null) {
                Run now2 = s.run(spec.type());
                m.msg.send(online, d.outcome() == BoosterRules.Outcome.STARTED ? "personal.started" : "personal.extended",
                        Msg.p("type", m.typeName(spec.type())), Msg.number("percent", spec.percent()),
                        Msg.p("time", BoosterRules.time(add)), Msg.p("total", BoosterRules.time(now2 == null ? add : now2.remaining)));
                m.sound(online, m.settings.startSound);
                if (m.settings.savePlayerData) {
                    PlayerSaves.save(online, "a booster start");
                }
                m.refreshMenu(online);
            }
        }, m.main());
    }

    private void refuse(Player p, BoosterItems.Spec spec, BoosterRules.Outcome outcome, Run current) {
        switch (outcome) {
            case FULL -> m.msg.send(p, "personal.full", Msg.p("type", m.typeName(spec.type())),
                    Msg.p("max", BoosterRules.time(m.settings.personalMaxMillis())),
                    Msg.p("left", BoosterRules.time(current == null ? 0 : current.remaining)));
            case OTHER_STRENGTH -> m.msg.send(p, "personal.other-strength", Msg.p("type", m.typeName(spec.type())),
                    Msg.number("running", current == null ? 0 : current.percent), Msg.number("percent", spec.percent()),
                    Msg.p("left", BoosterRules.time(current == null ? 0 : current.remaining)));
            default -> m.msg.send(p, "invalid");
        }
    }
}
