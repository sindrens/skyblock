package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Anti-abuse member rules of the island level (PHASE-5 M5.2, owner P5-4). */
class LevelRulesTest {

    private static final long DAY = 86_400_000L;

    @Test
    void onlyMembersWithThreeDaysCount() {
        UUID old = UUID.randomUUID(), fresh = UUID.randomUUID(), coop = UUID.randomUUID();
        Map<UUID, OptionalLong> since = Map.of(old, OptionalLong.of(0), fresh, OptionalLong.of(9 * DAY), coop, OptionalLong.empty());
        List<UUID> counted = LevelRules.countedMembers(List.of(old, fresh, coop), since::get, 10 * DAY, 3 * DAY);
        assertEquals(List.of(old), counted, "fresh members and players without a join time (coop) never count");
        assertEquals(List.of(old, fresh), LevelRules.countedMembers(List.of(old, fresh), since::get, 12 * DAY, 3 * DAY));
    }

    @Test
    void bestMemberPerWingAndAMemberWhoLeftTakesTheirPoints() {
        Map<SkillType, Integer> miner = new EnumMap<>(SkillType.class);
        miner.put(SkillType.MINING, 40);
        miner.put(SkillType.FORAGING, 5);
        Map<SkillType, Integer> lumberjack = new EnumMap<>(SkillType.class);
        lumberjack.put(SkillType.MINING, 3);
        lumberjack.put(SkillType.FORAGING, 50);
        // 1 tier per 5 levels, wings only for mining/foraging/combat.
        java.util.function.ToIntBiFunction<SkillType, Integer> tiers = (skill, level) ->
                skill == SkillType.MINING || skill == SkillType.FORAGING || skill == SkillType.COMBAT ? level / 5 : 0;
        Map<SkillType, Integer> both = LevelRules.bestTiers(List.of(miner, lumberjack), tiers);
        assertEquals(8, both.get(SkillType.MINING));
        assertEquals(10, both.get(SkillType.FORAGING));
        assertEquals(18, LevelRules.sum(both.values()), "a team that splits the wings is rewarded");
        Map<SkillType, Integer> afterLeave = LevelRules.bestTiers(List.of(miner), tiers);
        assertEquals(9, LevelRules.sum(afterLeave.values()), "the lumberjack left and took their Foraging tiers");
    }

    @Test
    void skillTotalsAddEverySkill() {
        Map<SkillType, Integer> a = new EnumMap<>(SkillType.class);
        a.put(SkillType.MINING, 10);
        a.put(SkillType.COMBAT, 5);
        assertEquals(List.of(15, 0), LevelRules.skillTotals(List.of(a, new EnumMap<>(SkillType.class))));
    }
}
