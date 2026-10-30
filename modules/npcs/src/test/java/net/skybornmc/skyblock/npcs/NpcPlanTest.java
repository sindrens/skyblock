package net.skybornmc.skyblock.npcs;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcPlanTest {

    private static Material item(String name) {
        return switch (name) {
            case "diamond_pickaxe" -> Material.DIAMOND_PICKAXE;
            case "anvil" -> Material.ANVIL;
            default -> null;
        };
    }

    private static NpcPlan.Inputs inputs(List<String> held, List<String> sources) {
        NpcsConfig c = new NpcsConfig();
        return new NpcPlan.Inputs(c.npcRoles, c.roleActions, List.of("bank: Notch"), held, c.hologramSkip, sources);
    }

    @Test
    void parsesEverythingFromTheBuildPoints() {
        SpotsTest.FakePoints points = new SpotsTest.FakePoints(List.of(
                SpotsTest.point("spawn_market_square_v1", "npcs", "bank", 0, 99, 81, 180, Map.of()),
                SpotsTest.point("spawn_base_v1", "holograms", "info_board", 10, 103, -11, 0, Map.of("facing", "south")),
                SpotsTest.point("spawn_base_v1", "holograms", "miniboss_timer", 10, 103, -11, 0, Map.of()),
                SpotsTest.point("spawn_hall_of_heroes_v1", "leaderboards", "mining", -61, 108, -62, 0, Map.of("facing", "south"))));
        NpcPlan plan = NpcPlan.compute(inputs(List.of("bank: anvil"), List.of("mining: skill mining")), points,
                NpcPlanTest::item, t -> true);
        assertTrue(plan.pointsAvailable());
        assertEquals(List.of(), plan.problems());
        assertEquals(1, plan.spots().npcs().size());
        assertEquals(1, plan.spots().holograms().size(), "skipped hologram ids stay out");
        assertEquals(1, plan.spots().boards().size());
        assertEquals("Notch", plan.skins().get("bank"));
        assertEquals(Material.ANVIL, plan.held().get("bank"));
        assertTrue(plan.sources().get("mining") instanceof BoardSource.Skill);
        assertEquals("bank", plan.roles().roleOf("bank", null));
    }

    @Test
    void problemsKeepTheirOrderAndMissingTextsAreListed() {
        SpotsTest.FakePoints points = new SpotsTest.FakePoints(List.of(
                SpotsTest.point("b", "npcs", "bank", 0, 99, 81, 0, Map.of()),
                SpotsTest.point("b", "npcs", "bank", 5, 99, 81, 0, Map.of()),
                SpotsTest.point("b", "holograms", "silent", 0, 100, 0, 0, Map.of()),
                SpotsTest.point("b", "holograms", "spoken", 0, 100, 0, 0, Map.of())));
        NpcPlan plan = NpcPlan.compute(inputs(List.of("bank: not_an_item"), List.of("mining: skill nope")), points,
                NpcPlanTest::item, t -> !t.id().equals("silent"));
        List<String> problems = plan.problems();
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("npcs.held-items"), problems.toString());
        assertTrue(problems.get(1).contains("used twice"), problems.toString());
        assertTrue(problems.get(2).contains("leaderboards.sources"), problems.toString());
        assertEquals("messages/npcs.yml -> holograms: no text for silent (not shown)", problems.get(3));
        assertTrue(plan.held().isEmpty());
        assertTrue(plan.sources().get("mining") instanceof BoardSource.None, "a broken source shows Coming soon");
    }

    @Test
    void withoutBuildPointsNothingIsPlacedButTheRulesAreParsed() {
        NpcPlan plan = NpcPlan.compute(inputs(List.of(), List.of("baltop: money")), null, NpcPlanTest::item, t -> true);
        assertFalse(plan.pointsAvailable());
        assertEquals(0, plan.spots().total());
        assertEquals(1, plan.sources().size());
    }

    @Test
    void inputsAreSnapshots() {
        java.util.ArrayList<String> held = new java.util.ArrayList<>(List.of("bank: anvil"));
        NpcPlan.Inputs in = inputs(held, List.of());
        held.clear(); // a reload replacing the config while the plan is parsed off the main thread
        assertEquals(List.of("bank: anvil"), in.heldItems());
    }
}
