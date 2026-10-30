package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.afk.AfkPool;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.pass.PassXp;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Playtime rewards (PHASE-10 M10.6, R6), per profile. Every {@code tick-seconds} each online player who is active (did
 * something in the last {@code idle-minutes}, not marked AFK, not in the AFK pool, not frozen, in staff mode or switching
 * profile) earns that many seconds for the profile they are on. Every {@code save-seconds} the time is added to the
 * database (relative update) and due milestones are paid in one guarded transaction ({@link ClaimEngine#playtimeGuard}):
 * hourly crystals, a bound key every 10 hours, titles. A payout that lost a race simply happens at the next save.
 */
final class PlaytimeService {

    /** What the Playtime tab shows about right now. */
    enum Status { COUNTING, IDLE, AFK, POOL, PAUSED, DISABLED }

    private final RewardsModule m;
    private final ActivityTracker activity;
    /** Counted but not saved yet, per profile (main thread). */
    private final Map<UUID, Long> unsaved = new HashMap<>();
    /** The last stored row per profile (for the menu). */
    private final Map<UUID, PlaytimeRules.Row> rows = new ConcurrentHashMap<>();
    /** Last tier, owner and status per profile/player, from the tick (main thread). */
    private final Map<UUID, Integer> tiers = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, Status> status = new HashMap<>();
    /** Per-profile chain: saves of one profile never overlap. */
    private final Map<UUID, CompletableFuture<?>> chains = new HashMap<>();

    PlaytimeService(RewardsModule module, ActivityTracker activity) {
        this.m = module;
        this.activity = activity;
    }

    // ================================================================== counting (main thread)

    void tick() {
        long now = System.currentTimeMillis();
        long idle = m.settings.idleMinutes * 60_000L;
        Optional<AfkPool> pool = AfkPool.of(m.core.services());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Status s = statusNow(p, now, idle, pool);
            status.put(p.getUniqueId(), s);
            if (s != Status.COUNTING) {
                continue;
            }
            UUID profile = m.profiles().profileKey(p);
            unsaved.merge(profile, (long) m.settings.tickSeconds, Long::sum);
            tiers.put(profile, m.tier(p));
            owners.put(profile, p.getUniqueId());
        }
    }

    private Status statusNow(Player p, long now, long idle, Optional<AfkPool> pool) {
        if (!m.settings.playtimeEnabled || !p.hasPermission(RewardsModule.PERM_PLAYTIME)) {
            return Status.DISABLED;
        }
        boolean markedAfk = m.flag(p, "afk");
        boolean inPool = pool.map(a -> a.inPool(p)).orElse(false);
        boolean blocked = m.problem(p).isPresent();
        long last = activity.lastAction(p.getUniqueId());
        if (PlaytimeRules.active(last, now, idle, markedAfk, inPool, blocked)) {
            return Status.COUNTING;
        }
        if (inPool) {
            return Status.POOL;
        }
        if (markedAfk) {
            return Status.AFK;
        }
        return blocked ? Status.PAUSED : Status.IDLE;
    }

    Status status(Player p) {
        return status.getOrDefault(p.getUniqueId(), Status.IDLE);
    }

    /** Stored row plus time counted but not saved yet (empty while loading). */
    Optional<PlaytimeRules.Row> view(UUID profile) {
        PlaytimeRules.Row row = rows.get(profile);
        if (row == null) {
            load(profile);
            return Optional.empty();
        }
        long extra = unsaved.getOrDefault(profile, 0L);
        return Optional.of(new PlaytimeRules.Row(row.activeSeconds() + extra, row.hoursPaid(), row.tensPaid(), row.titleHours()));
    }

    private void load(UUID profile) {
        m.core.database().async(c -> RewardStore.playtime(c, profile)).whenCompleteAsync((row, e) -> {
            if (e == null && row != null) {
                rows.putIfAbsent(profile, row);
                m.refreshMenus(profile);
            }
        }, m.main());
    }

    // ================================================================== saving

    void flushAll() {
        for (UUID profile : List.copyOf(unsaved.keySet())) {
            flush(profile);
        }
    }

    void quit(Player p) {
        UUID profile = m.profiles().profileKey(p);
        flush(profile);
        status.remove(p.getUniqueId());
    }

    void forget(UUID profile) {
        unsaved.remove(profile);
        rows.remove(profile);
        tiers.remove(profile);
        owners.remove(profile);
    }

    /** On disable: saves counted time (blocking, no payouts; they follow after the next start). */
    void flushAllBlocking() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : Map.copyOf(unsaved).entrySet()) {
            try {
                m.core.database().blockingTransaction(c -> RewardStore.addSeconds(c, e.getKey(), e.getValue(), now));
            } catch (RuntimeException ex) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not save the playtime of profile " + e.getKey(), ex);
            }
        }
        unsaved.clear();
    }

    private void flush(UUID profile) {
        Long seconds = unsaved.remove(profile);
        if (seconds == null || seconds <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        chain(profile, () -> m.core.database().transaction(c -> RewardStore.addSeconds(c, profile, seconds, now)))
                .whenCompleteAsync((row, e) -> {
                    if (e != null) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not save the playtime of profile " + profile
                                + "; it is counted again at the next save", Database.cause(e));
                        unsaved.merge(profile, seconds, Long::sum);
                        return;
                    }
                    rows.put(profile, row);
                    PlaytimeRules.Due due = PlaytimeRules.due(row, m.settings.keyEveryHours, m.playtimeTitles(), m.settings.maxHoursPerPayout);
                    if (due.any()) {
                        pay(profile, row, due);
                    }
                    if (m.receiver(profile) == null && !unsaved.containsKey(profile)) {
                        // Nobody plays this profile anymore: drop its caches (pay() already read what it needs).
                        rows.remove(profile);
                        tiers.remove(profile);
                        owners.remove(profile);
                        return;
                    }
                    m.refreshMenus(profile);
                }, m.main());
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> chain(UUID profile, java.util.function.Supplier<CompletableFuture<T>> work) {
        CompletableFuture<?> before = chains.getOrDefault(profile, CompletableFuture.completedFuture(null));
        CompletableFuture<T> next = before.handle((x, e) -> null).thenCompose(v -> work.get());
        chains.put(profile, next);
        next.whenCompleteAsync((v, e) -> chains.remove(profile, next), m.main());
        return next;
    }

    // ================================================================== payouts (main thread)

    private void pay(UUID profile, PlaytimeRules.Row before, PlaytimeRules.Due due) {
        int tier = tiers.getOrDefault(profile, 1);
        UUID owner = owners.getOrDefault(profile, m.profiles().ownerOf(profile));
        if (owner == null) {
            return;
        }
        Payout.Context ctx = m.context(tier, profile);
        Payout payout = new Payout();
        long perHour = RewardTables.crystals(m.settings.hourlyCrystals, tier, m.settings.crystalGrowth);
        payout.crystals(perHour > Long.MAX_VALUE / Math.max(1, due.hours()) ? Long.MAX_VALUE : perHour * due.hours());
        payout.keys(m.settings.keyCrate.trim().toLowerCase(java.util.Locale.ROOT), due.keys(), m.settings.keyFallback, ctx);
        if (m.cosmetics().isPresent()) {
            for (int hours : due.titles()) {
                String id = m.playtimeTitles().get(hours);
                if (id != null) {
                    payout.title(id);
                }
            }
        }
        long passXp = PassXp.of(m.core.services()).isPresent() ? m.settings.hourlyPassXp * due.hours() : 0;
        payout.passXp(passXp);
        PlaytimeRules.Row after = due.after();
        if (payout.isEmpty() && after.equals(before)) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = "playtime:" + profile + ":" + after.hoursPaid() + ":" + after.tensPaid() + ":" + after.titleHours();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("hours", due.hours());
        details.put("hours_paid", after.hoursPaid());
        List<Transaction> txs = ClaimEngine.transactions(payout, profile, TxReason.PLAYTIME_REWARD, key, Actor.system(RewardsModule.ID), details);
        List<RewardStore.GrantRow> grantRows = ClaimEngine.grantRows(payout, profile, owner, "playtime", key, now);
        ClaimEngine.commit(m.runner(), txs, ClaimEngine.playtimeGuard(profile, before, after, grantRows, now))
                .whenCompleteAsync((st, error) -> {
                    if (error != null || st == null || st == ClaimEngine.Status.ERROR) {
                        m.core.plugin().getLogger().log(Level.WARNING, "Rewards: playtime payout of profile " + profile
                                + " failed (nothing paid; tried again at the next save)", error == null ? null : Database.cause(error));
                        return;
                    }
                    if (st == ClaimEngine.Status.CHANGED) {
                        return; // another payout won; the next save pays what is still due
                    }
                    rows.computeIfPresent(profile, (k, r) -> new PlaytimeRules.Row(r.activeSeconds(), after.hoursPaid(), after.tensPaid(),
                            after.titleHours()));
                    paid(profile, owner, due, payout, grantRows, tier, key, passXp);
                }, m.main());
    }

    private void paid(UUID profile, UUID owner, PlaytimeRules.Due due, Payout payout, List<RewardStore.GrantRow> grantRows, int tier,
                      String key, long passXp) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("profile", profile.toString());
        d.put("hours", due.hours());
        d.put("hours_paid", due.after().hoursPaid());
        d.put("keys", due.keys());
        d.put("titles", due.titles().toString());
        d.put("tier", tier);
        d.put("crystals", payout.total(Currency.CRYSTALS));
        d.put("pass_xp", passXp);
        d.put("rewards", RewardText.summary(payout));
        d.put("key", key);
        m.audit("playtime_reward", Actor.system(RewardsModule.ID), owner, null, (long) due.hours(), d);
        m.grants().handOut(grantRows);
        Player p = m.receiver(profile);
        if (p != null) {
            m.msg.send(p, "playtime.reward", Msg.number("hours", due.after().hoursPaid()));
            RewardText.lines(m, p, payout).forEach(p::sendMessage);
            m.sound(p, "success");
        }
    }
}
