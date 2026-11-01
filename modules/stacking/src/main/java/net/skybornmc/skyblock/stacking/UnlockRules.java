package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.stacking.StackingService;

import java.util.List;
import java.util.Map;

/**
 * ECON-SPAWNERS step 9 (pure): a spawner tier unlocks per island once the island killed enough mobs of the previous tier
 * (any source). Tier 1 and a tier with 0 kills needed (the premium tier) are always open. The gate only blocks shop
 * purchases (S5).
 */
final class UnlockRules {

    private UnlockRules() {
    }

    /**
     * @param needed      kills needed for this tier (0 = open)
     * @param named       mobs named in {@code spawners.unlock.tier-N.mobs} (empty = every mob of the previous tier)
     * @param previousTier mobs of the previous tier (sellable ones)
     * @param kills       the island's kills per mob
     */
    static StackingService.Unlock status(int tier, long needed, List<String> named, List<String> previousTier, Map<String, Long> kills) {
        if (tier <= 1 || needed <= 0) {
            return StackingService.Unlock.OPEN;
        }
        List<String> mobs = named == null || named.isEmpty() ? previousTier : named;
        long sum = 0;
        for (String mob : mobs) {
            sum += Math.max(0, kills.getOrDefault(mob, 0L));
        }
        String label = named == null || named.isEmpty() ? "tier" : "mobs";
        return new StackingService.Unlock(sum >= needed, Math.min(sum, needed), needed, label, List.copyOf(mobs));
    }
}
