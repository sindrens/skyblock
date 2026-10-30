package net.skybornmc.skyblock.worlds.protection;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.common.region.RegionFlag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagIndexTest {

    private static final FlagRules RULES = FlagRules.parse(
            List.of("depths: build, interact, fire"),
            List.of("protected 10: build, interact", "field 100: allow build", "safe 20: pve-taken, mob-target",
                    "open 10: allow interact", "combat_zone 5: ender-pearl", "info 10:"),
            List.of("b1:special=safe"),
            p -> {
                throw new AssertionError(p);
            });

    private static FlagIndex.Source src(String owner, String id, String kind, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new FlagIndex.Source(owner, new FlagRegion(id, kind, world, new BuildBox(x1, y1, z1, x2, y2, z2)));
    }

    @Test
    void worldFlagsApplyEverywhereInTheWorld() {
        FlagIndex idx = FlagIndex.build(RULES, List.of());
        assertTrue(idx.denied("depths", 1000, 64, -1000, RegionFlag.BUILD));
        assertTrue(idx.denied("depths", 0, 0, 0, RegionFlag.FIRE));
        assertFalse(idx.denied("depths", 0, 0, 0, RegionFlag.ENDER_PEARL));
        assertFalse(idx.denied("islands", 0, 100, 0, RegionFlag.BUILD));
        assertFalse(idx.hasWorld("islands"));
        assertTrue(idx.hasWorld("Depths"));
        assertEquals(FlagRules.NONE, idx.resolve("spawn", 0, 0, 0, RegionFlag.BUILD));
    }

    @Test
    void fieldBeatsTheProtectedWorldButOnlyInsideItsBox() {
        FlagIndex idx = FlagIndex.build(RULES, List.of(
                src("depths", "mine_t01_coal", "field", "depths", -150, 99, -20, -90, 105, 20),
                src("depths_mine_t01_coal_v1", "zone", "protected", "depths", -160, 50, -48, -81, 169, 31)));
        assertEquals(FlagRules.ALLOW, idx.resolve("depths", -120, 100, 0, RegionFlag.BUILD), "ore field: engine decides");
        assertTrue(idx.denied("depths", -120, 110, 0, RegionFlag.BUILD), "above the field: protected zone");
        assertTrue(idx.denied("depths", -155, 100, 0, RegionFlag.BUILD), "zone, outside the field");
        assertTrue(idx.denied("depths", 0, 100, 0, RegionFlag.BUILD), "outside everything: world flag");
        assertTrue(idx.denied("depths", -120, 100, 0, RegionFlag.INTERACT), "field allows only build");
        assertTrue(idx.denied("depths", -120, 100, 0, RegionFlag.FIRE), "world flag still applies in the field");
    }

    @Test
    void sameChunkDifferentBoxesAndChunkEdges() {
        FlagIndex idx = FlagIndex.build(RULES, List.of(src("spawn_x", "zone", "combat_zone", "warzone", -1, 0, -1, 0, 255, 0)));
        // The box crosses chunk borders in both directions (-1 is chunk -1, 0 is chunk 0).
        assertTrue(idx.denied("warzone", -1, 10, -1, RegionFlag.ENDER_PEARL));
        assertTrue(idx.denied("warzone", 0, 10, 0, RegionFlag.ENDER_PEARL));
        assertTrue(idx.denied("warzone", -1, 10, 0, RegionFlag.ENDER_PEARL));
        assertFalse(idx.denied("warzone", 1, 10, 0, RegionFlag.ENDER_PEARL));
        assertFalse(idx.denied("warzone", -2, 10, -1, RegionFlag.ENDER_PEARL));
        assertFalse(idx.denied("warzone", 15, 10, 15, RegionFlag.ENDER_PEARL));
    }

    @Test
    void samePriorityDenyWinsHigherPriorityAllowWins() {
        FlagIndex idx = FlagIndex.build(RULES, List.of(
                src("a", "p", "protected", "spawn", 0, 0, 0, 10, 10, 10),
                src("a", "o", "open", "spawn", 0, 0, 0, 10, 10, 10)));
        assertTrue(idx.denied("spawn", 5, 5, 5, RegionFlag.INTERACT), "protected (deny) and open (allow) tie at 10: deny");
        FlagIndex withField = FlagIndex.build(RULES, List.of(
                src("a", "p", "protected", "spawn", 0, 0, 0, 10, 10, 10),
                src("a", "f", "field", "spawn", 2, 2, 2, 3, 3, 3)));
        assertFalse(withField.denied("spawn", 2, 2, 2, RegionFlag.BUILD));
        assertTrue(withField.denied("spawn", 4, 4, 4, RegionFlag.BUILD));
    }

    @Test
    void lowerPriorityStillAppliesToFlagsTheHigherOneDoesNotSet() {
        FlagIndex idx = FlagIndex.build(RULES, List.of(
                src("b", "safe", "safe", "depths", 0, 99, 0, 10, 110, 10),
                src("depths", "combat_t01", "combat_zone", "depths", -50, 50, -50, 50, 169, 50)));
        assertTrue(idx.denied("depths", 5, 100, 5, RegionFlag.MOB_TARGET));
        assertTrue(idx.denied("depths", 5, 100, 5, RegionFlag.ENDER_PEARL), "combat zone (5) under the safe zone (20)");
        assertFalse(idx.denied("depths", 20, 100, 20, RegionFlag.MOB_TARGET));
        assertTrue(idx.denied("depths", 20, 100, 20, RegionFlag.ENDER_PEARL));
    }

    @Test
    void overridesAndUnflaggedKinds() {
        FlagIndex idx = FlagIndex.build(RULES, List.of(
                src("b1", "special", "plot", "spawn", 0, 0, 0, 5, 5, 5),
                src("b2", "special", "plot", "spawn", 10, 0, 10, 15, 5, 15),
                src("b3", "note", "info", "spawn", 0, 0, 0, 5, 5, 5),
                src("b4", "x", "unknown", "spawn", 0, 0, 0, 5, 5, 5)));
        assertTrue(idx.denied("spawn", 1, 1, 1, RegionFlag.PVE_TAKEN), "b1:special became kind safe");
        assertFalse(idx.denied("spawn", 11, 1, 11, RegionFlag.PVE_TAKEN), "b2:special keeps kind plot");
        assertEquals(1, idx.regionCount(), "plot, info (no flags) and unknown kinds are not indexed");
        assertEquals(List.of("b1:special (safe, 20)"), idx.regionsAt("spawn", 1, 1, 1));
        assertEquals(List.of("pve-taken", "mob-target"), idx.deniedAt("spawn", 1, 1, 1));
        assertEquals(List.of(), idx.regionsAt("nowhere", 1, 1, 1));
    }
}
