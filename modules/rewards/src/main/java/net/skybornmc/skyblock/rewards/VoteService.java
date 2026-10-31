package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.npc.HologramTexts;
import net.skybornmc.skyblock.common.pass.PassXp;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.TutorialService;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.rewards.votifier.Vote;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Votes at runtime (PHASE-10 M10.5): a vote from the receiver is stored first ({@code vote_log}, keyed by its vote id so
 * a repeated packet is stored once), then paid to the voter's <b>active profile</b> (129) when they are online, or at
 * their next join. One payment = one transaction ({@link ClaimEngine}): the row moves queued → paid, the 24-hour rule is
 * checked again inside, the streak and the party counter move, earned credits go through the ledger (idempotency key per vote)
 * and keys / pass XP are written as pending grants. One payment at a time per account.
 */
final class VoteService {

    /** Ledger reason of vote credits and fallback crystals (a source on the economy dashboard). */
    static final String REASON = "vote_reward";
    /** Source id for the /menu Rewards button: unpaid votes are waiting. */
    static final String READY = "vote";
    /** Cells of the party progress bar (hologram and menu). */
    static final int BAR_WIDTH = 20;

    private final RewardsModule m;
    private volatile List<VoteRules.Site> sites = List.of();
    private volatile NavigableMap<Integer, String> streakRewards = new TreeMap<>();

    /** Per account (main thread writes, menus read). */
    private final Map<UUID, VoteRules.Streak> streaks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> lastPaid = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> queued = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile VoteStore.Party party;
    private volatile boolean boardDirty = true;

    VoteService(RewardsModule module) {
        this.m = module;
    }

    // ================================================================== settings

    void parse(List<String> problems) {
        sites = VoteRules.sites(m.votesSettings.sites, problems::add);
        streakRewards = VoteRules.streakRewards(m.votesSettings.streakRewards, problems::add);
        boardDirty = true;
    }

    List<VoteRules.Site> sites() {
        return sites;
    }

    NavigableMap<Integer, String> streakRewards() {
        return streakRewards;
    }

    Optional<VoteRules.Site> site(String id) {
        return sites.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    long cooldownMillis() {
        return m.votesSettings.cooldownHours * VoteRules.HOUR;
    }

    String prefix() {
        return m.core.bedrock() == null ? "." : m.core.bedrock().usernamePrefix();
    }

    // ================================================================== lifecycle

    void enable() {
        m.core.database().transaction(c -> {
            List<Integer> interrupted = VoteStore.reviewInterrupted(c);
            return new Object[]{VoteStore.party(c), interrupted};
        }).whenCompleteAsync((r, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not read the vote party", Database.cause(e));
                return;
            }
            party = (VoteStore.Party) r[0];
            boardDirty = true;
            @SuppressWarnings("unchecked")
            List<Integer> interrupted = (List<Integer>) r[1];
            for (int n : interrupted) {
                m.audit("vote_party_review", Actor.SYSTEM, null, null, (long) n, Map.of("party", n));
                m.core.plugin().getLogger().warning("Rewards: vote party #" + n + " was interrupted by a stop before everyone was paid; "
                        + "it is marked 'review' (vote_party_review in /logs). Check vote_party_rewards and give missing keys by hand.");
            }
        }, m.main());
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    void disable() {
        m.core.services().optional(HologramTexts.class).ifPresent(t -> t.clearAll(RewardsModule.ID));
        busy.clear();
    }

    // ================================================================== arrival

    /** From the receiver thread: hands a checked vote to the main thread. */
    void received(Vote vote) {
        long now = System.currentTimeMillis();
        m.main().execute(() -> arrive(vote, now));
    }

    /** Stores a vote (queued), then pays it when the voter is online. Main thread. */
    void arrive(Vote vote, long receivedAt) {
        if (!m.votesSettings.enabled) {
            m.receiver().reject("-", "voting is turned off (votes.yml -> votes.enabled); vote for " + vote.username() + " dropped");
            return;
        }
        Optional<VoteRules.Site> site = VoteRules.site(sites, vote.service());
        if (site.isEmpty()) {
            m.receiver().reject("-", "unknown service '" + vote.service() + "' (add it to a site in votes.yml -> sites)");
            return;
        }
        Player online = null;
        UUID account = null;
        List<String> names = VoteRules.voteNames(vote.username(), prefix());
        for (String name : names) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) {
                online = p;
                account = p.getUniqueId();
                break;
            }
        }
        if (account == null) {
            for (String name : names) {
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                if (op != null && op.hasPlayedBefore()) {
                    account = op.getUniqueId();
                    break;
                }
            }
        }
        VoteStore.Row row = new VoteStore.Row(VoteRules.voteId(vote.service(), vote.username(), vote.timestamp(), receivedAt), site.get().id(),
                ClaimEngine.cut(vote.service(), 64), ClaimEngine.cut(vote.username(), 36), ClaimEngine.cut(VoteRules.nameKey(vote.username()), 36),
                account, VoteRules.addressHash(vote.address()), vote.protocol(), vote.timestamp(), receivedAt,
                VoteRules.date(receivedAt, m.zone()), VoteRules.QUEUED);
        Player voter = online;
        UUID known = account;
        m.core.database().transaction(c -> VoteStore.insert(c, row)).whenCompleteAsync((stored, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not store a vote for " + vote.username() + " from "
                        + site.get().name() + " (it is lost; the site may send it again)", Database.cause(e));
                return;
            }
            if (!Boolean.TRUE.equals(stored)) {
                return; // the same packet again: stored and handled already
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("vote", row.voteId());
            d.put("site", row.siteId());
            d.put("service", row.service());
            d.put("name", row.username());
            d.put("protocol", row.protocol());
            d.put("known", known != null);
            d.put("online", voter != null);
            m.audit("vote_received", Actor.SYSTEM, known, row.username(), 1L, d);
            if (known != null) {
                // Counted as waiting until it is paid: if a payment of this player is running right now, the minute task
                // (or the next join) picks it up.
                queued.merge(known, 1, Integer::sum);
            }
            if (voter != null && voter.isOnline()) {
                payQueued(voter);
            }
        }, m.main());
    }

    // ================================================================== payment

    /** Pays every waiting vote of an online player to their active profile, oldest first. Completes with the number paid. */
    CompletableFuture<Integer> payQueued(Player p) {
        UUID account = p.getUniqueId();
        if (!m.votesSettings.enabled || !p.isOnline() || m.profiles().isSwitching(account) || !busy.add(account)) {
            return CompletableFuture.completedFuture(0);
        }
        List<String> names = VoteRules.joinNames(p.getName(), prefix());
        CompletableFuture<Integer> done = new CompletableFuture<>();
        m.core.database().async(c -> VoteStore.queuedFor(c, account, names)).whenCompleteAsync((rows, e) -> {
            if (e != null) {
                busy.remove(account);
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not read the waiting votes of " + p.getName(), Database.cause(e));
                done.complete(0);
                return;
            }
            AtomicInteger paid = new AtomicInteger();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (VoteStore.Row row : rows) {
                chain = chain.thenComposeAsync(v -> payOne(p, row).thenAccept(ok -> {
                    if (ok) {
                        paid.incrementAndGet();
                    }
                }), m.main());
            }
            chain.whenCompleteAsync((v, error) -> {
                busy.remove(account);
                if (error != null) {
                    m.core.plugin().getLogger().log(Level.WARNING, "Rewards: paying the votes of " + p.getName() + " stopped", Database.cause(error));
                }
                load(p);
                done.complete(paid.get());
            }, m.main());
        }, m.main());
        return done;
    }

    /** What a payment needs from the database, read before it is planned. */
    private record PreRead(List<Long> sameSite, Set<UUID> onAddress, Optional<VoteRules.Streak> streak) {
    }

    private CompletableFuture<Boolean> payOne(Player p, VoteStore.Row row) {
        if (!p.isOnline() || m.profiles().isSwitching(p.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }
        UUID account = p.getUniqueId();
        long cooldown = cooldownMillis();
        return m.core.database().async(c -> new PreRead(
                        VoteStore.paidTimes(c, account, row.siteId(), row.receivedAt() - cooldown + 1, row.receivedAt() + cooldown - 1, row.voteId()),
                        VoteStore.accountsOnAddress(c, row.addressHash(), row.voteDate()), VoteStore.streak(c, account)))
                .thenComposeAsync(pre -> decide(p, row, pre), m.main())
                .exceptionally(e -> {
                    m.core.plugin().getLogger().log(Level.WARNING, "Rewards: vote " + row.voteId() + " of " + p.getName()
                            + " could not be paid now (it stays waiting)", Database.cause(e));
                    return false;
                });
    }

    private CompletableFuture<Boolean> decide(Player p, VoteStore.Row row, PreRead pre) {
        UUID account = p.getUniqueId();
        String siteName = site(row.siteId()).map(VoteRules.Site::name).orElse(row.siteId());
        long now = System.currentTimeMillis();
        if (VoteRules.onCooldown(pre.sameSite(), row.receivedAt(), cooldownMillis())) {
            long last = pre.sameSite().stream().mapToLong(Long::longValue).max().orElse(row.receivedAt());
            return settle(p, row, VoteRules.DUPLICATE, "vote_duplicate", now).thenApply(ok -> {
                if (ok && p.isOnline()) {
                    m.msg.send(p, "vote.duplicate", Msg.p("site", siteName),
                            Msg.p("time", Format.duration(Duration.ofMillis(VoteRules.untilNext(last, now, cooldownMillis())))));
                }
                return false;
            });
        }
        if (!VoteRules.addressAllowed(pre.onAddress(), account, m.votesSettings.accountsPerAddress)) {
            return settle(p, row, VoteRules.LIMITED, "vote_limited", now).thenApply(ok -> {
                if (ok && p.isOnline()) {
                    m.msg.send(p, "vote.limited", Msg.number("max", m.votesSettings.accountsPerAddress));
                }
                return false;
            });
        }
        return pay(p, row, pre, siteName, now);
    }

    private CompletableFuture<Boolean> settle(Player p, VoteStore.Row row, String state, String action, long now) {
        UUID account = p.getUniqueId();
        return m.core.database().transaction(c -> VoteStore.settle(c, row.voteId(), account, state, now)).thenApplyAsync(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("vote", row.voteId());
                d.put("site", row.siteId());
                d.put("name", row.username());
                m.audit(action, Actor.SYSTEM, account, p.getName(), 0L, d);
            }
            return Boolean.TRUE.equals(ok);
        }, m.main());
    }

    private CompletableFuture<Boolean> pay(Player p, VoteStore.Row row, PreRead pre, String siteName, long now) {
        UUID account = p.getUniqueId();
        UUID profile = m.profiles().profileKey(p);
        VotesSettings s = m.votesSettings;
        int tier = m.tier(p);
        Payout.Context ctx = m.context(tier, profile);
        Payout vote = new Payout().keys(crate(s.keyCrate), s.keys, s.keyFallback, ctx).credits(s.credits);
        if (s.passXp > 0 && m.core.services().has(PassXp.class)) {
            vote.passXp(s.passXp);
        }
        VoteRules.Streak before = pre.streak().orElse(VoteRules.Streak.NONE);
        VoteRules.Streak after = VoteRules.advance(before, row.voteDate());
        boolean grew = !after.equals(before) && after.lastDate() == row.voteDate();
        Optional<String> streakCrate = grew ? VoteRules.streakReward(after.days(), streakRewards, s.cycleDays) : Optional.empty();
        Payout streak = new Payout();
        streakCrate.ifPresent(cr -> streak.keys(crate(cr), 1, s.keyFallback, ctx));

        String key = "vote:" + row.voteId();
        String streakKey = "vote_streak:" + row.voteId();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("site", row.siteId());
        details.put("vote", row.voteId());
        List<Transaction> txs = new ArrayList<>(ClaimEngine.transactions(vote, profile, REASON, key, Actor.player(p), details));
        txs.addAll(ClaimEngine.transactions(streak, profile, REASON, streakKey, Actor.player(p), details));
        List<RewardStore.GrantRow> rows = new ArrayList<>(ClaimEngine.grantRows(vote, profile, account, "vote", key, now));
        rows.addAll(ClaimEngine.grantRows(streak, profile, account, "vote_streak", streakKey, now));

        AtomicInteger filled = new AtomicInteger();
        AtomicReference<VoteStore.Party> partyAfter = new AtomicReference<>();
        return ClaimEngine.commit(m.runner(), txs, payGuard(row, account, profile, cooldownMillis(), pre.streak(), after,
                s.partyEnabled ? s.partySize : 0, rows, now, filled, partyAfter)).thenApplyAsync(status -> {
            if (status != ClaimEngine.Status.OK) {
                if (status == ClaimEngine.Status.ERROR) {
                    m.core.plugin().getLogger().warning("Rewards: vote " + row.voteId() + " of " + p.getName()
                            + " could not be paid now (nothing paid; it stays waiting and is tried again)");
                }
                return false;
            }
            streaks.put(account, after);
            lastPaid.computeIfAbsent(account, k -> new ConcurrentHashMap<>()).merge(row.siteId(), row.receivedAt(), Math::max);
            queued.computeIfPresent(account, (k, v) -> v > 1 ? v - 1 : null);
            if (partyAfter.get() != null) {
                party = partyAfter.get();
                boardDirty = true;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("vote", row.voteId());
            d.put("site", row.siteId());
            d.put("profile", profile.toString());
            d.put("tier", tier);
            d.put("rewards", RewardText.summary(vote));
            d.put("credits", vote.total(Currency.CREDITS_EARNED));
            d.put("streak", after.days());
            streakCrate.ifPresent(cr -> d.put("streak_reward", RewardText.summary(streak)));
            d.put("waited_ms", Math.max(0, now - row.receivedAt()));
            if (partyAfter.get() != null) {
                d.put("party_count", partyAfter.get().counter());
                if (filled.get() > 0) {
                    d.put("party_filled", filled.get());
                }
            }
            m.audit("vote", Actor.SYSTEM, account, p.getName(), 1L, d);
            m.grants().handOut(rows);
            if (p.isOnline()) {
                m.msg.send(p, "vote.thanks", Msg.p("site", siteName), Msg.c("rewards", RewardText.inline(m, p, vote)));
                if (streakCrate.isPresent()) {
                    m.msg.send(p, "vote.streak-reward", Msg.number("streak", after.days()), Msg.c("rewards", RewardText.inline(m, p, streak)));
                }
                m.sound(p, "success");
            }
            refreshMenu(p);
            if (filled.get() > 0) {
                int number = filled.get();
                m.core.scheduler().later("rewards:vote-party", 1L, () -> startParty(number));
            }
            return true;
        }, m.main());
    }

    /**
     * The SQL of one vote payment (tested on H2): queued → paid (false when it was settled meanwhile), the 24-hour rule
     * again inside the transaction, the streak only if unchanged since it was read, one more vote on the party counter
     * ({@code partySize} 0 = party off; {@code filled} gets the party number that filled, {@code partyAfter} the counter
     * after it), and the pending grant rows. False rolls everything back (nothing paid).
     */
    static TxGuard payGuard(VoteStore.Row row, UUID account, UUID profile, long cooldown, Optional<VoteRules.Streak> streakBefore,
                            VoteRules.Streak after, int partySize, List<RewardStore.GrantRow> rows, long now, AtomicInteger filled,
                            AtomicReference<VoteStore.Party> partyAfter) {
        return c -> {
            if (!VoteStore.markPaid(c, row.voteId(), account, profile, now)) {
                return false;
            }
            if (!VoteStore.paidTimes(c, account, row.siteId(), row.receivedAt() - cooldown + 1, row.receivedAt() + cooldown - 1, row.voteId())
                    .isEmpty()) {
                return false;
            }
            if (!VoteStore.saveStreak(c, account, streakBefore, after, now)) {
                return false;
            }
            if (partySize > 0) {
                filled.set(VoteStore.addPartyVote(c, partySize, now));
                partyAfter.set(VoteStore.party(c));
            }
            RewardStore.insertGrants(c, rows);
            return true;
        };
    }

    /** The SQL of one party reward: once per account and party, then the pending grant rows. */
    static TxGuard partyGuard(int number, UUID account, UUID profile, List<RewardStore.GrantRow> rows, long now) {
        return c -> {
            if (!VoteStore.insertPartyReward(c, number, account, profile, now)) {
                return false;
            }
            RewardStore.insertGrants(c, rows);
            return true;
        };
    }

    private static String crate(String id) {
        return id == null ? "" : id.strip().toLowerCase(java.util.Locale.ROOT);
    }

    // ================================================================== vote party

    /** A party just filled: everyone online who voted today, finished the tutorial and is not AFK gets the party key. */
    void startParty(int number) {
        List<Player> eligible = new ArrayList<>();
        List<Player> missed = new ArrayList<>();
        int today = today();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean voted = VoteRules.votedOn(streaks.getOrDefault(p.getUniqueId(), VoteRules.Streak.NONE), today);
            if (VoteRules.partyEligible(p.hasPermission(RewardsModule.PERM_VOTE), voted, tutorialDone(p), m.afk(p))
                    && !m.profiles().isSwitching(p.getUniqueId())) {
                eligible.add(p);
            } else if (!voted && p.hasPermission(RewardsModule.PERM_VOTE)) {
                missed.add(p);
            }
        }
        String crateName = RewardText.crateName(m, crate(m.votesSettings.partyCrate));
        Bukkit.getOnlinePlayers().forEach(p -> m.msg.send(p, "vote.party.started", Msg.number("size", m.votesSettings.partySize),
                Msg.number("players", eligible.size()), Msg.p("crate", crateName)));
        missed.forEach(p -> m.msg.send(p, "vote.party.missed"));
        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (Player p : eligible) {
            all.add(payParty(p, number));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).whenCompleteAsync((v, e) -> {
            int rewarded = (int) all.stream().filter(f -> !f.isCompletedExceptionally() && Boolean.TRUE.equals(f.getNow(false))).count();
            m.core.database().transaction(c -> {
                VoteStore.partyDone(c, number, rewarded);
                return true;
            }).whenComplete((x, error) -> {
                if (error != null) {
                    m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not mark vote party #" + number + " as paid", Database.cause(error));
                }
            });
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("party", number);
            d.put("size", m.votesSettings.partySize);
            d.put("online", Bukkit.getOnlinePlayers().size());
            d.put("eligible", eligible.size());
            d.put("rewarded", rewarded);
            d.put("crate", crate(m.votesSettings.partyCrate));
            m.audit("vote_party", Actor.SYSTEM, null, null, (long) rewarded, d);
            boardDirty = true;
        }, m.main());
    }

    private CompletableFuture<Boolean> payParty(Player p, int number) {
        UUID account = p.getUniqueId();
        UUID profile = m.profiles().profileKey(p);
        long now = System.currentTimeMillis();
        Payout.Context ctx = m.context(m.tier(p), profile);
        Payout payout = new Payout().keys(crate(m.votesSettings.partyCrate), 1, m.votesSettings.keyFallback, ctx);
        String key = "vote_party:" + number + ":" + account;
        List<Transaction> txs = ClaimEngine.transactions(payout, profile, REASON, key, Actor.SYSTEM, Map.of("party", number));
        List<RewardStore.GrantRow> rows = ClaimEngine.grantRows(payout, profile, account, "vote_party", key, now);
        return ClaimEngine.commit(m.runner(), txs, partyGuard(number, account, profile, rows, now)).thenApplyAsync(status -> {
            if (status != ClaimEngine.Status.OK) {
                return false;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("party", number);
            d.put("profile", profile.toString());
            d.put("rewards", RewardText.summary(payout));
            m.audit("vote_party_reward", Actor.SYSTEM, account, p.getName(), 1L, d);
            m.grants().handOut(rows);
            if (p.isOnline()) {
                m.msg.send(p, "vote.party.reward", Msg.c("rewards", RewardText.inline(m, p, payout)));
                m.sound(p, "success");
            }
            return true;
        }, m.main());
    }

    boolean tutorialDone(Player p) {
        Optional<TutorialService> tutorial = m.core.services().optional(TutorialService.class);
        return tutorial.isEmpty() || tutorial.get().state(p.getUniqueId()).map(TutorialService.State::complete).orElse(false);
    }

    // ================================================================== caches and periodic work

    int today() {
        return VoteRules.date(System.currentTimeMillis(), m.zone());
    }

    VoteRules.Streak streak(UUID account) {
        return streaks.getOrDefault(account, VoteRules.Streak.NONE);
    }

    Map<String, Long> lastPaid(UUID account) {
        return lastPaid.getOrDefault(account, Map.of());
    }

    int queued(UUID account) {
        return queued.getOrDefault(account, 0);
    }

    boolean loaded(UUID account) {
        return lastPaid.containsKey(account);
    }

    Optional<VoteStore.Party> party() {
        return Optional.ofNullable(party);
    }

    /** Loads streak, per-site times and the waiting count of an online player (async), then refreshes their menu. */
    void load(Player p) {
        UUID account = p.getUniqueId();
        List<String> names = VoteRules.joinNames(p.getName(), prefix());
        m.core.database().async(c -> new Object[]{VoteStore.streak(c, account).orElse(VoteRules.Streak.NONE),
                VoteStore.lastPaidPerSite(c, account), VoteStore.queuedCount(c, account, names)}).whenCompleteAsync((r, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not load the votes of " + p.getName(), Database.cause(e));
                return;
            }
            if (!p.isOnline()) {
                return;
            }
            streaks.put(account, (VoteRules.Streak) r[0]);
            @SuppressWarnings("unchecked")
            Map<String, Long> times = (Map<String, Long>) r[1];
            lastPaid.put(account, new ConcurrentHashMap<>(times));
            int waiting = (Integer) r[2];
            if (waiting > 0) {
                queued.put(account, waiting);
            } else {
                queued.remove(account);
            }
            refreshMenu(p);
        }, m.main());
    }

    /** After joining (the profile is loaded by now): load, then pay waiting votes. */
    void joined(Player p) {
        load(p);
        payQueued(p);
    }

    void quit(Player p) {
        UUID account = p.getUniqueId();
        streaks.remove(account);
        lastPaid.remove(account);
        queued.remove(account);
        busy.remove(account);
    }

    /** Every minute: expire old waiting votes, prune old rows, retry waiting votes of online players. Main thread. */
    void periodic() {
        if (!m.votesSettings.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long unknownBefore = now - m.votesSettings.unknownNameDays * 24 * VoteRules.HOUR;
        long knownBefore = now - m.votesSettings.offlineDays * 24 * VoteRules.HOUR;
        long pruneBefore = now - m.votesSettings.keepDays * 24 * VoteRules.HOUR;
        m.core.database().transaction(c -> {
            int expired = VoteStore.expire(c, unknownBefore, knownBefore, now);
            VoteStore.prune(c, pruneBefore);
            return expired;
        }).whenCompleteAsync((expired, e) -> {
            if (e != null) {
                m.core.plugin().getLogger().log(Level.WARNING, "Rewards: could not clean up old votes", Database.cause(e));
                return;
            }
            if (expired != null && expired > 0) {
                m.audit("vote_expired", Actor.SYSTEM, null, null, (long) expired, Map.of("count", expired,
                        "unknown_name_days", m.votesSettings.unknownNameDays, "offline_days", m.votesSettings.offlineDays));
            }
        }, m.main());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (queued(p.getUniqueId()) > 0) {
                payQueued(p);
            }
        }
    }

    /** Writes the party progress to the Tavern vote board (only when it changed). Main thread. */
    void pushBoard() {
        if (!boardDirty) {
            return;
        }
        Optional<HologramTexts> texts = m.core.services().optional(HologramTexts.class);
        if (texts.isEmpty()) {
            return;
        }
        boardDirty = false;
        String id = m.votesSettings.partyHologram == null ? "" : m.votesSettings.partyHologram.strip();
        if (id.isEmpty() || !m.votesSettings.enabled || !m.votesSettings.partyEnabled) {
            texts.get().clearAll(RewardsModule.ID);
            return;
        }
        VoteStore.Party p = party;
        int count = p == null ? 0 : p.counter();
        int size = m.votesSettings.partySize;
        Audience none = Audience.empty();
        List<Component> lines = new ArrayList<>();
        lines.add(m.msg.component(none, "vote.board.title"));
        lines.add(m.msg.component(none, "vote.board.progress", Msg.number("count", count), Msg.number("size", size),
                Msg.number("left", Math.max(0, size - count))));
        lines.add(bar(none, count, size));
        lines.add(m.msg.component(none, "vote.board.footer", Msg.p("crate", RewardText.crateName(m, crate(m.votesSettings.partyCrate)))));
        texts.get().set(RewardsModule.ID, id, lines);
    }

    /** A progress bar from messages ({@code vote.board.bar-*}). */
    Component bar(Audience viewer, int count, int size) {
        int width = BAR_WIDTH;
        int full = VoteRules.filledCells(count, size, width);
        String cell = m.raw("vote.board.bar-cell", "|");
        return m.msg.component(viewer, "vote.board.bar", Msg.p("full", cell.repeat(full)), Msg.p("empty", cell.repeat(width - full)));
    }

    void markBoard() {
        boardDirty = true;
    }

    private void refreshMenu(Player p) {
        if (p.isOnline() && m.core.menus().openMenu(p) instanceof VoteMenu menu) {
            menu.refresh();
        }
    }
}
