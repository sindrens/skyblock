package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Gates and what a player can do with a set they look at (WARDROBE §2.4, §6.2). Pure and unit-tested; the menus and the
 * purchase code both ask here, so the button and the rule can never disagree.
 */
final class Offers {

    /** What the set's action button does. */
    enum Kind {
        /** The profile owns it. */
        OWNED,
        /** {@code enabled: false} in sets.yml. */
        DISABLED,
        /** Its feature ({@code enabled-from}) doesn't exist yet: "Arrives with …". */
        ARRIVES,
        /** The gate is not met (shows the requirement and progress). */
        LOCKED,
        /** Gem set whose Sigil the account owns: free claim (once per profile per season). */
        CLAIM,
        /** Gem set already claimed on this profile this season (and not owned: it was merged or removed). */
        CLAIMED,
        /** Buy with money, crystals or gems. */
        BUY,
        /** Crate-only set: comes as a Set Token. */
        CRATE_ONLY
    }

    /** The decision plus what to show. {@code currency}/{@code price} are set for BUY. */
    record Offer(Kind kind, Currency currency, long price, GateStatus gate) {
    }

    /**
     * Where a gate stands.
     *
     * @param kind  which part is shown: none, tier, any-skill or skill
     * @param wing  the wing of a tier gate ({@code any} = best wing), or the skill id of a skill gate
     * @param need  tier/level needed
     * @param have  the player's best tier/level for it
     */
    record GateStatus(boolean passes, String kind, String wing, int need, int have) {
        static final GateStatus OPEN = new GateStatus(true, "none", "any", 0, 0);
    }

    /** What the profile has, as far as the offer is concerned. */
    record State(boolean owned, boolean sigil, boolean claimedThisSeason) {
    }

    private Offers() {
    }

    // ------------------------------------------------------------------ gates

    /** The tier a skill level opens: how many thresholds it reaches, at least 1. */
    static int tier(List<Integer> thresholds, int skillLevel) {
        int count = 0;
        for (int t : thresholds) {
            if (skillLevel >= t) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * Checks a gate. Any one configured part passes it; the status shows the first part (tier, then any-skill, then
     * skill) with the player's progress so the menu can say "Mining tier 6 ▸ 4/6".
     *
     * @param tiers  skill levels per tier for a wing (mining, foraging, combat, farming, fishing)
     */
    static GateStatus gate(SetDef.Gate gate, Map<SkillType, Integer> levels, Function<String, List<Integer>> tiers) {
        if (gate.open()) {
            return GateStatus.OPEN;
        }
        GateStatus first = null;
        if (gate.tier() > 0) {
            int have;
            if (gate.wing().equals("any")) {
                have = 0;
                for (String wing : List.of("mining", "foraging", "combat", "farming", "fishing")) {
                    have = Math.max(have, wingTier(wing, levels, tiers));
                }
            } else {
                have = wingTier(gate.wing(), levels, tiers);
            }
            GateStatus s = new GateStatus(have >= gate.tier(), "tier", gate.wing(), gate.tier(), have);
            if (s.passes()) {
                return s;
            }
            first = s;
        }
        if (gate.anySkill() > 0) {
            int best = levels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            GateStatus s = new GateStatus(best >= gate.anySkill(), "any-skill", "any", gate.anySkill(), best);
            if (s.passes()) {
                return s;
            }
            first = first == null ? s : first;
        }
        if (!gate.skill().isEmpty() && gate.skillLevel() > 0) {
            int have = SkillType.byId(gate.skill()).map(t -> levels.getOrDefault(t, 0)).orElse(0);
            GateStatus s = new GateStatus(have >= gate.skillLevel(), "skill", gate.skill(), gate.skillLevel(), have);
            if (s.passes()) {
                return s;
            }
            first = first == null ? s : first;
        }
        return first == null ? GateStatus.OPEN : first;
    }

    private static int wingTier(String wing, Map<SkillType, Integer> levels, Function<String, List<Integer>> tiers) {
        SkillType skill = SkillType.byId(wing).orElse(null);
        if (skill == null) {
            return 0;
        }
        List<Integer> thresholds = tiers.apply(wing);
        return thresholds == null || thresholds.isEmpty() ? 0 : tier(thresholds, levels.getOrDefault(skill, 0));
    }

    // ------------------------------------------------------------------ offers

    /**
     * What the set's button does for this profile. Order: owned → disabled → feature missing → gem claim (a Sigil skips
     * the gate: it was met when bought) → crate only → gate → buy.
     */
    static Offer offer(SetDef def, State state, GateStatus gate, boolean featureAvailable) {
        if (state.owned()) {
            return new Offer(Kind.OWNED, null, 0, gate);
        }
        if (!def.enabled()) {
            return new Offer(Kind.DISABLED, null, 0, gate);
        }
        if (!featureAvailable) {
            return new Offer(Kind.ARRIVES, null, 0, gate);
        }
        if (def.source().type() == SetDef.SourceType.GEMS && state.sigil()) {
            return new Offer(state.claimedThisSeason() ? Kind.CLAIMED : Kind.CLAIM, null, 0, gate);
        }
        if (!def.source().buyable()) {
            return new Offer(Kind.CRATE_ONLY, null, 0, gate);
        }
        if (!gate.passes()) {
            return new Offer(Kind.LOCKED, null, 0, gate);
        }
        return new Offer(Kind.BUY, def.source().type().currency(), def.source().price(), gate);
    }

    /** Whether a Set Token for this set can be redeemed (enabled, feature there, gate met, not owned). */
    static boolean canRedeem(SetDef def, boolean owned, GateStatus gate, boolean featureAvailable) {
        return !owned && def.enabled() && featureAvailable && gate.passes();
    }
}
