package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusSource;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Claimed milestone tiers in memory and the claim itself (Complete Developer Spec §7.4, §9.1). The claimed tiers of every
 * online player's island and profile are cached (loaded on join, profile switch and after a claim), so the bonus source
 * answers from memory on every block and kill. An island perk applies to every member anywhere and new members get it at
 * once, because it is read from the island's claims, never stored per member.
 */
final class MilestoneClaims implements BonusSource, net.skybornmc.skyblock.common.milestones.MilestoneRewards {

    private final MilestonesModule m;
    /** Scope id (island id or profile key) -> chain id -> highest claimed tier. */
    private final Map<UUID, Map<String, Integer>> claimed = new ConcurrentHashMap<>();
    /** Scope id -> chain id -> highest completed tier (for the ready count of /rewards). */
    private final Map<UUID, Map<String, Integer>> completed = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    MilestoneClaims(MilestonesModule module) {
        this.m = module;
    }

    // ------------------------------------------------------------------ bonus source (main thread, from memory)

    @Override
    public double value(BonusFamily family, BonusContext context) {
        Player p = context == null ? null : context.player();
        if (p == null || !m.config().enabled) {
            return 0;
        }
        UUID island = islandOf(p);
        UUID profile = profileOf(p);
        return PerkRules.value(family.id(), context.target(), m.catalog()::chain,
                island == null ? null : claimed.get(island), claimed.get(profile));
    }

    // ------------------------------------------------------------------ cache

    UUID islandOf(Player p) {
        return m.islands().flatMap(i -> i.islandOf(p.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
    }

    UUID profileOf(Player p) {
        return m.profiles().map(ps -> ps.profileKey(p)).orElse(p.getUniqueId());
    }

    /** Loads the claims of the player's island and profile (async; join, profile switch). */
    void load(Player p) {
        UUID island = islandOf(p);
        UUID profile = profileOf(p);
        if (island != null) {
            load(ProgressBuffer.Scope.ISLAND, island);
        }
        load(ProgressBuffer.Scope.PROFILE, profile);
    }

    CompletableFuture<Void> load(ProgressBuffer.Scope scope, UUID id) {
        return m.core().database().async(c -> MilestoneStore.rows(c, scope, id)).handleAsync((rows, error) -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: could not load the claims of " + scope + " " + id,
                        Database.cause(error));
            } else {
                Map<String, Integer> claims = new LinkedHashMap<>();
                Map<String, Integer> done = new LinkedHashMap<>();
                for (MilestoneStore.Row r : rows) {
                    if (r.claimedTier() > 0) {
                        claims.put(r.chainId(), r.claimedTier());
                    }
                    if (r.highestTier() > 0) {
                        done.put(r.chainId(), r.highestTier());
                    }
                }
                claimed.put(id, Map.copyOf(claims));
                completed.put(id, Map.copyOf(done));
            }
            return null;
        }, m.main());
    }

    /** A tier was reached (engine, after a save): the ready count follows without a reload. */
    void completedTier(UUID scopeId, String chainId, int tier) {
        completed.computeIfPresent(scopeId, (k, map) -> {
            Map<String, Integer> next = new LinkedHashMap<>(map);
            next.merge(chainId, tier, Math::max);
            return Map.copyOf(next);
        });
    }

    // ------------------------------------------------------------------ /rewards source

    /** Chains of a scope with a completed tier above the claimed one (catalog chains only; capped at the chain's tiers). */
    int readyIn(UUID scopeId) {
        if (scopeId == null) {
            return 0;
        }
        int n = 0;
        for (Map.Entry<String, Integer> e : completed.getOrDefault(scopeId, Map.of()).entrySet()) {
            int top = m.catalog().chain(e.getKey()).map(c -> Math.min(e.getValue(), c.maxTier())).orElse(0);
            if (top > claimedTier(scopeId, e.getKey())) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int ready(Player player) {
        if (!m.config().enabled) {
            return 0;
        }
        return readyIn(islandOf(player)) + readyIn(profileOf(player));
    }

    @Override
    public CompletableFuture<java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine>> top(boolean island, int limit) {
        var profiles = m.core().services().optional(net.skybornmc.skyblock.common.profile.ProfileService.class).orElse(null);
        java.util.function.Function<java.util.Map<java.util.UUID, Long>, java.util.List<net.skybornmc.skyblock.common.profile.BestProfile.Entry>> best =
                profiles != null ? profiles::bestPerOwner
                        : values -> net.skybornmc.skyblock.common.profile.BestProfile.collapse(values, k -> k, k -> true);
        return m.core().database().async(c -> MilestoneStore.top(c, island, limit, best));
    }

    @Override
    public CompletableFuture<Integer> claimAll(Player player, boolean announce) {
        java.util.List<java.util.Map.Entry<MilestoneModel.Chain, Integer>> todo = new java.util.ArrayList<>();
        for (UUID scopeId : new UUID[]{islandOf(player), profileOf(player)}) {
            if (scopeId == null) {
                continue;
            }
            completed.getOrDefault(scopeId, Map.of()).forEach((chainId, tier) -> m.catalog().chain(chainId).ifPresent(chain -> {
                int top = Math.min(tier, chain.maxTier());
                if (top > claimedTier(scopeId, chainId)) {
                    todo.add(java.util.Map.entry(chain, top));
                }
            }));
        }
        if (!m.config().enabled || todo.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        // One after the other (one claim in flight per player), quietly; one summary line at the end.
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (java.util.Map.Entry<MilestoneModel.Chain, Integer> t : todo) {
            chain = chain.thenCompose(n -> n < 0 ? CompletableFuture.completedFuture(n)
                    : claim(player, t.getKey(), t.getValue(), true).thenApply(o -> o == Outcome.FAILED ? (n == 0 ? -1 : n)
                    : o == Outcome.CLAIMED ? n + 1 : n));
        }
        return chain.thenApply(n -> {
            if (n > 0 && player.isOnline()) {
                if (announce) {
                    m.msg().send(player, "claim.all", Msg.number("count", n));
                }
                m.core().sounds().play(player, "success");
            }
            return n;
        });
    }

    @Override
    public void open(Player player) {
        m.open(player);
    }

    /** Highest claimed tier of a chain in a scope, from memory (0 = none or not loaded). */
    int claimedTier(UUID scopeId, String chainId) {
        Map<String, Integer> map = claimed.get(scopeId);
        return map == null ? 0 : map.getOrDefault(chainId, 0);
    }

    /** Drops cached scopes nobody online uses any more (called on quit). */
    void forgetUnused(Set<UUID> inUse) {
        claimed.keySet().retainAll(inUse);
        completed.keySet().retainAll(inUse);
    }

    // ------------------------------------------------------------------ claim

    enum Outcome { CLAIMED, NOT_READY, FAILED }

    /**
     * Claims tier {@code tier} of a chain (spec §7.4): an island tier for the player's own island (any member, once per
     * island; the lower unclaimed tiers are marked too), a personal tier for the active profile. The perk starts now.
     * Main thread; the future completes on the main thread.
     */
    CompletableFuture<Outcome> claim(Player p, MilestoneModel.Chain chain, int tier) {
        return claim(p, chain, tier, false);
    }

    /** @param quiet true = no "claimed" chat line and sound for this tier (claim-all sends one summary) */
    CompletableFuture<Outcome> claim(Player p, MilestoneModel.Chain chain, int tier, boolean quiet) {
        ProgressBuffer.Scope scope = chain.menu() == MilestoneModel.Menu.ISLAND ? ProgressBuffer.Scope.ISLAND : ProgressBuffer.Scope.PROFILE;
        UUID profile = profileOf(p);
        UUID scopeId = scope == ProgressBuffer.Scope.ISLAND ? islandOf(p) : profile;
        if (scopeId == null) {
            m.msg().send(p, "claim.no-island");
            return CompletableFuture.completedFuture(Outcome.FAILED);
        }
        if (!busy.add(p.getUniqueId())) {
            m.msg().send(p, "claim.busy");
            return CompletableFuture.completedFuture(Outcome.FAILED);
        }
        long now = System.currentTimeMillis();
        CompletableFuture<Outcome> done = new CompletableFuture<>();
        m.core().database().transaction(c -> MilestoneStore.claim(c, scope, scopeId, chain.id(), tier, profile, now))
                .whenCompleteAsync((ok, error) -> {
                    busy.remove(p.getUniqueId());
                    if (error != null) {
                        m.core().plugin().getLogger().log(Level.WARNING, "Milestones: claim of " + chain.id() + " tier " + tier + " failed",
                                Database.cause(error));
                        m.msg().send(p, "error");
                        done.complete(Outcome.FAILED);
                        return;
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        m.msg().send(p, "claim.not-ready");
                        load(scope, scopeId);
                        done.complete(Outcome.NOT_READY);
                        return;
                    }
                    Map<String, Integer> next = new LinkedHashMap<>(claimed.getOrDefault(scopeId, Map.of()));
                    next.merge(chain.id(), tier, Math::max);
                    claimed.put(scopeId, Map.copyOf(next));
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("chain", chain.id());
                    d.put("tier", tier);
                    d.put("scope", scope.name());
                    d.put("scope_id", scopeId.toString());
                    d.put("profile", profile.toString());
                    m.core().audit().log(AuditCategory.REWARDS, "milestone_claim", Actor.player(p), d);
                    if (!quiet) {
                        m.msg().send(p, "claim.done", Msg.p("chain", chain.name()), Msg.number("tier", tier));
                        m.core().sounds().play(p, "success");
                    }
                    if (scope == ProgressBuffer.Scope.ISLAND) {
                        tellIsland(p, scopeId, chain, tier);
                    }
                    done.complete(Outcome.CLAIMED);
                }, m.main());
        return done;
    }

    /** The other online members see "claimed by <name>" (spec §7.4: a claimed island tier disappears for everyone). */
    private void tellIsland(Player by, UUID island, MilestoneModel.Chain chain, int tier) {
        for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (other != by && island.equals(islandOf(other))) {
                m.msg().send(other, "claim.by-member", Msg.p("player", by.getName()), Msg.p("chain", chain.name()), Msg.number("tier", tier));
            }
        }
    }
}
