package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FX-21 (7 Nov 2026): the grind-area NPCs come from npcs.extra-spots and are placed like build NPCs. */
class ExtraSpotsTest {

    @Test
    void defaultsPutTheFourAreaNpcsInTheGrindWorld() {
        NpcsConfig c = new NpcsConfig();
        List<String> problems = new ArrayList<>();
        List<BuildPoint> extra = ExtraSpots.parse(c.extraSpots, problems);
        assertEquals(List.of(), problems);
        assertEquals(List.of("bramble", "flint", "marla", "warden_hale"), extra.stream().map(BuildPoint::id).toList());
        assertTrue(extra.stream().allMatch(p -> p.world().equals("grind")));

        Spots spots = Spots.from(ExtraSpots.over(new SpotsTest.FakePoints(List.of()), extra),
                RoleRules.parse(c.npcRoles, c.roleActions), Set.of());
        assertEquals(List.of("farm_merchant", "mine_merchant", "fish_merchant", "arena_master"),
                spots.npcs().stream().map(Spots.Npc::role).toList());
        RoleRules rules = RoleRules.parse(c.npcRoles, c.roleActions);
        assertEquals("sell", rules.actionOf("fish_merchant").command());
        assertEquals("pve boss", rules.actionOf("arena_master").command());
    }

    @Test
    void extrasJoinTheBuildNpcsAndBadLinesAreSkipped() {
        BuildPoint guide = SpotsTest.point("spawn", Spots.NPCS, "guide", 1, 2, 3, 0f, Map.of());
        List<String> problems = new ArrayList<>();
        List<BuildPoint> extra = ExtraSpots.parse(List.of("a: sell grind 1 2 3 90", "broken line", "b: sell grind x 2 3 0",
                "a: sell grind 4 5 6 0", ""), problems);
        assertEquals(1, extra.size());
        assertEquals(3, problems.size());
        var points = ExtraSpots.over(new SpotsTest.FakePoints(List.of(guide)), extra);
        assertEquals(List.of("guide", "a"), points.points(Spots.NPCS).stream().map(BuildPoint::id).toList());
        assertEquals(List.of(), points.points(Spots.HOLOGRAMS));
        assertTrue(points.point(Spots.NPCS, "a").isPresent());
        assertTrue(points.builds().contains(ExtraSpots.BUILD));
        assertEquals("sell", extra.getFirst().extra("role", ""));
    }
}
