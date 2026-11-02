package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Weekly Reward and Monthly Reward (spec section 6), per profile. A cached state per online profile feeds the /rewards
 * menu and the hub count; a claim follows the Reward Core algorithm (spec 4.3): the period key is computed once at click
 * time, the stored state is read again, and ONE transaction writes the {@code reward_claims} row (unique per source,
 * profile and period), pays the currencies through the ledger and writes the hand-outs as pending grant rows. Hand-outs
 * run after the commit ({@link Grants}). One claim at a time per player.
 */
final class PeriodicService {

    /** What the menu shows for one period of one profile. {@code activeSeconds} only matters for the week. */
    record State(String periodKey, boolean claimed, long activeSeconds) {
    }

    private final RewardsModule m;
    private final Map<UUID, State> weeks = new ConcurrentHashMap<>();
    private final Map<UUID, State> months = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    PeriodicService(RewardsModule module) {
        this.m = module;
    }

    boolean enabled(PeriodicRules.Period period) {
        return period == PeriodicRules.Period.WEEK ? m.settings.weeklyEnabled : m.settings.monthlyEnabled;
    }

    private Map<UUID, State> cache(PeriodicRules.Period period) {
        return period == PeriodicRules.Period.WEEK ? weeks : months;
    }

    String periodKey(PeriodicRules.Period period) {
        return PeriodicRules.key(period, System.currentTimeMillis(), m.zone());
    }

    /** The cached state of this period (empty while loading or after the period changed). */
    Optional<State> state(UUID profile, PeriodicRules.Period period) {
        State s = cache(period).get(profile);
        return s != null && s.periodKey().equals(periodKey(period)) ? Optional.of(s) : Optional.empty();
    }

    /** Active seconds this week on the profile: stored plus counted but not saved yet. */
    long weekSeconds(UUID profile) {
        return state(profile, PeriodicRules.Period.WEEK).map(State::activeSeconds).orElse(0L) + m.playtime().unsaved(profile);
    }

    /** Ready to claim now (cheap; main thread). */
    boolean ready(Player p, PeriodicRules.Period period) {
        if (!enabled(period) || !p.hasPermission(RewardsModule.PERM_DAILY)) {
            return false;
        }
        UUID profile = m.profiles().profileKey(p);
        Optional<State> s = state(profile, period);
        if (s.isEmpty() || s.get().claimed()) {
            return false;
        }
        return period == PeriodicRules.Period.MONTH || PeriodicRules.weeklyUnlocked(weekSeconds(profile), m.settings.weeklyRequiredMinutes);
    }

    /** Loads both periods of the player's active profile (async) and refreshes open menus. */
    void load(Player p) {
        UUID profile = m.profiles().profileKey(p);
        if (!loading.add(profile)) {
            return; // one load at a time (the menu asks again every second while it shows "loading")
        }
        long now = System.currentTimeMillis();
        String week = PeriodicRules.key(PeriodicRules.Period.WEEK, now, m.zone());
        String month = PeriodicRules.key(PeriodicRules.Period.MONTH, now, m.zone());
        m.core.database().async(c -> new State[]{
                new State(week, RewardStore.periodClaimed(c, PeriodicRules.Period.WEEK.source(), profile, week),
                        RewardStore.weekSeconds(c, profile, week)),
                new State(month, RewardStore.periodClaimed(c, PeriodicRules.Period.MONTH.source(), profile, month), 0)
        }).whenCompleteAsync((states, e) -> {
            loading.remove(profile);
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not load the weekly/monthly state of profile " + profile,
                        Database.cause(e));
                return;
            }
            weeks.put(profile, states[0]);
            months.put(profile, states[1]);
            m.refreshMenus(profile);
        }, m.main());
    }

    void quit(Player p) {
        busy.remove(p.getUniqueId());
        forget(m.profiles().profileKey(p));
    }

    void forget(UUID profile) {
        weeks.remove(profile);
        months.remove(profile);
    }

    /** Outcome of a claim (for Claim all). */
    enum Outcome { CLAIMED, ALREADY, LOCKED, FAILED }

    record Result(Outcome outcome, Component inline) {
        static Result of(Outcome outcome) {
            return new Result(outcome, Component.empty());
        }
    }

    /**
     * Claims the period's reward for the player's active profile (main thread). The future completes on the main thread.
     * Problems are always told; {@code quiet} (Claim all) leaves out "already claimed" and the per-reward lines.
     */
    CompletableFuture<Result> claim(Player p, PeriodicRules.Period period, boolean quiet) {
        String name = period.source();
        if (!enabled(period)) {
            m.msg.send(p, name + ".disabled");
            return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
        }
        if (!p.hasPermission(RewardsModule.PERM_DAILY)) {
            m.msg.send(p, "no-permission");
            return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
        }
        Optional<String> problem = m.problem(p);
        if (problem.isPresent()) {
            m.msg.send(p, problem.get());
            return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
        }
        if (ItemBox.refuses(m.core.services(), p)) {
            m.msg.send(p, "box-full");
            return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
        }
        UUID player = p.getUniqueId();
        if (!busy.add(player)) {
            m.msg.send(p, "busy");
            return CompletableFuture.completedFuture(Result.of(Outcome.FAILED));
        }
        // Step 1 (spec 4.3): the period key is computed once, here; a claim at 23:59:59.9 keeps it.
        long now = System.currentTimeMillis();
        String periodKey = PeriodicRules.key(period, now, m.zone());
        UUID profile = m.profiles().profileKey(p);
        long unsaved = m.playtime().unsaved(profile);
        CompletableFuture<Result> done = new CompletableFuture<>();
        m.core.database().async(c -> new long[]{
                RewardStore.periodClaimed(c, name, profile, periodKey) ? 1 : 0,
                period == PeriodicRules.Period.WEEK ? RewardStore.weekSeconds(c, profile, periodKey) : 0
        }).whenCompleteAsync((read, e) -> {
            if (e != null) {
                busy.remove(player);
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not read the " + name + " state of profile " + profile,
                        Database.cause(e));
                m.msg.send(p, "error");
                done.complete(Result.of(Outcome.FAILED));
                return;
            }
            if (!p.isOnline() || !m.profiles().profileKey(p).equals(profile) || m.problem(p).isPresent()) {
                busy.remove(player);
                done.complete(Result.of(Outcome.FAILED));
                return;
            }
            cache(period).put(profile, new State(periodKey, read[0] == 1, read[1]));
            if (read[0] == 1) {
                busy.remove(player);
                if (!quiet) {
                    m.msg.send(p, name + ".already", Msg.p("time", Format.duration(PeriodicRules.untilReset(period, now, m.zone()))));
                }
                m.refreshMenus(profile);
                done.complete(Result.of(Outcome.ALREADY));
                return;
            }
            long seconds = read[1] + unsaved;
            if (period == PeriodicRules.Period.WEEK && !PeriodicRules.weeklyUnlocked(seconds, m.settings.weeklyRequiredMinutes)) {
                busy.remove(player);
                if (!quiet) {
                    m.msg.send(p, "weekly.locked", Msg.p("played", Format.duration(java.time.Duration.ofSeconds(seconds))),
                            Msg.p("needed", Format.duration(java.time.Duration.ofMinutes(m.settings.weeklyRequiredMinutes))));
                }
                done.complete(Result.of(Outcome.LOCKED));
                return;
            }
            commit(p, period, profile, periodKey, now, quiet, done);
        }, m.main());
        return done;
    }

    private void commit(Player p, PeriodicRules.Period period, UUID profile, String periodKey, long now, boolean quiet,
                        CompletableFuture<Result> done) {
        UUID player = p.getUniqueId();
        String name = period.source();
        int tier = m.tier(p);
        Payout payout = new Payout();
        Payout.Context ctx = m.context(tier, profile);
        for (RewardSpec spec : m.rewards(period)) {
            payout.add(spec, ctx);
        }
        String key = PeriodicRules.claimKey(period, profile, periodKey);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("period", periodKey);
        List<Transaction> txs = ClaimEngine.transactions(payout, profile, period == PeriodicRules.Period.WEEK
                ? net.skybornmc.skyblock.common.economy.TxReason.WEEKLY_REWARD : net.skybornmc.skyblock.common.economy.TxReason.MONTHLY_REWARD, key, Actor.player(p), details);
        List<RewardStore.GrantRow> rows = ClaimEngine.grantRows(payout, profile, player, name, key, now);
        TxGuard guard = c -> {
            if (RewardStore.periodClaimed(c, name, profile, periodKey)) {
                return false;
            }
            RewardStore.insertPeriodClaim(c, name, profile, periodKey, player, key, now);
            RewardStore.insertGrants(c, rows);
            return true;
        };
        ClaimEngine.commit(m.runner(), txs, guard).whenCompleteAsync((status, error) -> {
            busy.remove(player);
            if (error != null || status == null || status == ClaimEngine.Status.ERROR) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: " + name + " claim of profile " + profile + " failed (nothing paid)",
                        error == null ? null : Database.cause(error));
                if (p.isOnline()) {
                    m.msg.send(p, "error");
                }
                done.complete(Result.of(Outcome.FAILED));
                return;
            }
            if (status == ClaimEngine.Status.CHANGED) {
                cache(period).put(profile, new State(periodKey, true, state(profile, period).map(State::activeSeconds).orElse(0L)));
                if (p.isOnline() && !quiet) {
                    m.msg.send(p, name + ".already", Msg.p("time", Format.duration(PeriodicRules.untilReset(period, now, m.zone()))));
                }
                m.refreshMenus(profile);
                done.complete(Result.of(Outcome.ALREADY));
                return;
            }
            cache(period).put(profile, new State(periodKey, true, state(profile, period).map(State::activeSeconds).orElse(0L)));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("profile", profile.toString());
            d.put("period", periodKey);
            d.put("tier", tier);
            d.put("money", payout.total(Currency.MONEY));
            d.put("crystals", payout.total(Currency.CRYSTALS));
            d.put("rewards", RewardText.summary(payout));
            d.put("key", key);
            d.put("via", quiet ? "claim-all" : name);
            m.audit(name + "_claim", Actor.player(p), player, p.getName(), null, d);
            m.grants().handOut(rows);
            Component inline = Component.empty();
            if (p.isOnline()) {
                if (!quiet) {
                    m.msg.send(p, name + ".claimed");
                    RewardText.lines(m, p, payout).forEach(p::sendMessage);
                }
                m.sound(p, "success");
                inline = RewardText.inline(m, p, payout);
            }
            m.refreshMenus(profile);
            done.complete(new Result(Outcome.CLAIMED, inline));
        }, m.main());
    }
}
