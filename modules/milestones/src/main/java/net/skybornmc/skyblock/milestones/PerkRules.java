package net.skybornmc.skyblock.milestones;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * What claimed milestone tiers add to a bonus (Complete Developer Spec §9.1): per chain the perks of the <b>highest
 * claimed</b> tier (OWNER B3: it replaces the lower ones), only for the asked family, and a perk with a target only when
 * the context's target equals it (perks match on target only, never on world: OWNER V6). Island and personal claims
 * simply add up; the family cap is applied once by the bonus service on top of every source. Pure, unit-tested.
 */
final class PerkRules {

    private PerkRules() {
    }

    /**
     * @param family  bonus family id ({@code extra_drops})
     * @param target  the context's target ({@code WHEAT}, {@code ZOMBIE}, {@code spawner}) or null
     * @param claimed chain id -> highest claimed tier, for each scope that applies (island and profile)
     */
    @SafeVarargs
    static double value(String family, String target, Function<String, Optional<MilestoneModel.Chain>> chains,
                        Map<String, Integer>... claimed) {
        double sum = 0;
        for (Map<String, Integer> scope : claimed) {
            if (scope == null) {
                continue;
            }
            for (Map.Entry<String, Integer> e : scope.entrySet()) {
                Optional<MilestoneModel.Chain> chain = chains.apply(e.getKey());
                if (chain.isEmpty()) {
                    continue; // removed from the config: no perk (data kept)
                }
                for (MilestoneModel.Perk p : chain.get().perksOf(Math.min(e.getValue(), chain.get().maxTier()))) {
                    if (p.family().equals(family) && (p.target() == null || p.target().equalsIgnoreCase(target == null ? "" : target))) {
                        sum += p.value();
                    }
                }
            }
        }
        return sum;
    }
}
