package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/** Member rules of the island level (PHASE-5 M5.2), free of Bukkit (unit-tested). */
final class LevelRules {

    private LevelRules() {
    }

    /**
     * Members who count for skills and Depths: stored members (never coop) on the island for at least {@code minMillis}
     * (owner P5-4). A member without a join time does not count.
     */
    static List<UUID> countedMembers(Collection<UUID> members, Function<UUID, OptionalLong> since, long now, long minMillis) {
        List<UUID> out = new ArrayList<>();
        for (UUID m : members) {
            OptionalLong joined = since.apply(m);
            if (joined.isPresent() && now - joined.getAsLong() >= minMillis) {
                out.add(m);
            }
        }
        return out;
    }

    /** Total skill level (all skills added up) of every member. */
    static List<Integer> skillTotals(List<Map<SkillType, Integer>> memberLevels) {
        List<Integer> out = new ArrayList<>(memberLevels.size());
        for (Map<SkillType, Integer> levels : memberLevels) {
            int sum = 0;
            for (Integer l : levels.values()) {
                sum += l == null ? 0 : Math.max(0, l);
            }
            out.add(sum);
        }
        return out;
    }

    /**
     * Best member per skill wing: the most Depths tiers any counted member has open with that skill (a team that splits
     * mine, forest and combat is rewarded). Skills without a wing give 0 and are left out.
     */
    static Map<SkillType, Integer> bestTiers(List<Map<SkillType, Integer>> memberLevels, ToIntBiFunction<SkillType, Integer> unlocked) {
        Map<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        for (Map<SkillType, Integer> levels : memberLevels) {
            for (Map.Entry<SkillType, Integer> e : levels.entrySet()) {
                int tiers = Math.max(0, unlocked.applyAsInt(e.getKey(), e.getValue() == null ? 0 : e.getValue()));
                if (tiers > 0) {
                    out.merge(e.getKey(), tiers, Math::max);
                }
            }
        }
        return out;
    }

    static int sum(Collection<Integer> values) {
        int s = 0;
        for (Integer v : values) {
            s += v == null ? 0 : v;
        }
        return s;
    }
}
