package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneAccess;
import net.skybornmc.skyblock.depths.zones.ZoneLayout;
import net.skybornmc.skyblock.depths.zones.ZoneSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierRulesTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    @Test
    void tierCountsMatchWorldMd() {
        assertEquals(12, set.zones(Aspect.MINE).size());
        assertEquals(11, set.zones(Aspect.FOREST).size());
        assertEquals(10, set.zones(Aspect.COMBAT).size());
    }

    @Test
    void unlockAtExactlyTheRequiredLevel() {
        assertTrue(TierRules.unlocked(15, 15));
        assertTrue(TierRules.unlocked(16, 15));
        assertFalse(TierRules.unlocked(14, 15));
        assertTrue(TierRules.unlocked(0, 0), "tier 1 is open for everyone");
    }

    @Test
    void zoneAccessUsesTheWingSkill() {
        Zone gold = set.zone("mine_t04").orElseThrow();
        ZoneAccess access = ZoneAccess.of(gold, 9);
        assertFalse(access.allowed());
        assertEquals(SkillType.MINING, access.skill());
        assertEquals(15, access.required());
        assertEquals(9, access.level());
        assertTrue(ZoneAccess.of(gold, 15).allowed());
        assertTrue(ZoneAccess.of(gold, null).allowed(), "no skill system: open");
        assertFalse(ZoneAccess.of(gold, null).checked());
        assertEquals(SkillType.FORAGING, ZoneAccess.of(set.zone("forest_t02").orElseThrow(), 0).skill());
        assertEquals(SkillType.COMBAT, ZoneAccess.of(set.zone("combat_t02").orElseThrow(), 0).skill());
    }

    @Test
    void levelUpOpensExactlyTheTiersInBetween() {
        List<Zone> all = set.zones();
        // Mining 9 -> 21 crosses 10 (iron), 15 (gold), 20 (redstone)
        List<String> opened = TierRules.newlyUnlocked(all, SkillType.MINING, 9, 21).stream().map(Zone::id).toList();
        assertEquals(List.of("mine_t03", "mine_t04", "mine_t05"), opened);
        assertEquals(List.of("mine_t04"), TierRules.newlyUnlocked(all, SkillType.MINING, 14, 15).stream().map(Zone::id).toList());
        assertTrue(TierRules.newlyUnlocked(all, SkillType.MINING, 15, 16).isEmpty(), "already open before");
        assertTrue(TierRules.newlyUnlocked(all, SkillType.MINING, 20, 10).isEmpty(), "levels going down open nothing");
        assertTrue(TierRules.newlyUnlocked(all, SkillType.MINING, 0, 0).isEmpty(), "tier 1 is never 'newly' unlocked");
        List<Zone> forest = TierRules.newlyUnlocked(all, SkillType.FORAGING, 4, 5);
        assertEquals(List.of("forest_t02"), forest.stream().map(Zone::id).toList(), "other wings are not touched");
    }

    @Test
    void progressBar() {
        assertEquals(6, TierRules.filled(9, 15, 10));
        assertEquals(0, TierRules.filled(0, 15, 10));
        assertEquals(0, TierRules.filled(-3, 15, 10));
        assertEquals(9, TierRules.filled(14, 15, 10), "one level short is never a full bar");
        assertEquals(9, TierRules.filled(65, 66, 10));
        assertEquals(10, TierRules.filled(15, 15, 10));
        assertEquals(10, TierRules.filled(0, 0, 10));
        assertEquals(0, TierRules.filled(5, 10, 0));
        assertEquals(6, TierRules.missing(9, 15));
        assertEquals(0, TierRules.missing(20, 15));
    }

    @Test
    void highestAndNext() {
        List<Zone> mine = set.zones(Aspect.MINE);
        assertEquals("mine_t02", TierRules.highestUnlocked(mine, 9).id());
        assertEquals("mine_t03", TierRules.nextLocked(mine, 9).id());
        assertEquals("mine_t12", TierRules.highestUnlocked(mine, 99).id());
        assertNull(TierRules.nextLocked(mine, 99));
        assertNull(TierRules.highestUnlocked(List.of(), 5));
    }
}
