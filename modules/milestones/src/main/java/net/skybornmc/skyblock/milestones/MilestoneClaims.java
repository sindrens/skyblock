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
final class MilestoneClaims implements BonusSource {

    private final MilestonesModule m;
    /** Scope id (island id or profile key) -> chain id -> highest claimed tier. */
    private final Map<UUID, Map<String, Integer>> claimed = new ConcurrentHashMap<>();
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
        return m.core().database().async(c -> MilestoneStore.claimed(c, scope, id)).handleAsync((map, error) -> {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Milestones: could not load the claims of " + scope + " " + id,
                        Database.cause(error));
            } else {
                claimed.put(id, Map.copyOf(map));
            }
            return null;
        }, m.main());
    }

    /** Highest claimed tier of a chain in a scope, from memory (0 = none or not loaded). */
    int claimedTier(UUID scopeId, String chainId) {
        Map<String, Integer> map = claimed.get(scopeId);
        return map == null ? 0 : map.getOrDefault(chainId, 0);
    }

    /** Drops cached scopes nobody online uses any more (called on quit). */
    void forgetUnused(Set<UUID> inUse) {
        claimed.keySet().retainAll(inUse);
    }

    // ------------------------------------------------------------------ claim

    enum Outcome { CLAIMED, NOT_READY, FAILED }

    /**
     * Claims tier {@code tier} of a chain (spec §7.4): an island tier for the player's own island (any member, once per
     * island; the lower unclaimed tiers are marked too), a personal tier for the active profile. The perk starts now.
     * Main thread; the future completes on the main thread.
     */
    CompletableFuture<Outcome> claim(Player p, MilestoneModel.Chain chain, int tier) {
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
                    m.msg().send(p, "claim.done", Msg.p("chain", chain.name()), Msg.number("tier", tier));
                    m.core().sounds().play(p, "success");
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
