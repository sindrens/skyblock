package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The daily calendar of online profiles: a cached state per profile (for the menu) and the claim. A claim reads the
 * stored state again, plans the payout on the main thread and commits it in one transaction ({@link ClaimEngine}); the
 * stored state decides, never the cache. One claim at a time per player.
 */
final class DailyService {

    private final RewardsModule m;
    private final Map<UUID, DailyRules.State> states = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    DailyService(RewardsModule module) {
        this.m = module;
    }

    /** The cached state of a profile (empty while loading). */
    Optional<DailyRules.State> state(UUID profile) {
        return Optional.ofNullable(states.get(profile));
    }

    int today() {
        return DailyRules.today(System.currentTimeMillis(), m.zone());
    }

    /** Loads the state of the player's active profile (async) and refreshes open menus. */
    void load(Player p) {
        UUID profile = m.profiles().profileKey(p);
        m.core.database().async(c -> RewardStore.calendar(c, profile)).whenCompleteAsync((s, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not load the calendar of profile " + profile, Database.cause(e));
                return;
            }
            states.put(profile, s.orElse(DailyRules.State.NONE));
            m.refreshMenus(profile);
        }, m.main());
    }

    /** After joining: load, then remind when today's reward is still open. */
    void joined(Player p) {
        UUID profile = m.profiles().profileKey(p);
        m.core.database().async(c -> RewardStore.calendar(c, profile)).whenCompleteAsync((s, e) -> {
            if (e != null || !p.isOnline()) {
                return;
            }
            DailyRules.State state = s.orElse(DailyRules.State.NONE);
            states.put(profile, state);
            if (m.settings.calendarEnabled && m.settings.joinReminder && p.hasPermission(RewardsModule.PERM_DAILY)
                    && m.wantsReminder(p) && !DailyRules.claimedToday(state, today())) {
                m.msg.send(p, "daily.reminder", Msg.number("day", DailyRules.nextDay(state, m.calendar().size())),
                        Msg.number("streak", DailyRules.liveStreak(state, today())));
            }
        }, m.main());
    }

    void quit(Player p) {
        busy.remove(p.getUniqueId());
        UUID profile = m.profiles().profileKey(p);
        states.remove(profile);
    }

    void forget(UUID profile) {
        states.remove(profile);
    }

    /** Claims today's reward for the player's active profile (menu click, main thread). */
    void claim(Player p) {
        if (!m.settings.calendarEnabled) {
            m.msg.send(p, "daily.disabled");
            return;
        }
        if (!p.hasPermission(RewardsModule.PERM_DAILY)) {
            m.msg.send(p, "no-permission");
            return;
        }
        Optional<String> problem = m.problem(p);
        if (problem.isPresent()) {
            m.msg.send(p, problem.get());
            return;
        }
        if (ItemBox.refuses(m.core.services(), p)) {
            m.msg.send(p, "box-full");
            return;
        }
        UUID player = p.getUniqueId();
        if (!busy.add(player)) {
            m.msg.send(p, "busy");
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        m.core.database().async(c -> RewardStore.calendar(c, profile)).whenCompleteAsync((before, e) -> {
            if (e != null) {
                busy.remove(player);
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not read the calendar of profile " + profile, Database.cause(e));
                m.msg.send(p, "error");
                return;
            }
            if (!p.isOnline() || !m.profiles().profileKey(p).equals(profile) || m.problem(p).isPresent()) {
                busy.remove(player);
                return;
            }
            commit(p, profile, before);
        }, m.main());
    }

    private void commit(Player p, UUID profile, Optional<DailyRules.State> before) {
        UUID player = p.getUniqueId();
        long now = System.currentTimeMillis();
        int today = DailyRules.today(now, m.zone());
        List<RewardSpec> calendar = m.calendar();
        DailyRules.State state = before.orElse(DailyRules.State.NONE);
        states.put(profile, state);
        Optional<DailyRules.Claim> claim = DailyRules.claim(state, today, calendar.size());
        if (claim.isEmpty()) {
            busy.remove(player);
            m.msg.send(p, "daily.already", Msg.p("time", Format.duration(DailyRules.untilReset(now, m.zone()))));
            m.refreshMenus(profile);
            return;
        }
        DailyRules.Claim c = claim.get();
        int tier = m.tier(p);
        Payout.Context ctx = m.context(tier, profile);
        Payout payout = new Payout().add(calendar.get(c.day() - 1), ctx);
        long gems = DailyRules.streakGems(c.streakAfter(), m.settings.gemEvery, m.settings.gems);
        payout.gems(gems);
        String title = m.streakTitles().get(c.streakAfter());
        if (title != null && m.cosmetics().isPresent()) {
            payout.title(title);
        }
        String key = "daily:" + profile + ":" + today;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("day", c.day());
        details.put("streak", c.streakAfter());
        details.put("date", today);
        List<Transaction> txs = ClaimEngine.transactions(payout, profile, TxReason.DAILY_REWARD, key, Actor.player(p), details);
        List<RewardStore.GrantRow> rows = ClaimEngine.grantRows(payout, profile, player, "daily", key, now);
        ClaimEngine.commit(m.runner(), txs, ClaimEngine.dailyGuard(profile, player, before, c, rows, now))
                .whenCompleteAsync((status, error) -> {
                    busy.remove(player);
                    if (error != null || status == null || status == ClaimEngine.Status.ERROR) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Rewards: daily claim of profile " + profile + " failed (nothing paid)",
                                error == null ? null : Database.cause(error));
                        if (p.isOnline()) {
                            m.msg.send(p, "error");
                        }
                        load(p);
                        return;
                    }
                    if (status == ClaimEngine.Status.CHANGED) {
                        if (p.isOnline()) {
                            m.msg.send(p, "daily.changed");
                            load(p);
                        }
                        return;
                    }
                    states.put(profile, c.after());
                    claimed(p, profile, c, payout, rows, tier, key);
                }, m.main());
    }

    private void claimed(Player p, UUID profile, DailyRules.Claim c, Payout payout, List<RewardStore.GrantRow> rows, int tier, String key) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("profile", profile.toString());
        d.put("day", c.day());
        d.put("streak", c.streakAfter());
        d.put("streak_broken", c.streakBroken());
        if (c.streakBroken()) {
            d.put("lost_streak", c.after().lostStreak());
        }
        d.put("tier", tier);
        d.put("money", payout.total(Currency.MONEY));
        d.put("crystals", payout.total(Currency.CRYSTALS));
        d.put("gems", payout.total(Currency.GEMS));
        d.put("rewards", RewardText.summary(payout));
        d.put("key", key);
        m.audit("daily_claim", Actor.player(p), p.getUniqueId(), p.getName(), (long) c.day(), d);
        m.grants().handOut(rows);
        if (p.isOnline()) {
            m.msg.send(p, "daily.claimed", Msg.number("day", c.day()), Msg.number("streak", c.streakAfter()));
            RewardText.lines(m, p, payout).forEach(p::sendMessage);
            if (c.streakBroken() && c.after().lostStreak() > 1) {
                m.msg.send(p, "daily.streak-lost", Msg.number("lost", c.after().lostStreak()));
            }
            long gems = payout.total(Currency.GEMS);
            if (gems > 0 && DailyRules.streakGems(c.streakAfter(), m.settings.gemEvery, m.settings.gems) > 0) {
                m.msg.send(p, "daily.streak-gems", Msg.number("streak", c.streakAfter()),
                        Msg.p("gems", m.economy().map(e -> e.format(Currency.GEMS, m.settings.gems)).orElse(String.valueOf(m.settings.gems))));
            }
            m.sound(p, "success");
        }
        m.refreshMenus(profile);
    }
}
