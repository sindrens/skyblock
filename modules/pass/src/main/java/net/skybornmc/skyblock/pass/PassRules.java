package net.skybornmc.skyblock.pass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Which tiers can be claimed (no Bukkit; unit-tested). */
final class PassRules {

    /** One claimable tier of a track. */
    record TierRef(PassTable.Track track, int tier) {
    }

    /** Why a tier can't be claimed now (null = it can). */
    enum Block {
        LOCKED, CLAIMED, NO_PREMIUM, NOTHING, UNAVAILABLE
    }

    /** Safety bound for scanning overflow tiers (a broken XP value can never make a menu or count loop for long). */
    static final int MAX_OVERFLOW_TIERS = 10_000;

    private PassRules() {
    }

    /**
     * The state of one tier.
     *
     * @param premium   the account has the premium pass this season
     * @param available false for a reward that can't be given yet (an unknown tag), so the tier waits instead of being lost
     */
    static Block block(PassTable.Track track, int tier, long xp, PassMath.Curve curve, PassTable table, Set<Integer> claimed,
                       boolean premium, Predicate<PassTable.Reward> available) {
        List<PassTable.Reward> rewards = table.rewards(track, tier, curve.max());
        if (rewards.isEmpty()) {
            return Block.NOTHING;
        }
        if (claimed.contains(tier)) {
            return Block.CLAIMED;
        }
        int reached = track == PassTable.Track.FREE ? curve.tier(xp) : curve.level(xp);
        if (track == PassTable.Track.PREMIUM && tier > curve.max()) {
            return Block.NOTHING;
        }
        if (tier > reached) {
            return Block.LOCKED;
        }
        if (track == PassTable.Track.PREMIUM && !premium) {
            return Block.NO_PREMIUM;
        }
        for (PassTable.Reward r : rewards) {
            if (!available.test(r)) {
                return Block.UNAVAILABLE;
            }
        }
        return null;
    }

    /** Claimable tiers, free track first (ascending), at most {@code limit}. */
    static List<TierRef> claimable(long xp, PassMath.Curve curve, PassTable table, Set<Integer> freeClaimed, Set<Integer> premiumClaimed,
                                   boolean premium, Predicate<PassTable.Reward> available, int limit) {
        List<TierRef> out = new ArrayList<>();
        int top = curve.tier(xp);
        top = table.hasOverflow() ? Math.min(top, curve.max() + MAX_OVERFLOW_TIERS) : Math.min(top, curve.max());
        for (int t = 1; t <= top && out.size() < limit; t++) {
            if (block(PassTable.Track.FREE, t, xp, curve, table, freeClaimed, premium, available) == null) {
                out.add(new TierRef(PassTable.Track.FREE, t));
            }
        }
        if (premium) {
            for (int t : table.premiumTiers()) {
                if (out.size() >= limit) {
                    break;
                }
                if (block(PassTable.Track.PREMIUM, t, xp, curve, table, premiumClaimed, true, available) == null) {
                    out.add(new TierRef(PassTable.Track.PREMIUM, t));
                }
            }
        }
        return out;
    }

    /** True when every reward of the list is a cosmetic (the premium track's rule, R3-44). */
    static boolean cosmeticOnly(List<PassTable.Reward> rewards) {
        return rewards.stream().allMatch(r -> r.kind().cosmetic());
    }
}
