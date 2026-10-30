package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Living tool rules: XP gains, slot unlocks, prestige and Forge upgrades keeping progress. */
class ToolProgressTest {

    private static final List<Integer> SLOTS = ToolSlots.DEFAULT_SLOT_LEVELS;
    private final ToolCurve curve = new ToolCurve(400, 1.21, 50);

    private ToolData fresh() {
        return ToolData.fresh(UUID.randomUUID(), ToolKind.PICKAXE, 3, false, null);
    }

    private ToolData atLevel(int level) {
        ToolData t = fresh();
        return t.withProgress(level, curve.xpForLevel(level));
    }

    @Test
    void xpGainLevelsUp() {
        ToolProgress.Gain g = ToolProgress.addXp(fresh(), 450, curve);
        assertEquals(1, g.fromLevel());
        assertEquals(2, g.toLevel());
        assertTrue(g.levelledUp());
        assertEquals(450, g.tool().xp(), 1e-9);
        assertEquals(450, g.tool().stat(ToolData.STAT_XP));

        ToolProgress.Gain none = ToolProgress.addXp(fresh(), 0, curve);
        assertFalse(none.levelledUp());
        assertEquals(0, none.tool().xp());
        assertEquals(0, ToolProgress.addXp(fresh(), Double.NaN, curve).added());
    }

    @Test
    void xpStopsAtTheMaxLevelButLifetimeXpCounts() {
        ToolData max = atLevel(50);
        ToolProgress.Gain g = ToolProgress.addXp(max, 1000, curve);
        assertEquals(50, g.tool().level());
        assertEquals(curve.maxXp(), g.tool().xp(), 1e-6);
        assertEquals(0, g.added(), 1e-6);
        assertEquals(1000, g.tool().stat(ToolData.STAT_XP));
    }

    @Test
    void slotsUnlockAt5_15_30_45() {
        assertEquals(0, ToolSlots.unlocked(1, 0, SLOTS));
        assertEquals(0, ToolSlots.unlocked(4, 0, SLOTS));
        assertEquals(1, ToolSlots.unlocked(5, 0, SLOTS));
        assertEquals(1, ToolSlots.unlocked(14, 0, SLOTS));
        assertEquals(2, ToolSlots.unlocked(15, 0, SLOTS));
        assertEquals(3, ToolSlots.unlocked(30, 0, SLOTS));
        assertEquals(4, ToolSlots.unlocked(45, 0, SLOTS));
        assertEquals(4, ToolSlots.unlocked(50, 0, SLOTS));
        assertEquals(5, ToolSlots.unlocked(1, 1, SLOTS), "prestige: keeps the 4 level slots + the prestige slot");
        assertEquals(15, ToolSlots.unlockLevel(2, SLOTS));
        assertEquals(-1, ToolSlots.unlockLevel(ToolSlots.PRESTIGE_SLOT, SLOTS));
        assertEquals(30, ToolSlots.nextUnlock(15, SLOTS));
        assertEquals(-1, ToolSlots.nextUnlock(45, SLOTS));
    }

    @Test
    void enchantGateFromTheEnchantPlan() {
        // ENCHANTS §2.4 table: max-V enchant at tool level 5/15/30/45/50 → I/II/III/V/V; max-X → I/III/VI/IX/X
        assertEquals(List.of(1, 2, 3, 5, 5), List.of(5, 15, 30, 45, 50).stream().map(l -> ToolSlots.maxEnchantLevel(5, l, 50)).toList());
        assertEquals(List.of(1, 3, 6, 9, 10), List.of(5, 15, 30, 45, 50).stream().map(l -> ToolSlots.maxEnchantLevel(10, l, 50)).toList());
        assertEquals(List.of(1, 1, 2, 3, 3), List.of(5, 15, 30, 45, 50).stream().map(l -> ToolSlots.maxEnchantLevel(3, l, 50)).toList());
        assertEquals(50, ToolSlots.effectiveLevel(1, 1, 50), "a prestiged tool counts as level 50");
    }

    @Test
    void prestigeRules() {
        ToolData lv49 = atLevel(49);
        assertEquals(ToolProgress.PrestigeCheck.NOT_MAX_LEVEL, ToolProgress.canPrestige(lv49, curve, 0, 1, true));
        ToolData max = atLevel(50).withPerks(List.of(new ToolPerk(1, "vein_rend", 2))).addStat(ToolData.STAT_BLOCKS, 12_345);
        assertEquals(ToolProgress.PrestigeCheck.DISABLED, ToolProgress.canPrestige(max, curve, 0, 1, false));
        assertEquals(ToolProgress.PrestigeCheck.OK, ToolProgress.canPrestige(max, curve, 0, 1, true));

        ToolData p = ToolProgress.prestige(max, 0);
        assertEquals(1, p.level());
        assertEquals(0, p.xp());
        assertEquals(1, p.prestige());
        assertEquals(max.perks(), p.perks(), "keeps perks");
        assertEquals(12_345, p.stat(ToolData.STAT_BLOCKS), "keeps stats");
        assertEquals(4, max.slots(SLOTS));
        assertEquals(5, p.slots(SLOTS), "+1 slot");

        ToolData againMax = p.withProgress(50, curve.maxXp());
        assertEquals(ToolProgress.PrestigeCheck.ALREADY_THIS_SEASON, ToolProgress.canPrestige(againMax, curve, 0, 1, true),
                "max 1 per tool per season");
        assertEquals(ToolProgress.PrestigeCheck.OK, ToolProgress.canPrestige(againMax, curve, 1, 1, true), "next season again");
        ToolData twice = ToolProgress.prestige(againMax, 1);
        assertEquals(2, twice.prestige());
        assertEquals(5, twice.slots(SLOTS), "still one prestige slot");
    }

    @Test
    void upgradeKeepsProgress() {
        ToolData tool = new ToolData(UUID.randomUUID(), ToolKind.PICKAXE, 1, 17, curve.xpForLevel(17) + 5, 1, 0,
                List.of(new ToolPerk(1, "vein_rend", 2)), "frostbite", "scholar", true, Map.of(ToolData.STAT_BLOCKS, 500L));
        ToolData up = ToolProgress.upgrade(tool, 2);
        assertEquals(2, up.tier());
        assertEquals(tool.level(), up.level());
        assertEquals(tool.xp(), up.xp());
        assertEquals(tool.prestige(), up.prestige());
        assertEquals(tool.perks(), up.perks());
        assertEquals("frostbite", up.skin());
        assertEquals("scholar", up.ability());
        assertEquals(500, up.stat(ToolData.STAT_BLOCKS));
        assertEquals(1, up.stat(ToolData.STAT_FORGED));
        assertEquals(tool.id(), up.id(), "same unique tool");
        assertFalse(up.starter(), "a forged tool is no longer a starter piece");
    }

    @Test
    void normalizeAfterCurveChange() {
        ToolData t = fresh().withProgress(10, curve.xpForLevel(5));
        assertEquals(5, ToolProgress.normalize(t, curve).level());
    }

    @Test
    void perkValidation() {
        ToolData lv15 = atLevel(15);
        assertNull(ToolProgress.perkProblem(lv15, List.of(new ToolPerk(1, "a", 1), new ToolPerk(2, "b", 1)), SLOTS));
        assertNotNull(ToolProgress.perkProblem(lv15, List.of(new ToolPerk(3, "a", 1)), SLOTS), "slot 3 locked at Lv 15");
        assertNotNull(ToolProgress.perkProblem(lv15, List.of(new ToolPerk(1, "a", 1, 2), new ToolPerk(2, "b", 1)), SLOTS), "overlap");
        assertNotNull(ToolProgress.perkProblem(lv15, List.of(new ToolPerk(1, "a", 1), new ToolPerk(2, "a", 2)), SLOTS), "twice");
    }

    @Test
    void codecRoundTrip() {
        List<ToolPerk> perks = List.of(new ToolPerk(1, "vein_rend", 2), new ToolPerk(2, "dragonfire_blast", 1, 2));
        assertEquals(perks, ToolCodec.perks(ToolCodec.perks(perks)));
        Map<String, Long> stats = Map.of("blocks", 12L, "kills", 3L);
        assertEquals(stats, ToolCodec.stats(ToolCodec.stats(stats)));
        assertTrue(ToolCodec.perks("not json").isEmpty());
        assertTrue(ToolCodec.stats("[1,2]").isEmpty());
        assertEquals(1, ToolCodec.perks("[{\"slot\":1,\"id\":\"ok\",\"level\":1},{\"slot\":9,\"id\":\"bad\",\"level\":1}]").size());
        UUID u = UUID.randomUUID();
        assertEquals(List.of(new ToolCodec.Owner(u, "Steve", 5)), ToolCodec.owners(ToolCodec.owners(List.of(new ToolCodec.Owner(u, "Steve", 5)))));
    }
}
