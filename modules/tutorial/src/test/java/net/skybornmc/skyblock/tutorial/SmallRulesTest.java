package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.tutorial.Gates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compass math, target specs, starter chest lists, portal gates and menu ids. */
class SmallRulesTest {

    @Test
    void compassFollowsMinecraftYaw() {
        // Facing south (yaw 0): a target south is ahead, east is on the left, west on the right, north behind.
        assertEquals(Compass.Direction.AHEAD, Compass.direction(0, 0, 50, 30, 135, 2));
        assertEquals(Compass.Direction.LEFT, Compass.direction(0, 50, 0, 30, 135, 2));
        assertEquals(Compass.Direction.RIGHT, Compass.direction(0, -50, 0, 30, 135, 2));
        assertEquals(Compass.Direction.BEHIND, Compass.direction(0, 0, -50, 30, 135, 2));
        // Facing north (yaw 180 or -180): the portal north of spawn is ahead.
        assertEquals(Compass.Direction.AHEAD, Compass.direction(180, 0, -95, 30, 135, 2));
        assertEquals(Compass.Direction.AHEAD, Compass.direction(-180, 1, -95, 30, 135, 2));
        assertEquals(Compass.Direction.HERE, Compass.direction(90, 1, 1, 30, 135, 2));
        assertEquals(170.0, Compass.normalize(-190), 1e-9);
        assertEquals(1f, Compass.distanceProgress(0, 100));
        assertEquals(0.05f, Compass.distanceProgress(500, 100));
    }

    @Test
    void targetSpecsParse() {
        assertEquals(Optional.of(new TargetSpec.Point("spawn", 0.5, 104, -96.5)), TargetSpec.parse("spawn:0.5,104,-96.5"));
        assertEquals(Optional.of(new TargetSpec.Npc("depths_forge")), TargetSpec.parse("npc:depths_forge"));
        assertEquals(Optional.of(new TargetSpec.Role("forge")), TargetSpec.parse("role:Forge"));
        assertEquals(Optional.of(new TargetSpec.Build("portals", "to_island")), TargetSpec.parse("build:portals:to_island"));
        assertTrue(TargetSpec.parse("spawn:1,2").isEmpty());
        assertTrue(TargetSpec.parse("").isEmpty());
        assertTrue(TargetSpec.parse("build:x").isEmpty());
    }

    @Test
    void defaultTrailNodesAndTargetsAreValid() {
        TutorialConfig c = new TutorialConfig();
        for (String node : c.trailNodes) {
            assertTrue(TargetSpec.parse(node).orElseThrow() instanceof TargetSpec.Point, node);
        }
        assertTrue(TargetSpec.parse(c.portalTarget).isPresent());
        assertTrue(TargetSpec.parse(c.forgeTarget).isPresent());
        assertTrue(TargetSpec.parse(c.helpTarget).isPresent());
    }

    @Test
    void defaultStarterChestMatchesThePlan() {
        Set<String> known = Set.of("wheat_seeds", "bone_meal", "oak_sapling", "lava_bucket", "bread", "crafting_table", "chest");
        StarterSpec.Result r = StarterSpec.chest(new TutorialConfig().chestItems, known::contains);
        assertEquals(List.of(), r.problems());
        assertEquals(7, r.entries().size());
        assertEquals(new StarterSpec.Entry(15, "wheat_seeds", 8), r.entries().getFirst());
        assertTrue(r.entries().stream().noneMatch(e -> e.material().equals("ice") || e.material().equals("cactus")), "TQ17");
        assertEquals(List.of(10, 11, 12, 13, 14), StarterSpec.slots(new TutorialConfig().kitSlots));
    }

    @Test
    void starterListsReportBadLines() {
        StarterSpec.Result r = StarterSpec.chest(List.of("x: bread: 1", "30: bread: 1", "5: bread: 99", "5: rock: 1", "6: bread: 2",
                "6: bread: 3", "{7=bread: 4}"), "bread"::equals);
        assertEquals(5, r.problems().size(), "4 bad lines + 1 doubled slot");
        assertEquals(3, r.entries().size());
        assertEquals(-1, r.entries().get(1).slot(), "a doubled slot goes to the next free slot");
        assertEquals(7, r.entries().get(2).slot());
        StarterSpec.Result pack = StarterSpec.pack(List.of("bread: 16", "bread", "a:b:c", "rock: 1"), "bread"::equals);
        assertEquals(2, pack.entries().size());
        assertEquals(2, pack.problems().size());
        assertEquals(1, pack.entries().get(1).amount());
    }

    @Test
    void portalTargetsMapToGates() {
        assertEquals(Optional.of(Gates.DEPTHS), Gates.forPortalTarget("depths"));
        assertEquals(Optional.of(Gates.WARZONE), Gates.forPortalTarget("WARZONE"));
        assertTrue(Gates.forPortalTarget("spawn").isEmpty());
        assertTrue(Gates.forPortalTarget(null).isEmpty());
    }

    @Test
    void menuIdsComeFromTheClassName() {
        assertEquals("island", Menu.idOf("IslandMenu"));
        assertEquals("island-stats", Menu.idOf("IslandStatsMenu"));
        assertEquals("forge", Menu.idOf("ForgeMenu"));
        assertEquals("hub", Menu.idOf("HubMenu"));
        assertEquals("menu", Menu.idOf("Menu"));
        assertEquals("satchel", Menu.idOf("SatchelMenu"));
    }
}
