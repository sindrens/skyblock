package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.DepthsLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthsWiringTest {

    @Test
    void layoutMatchesBuildsByTheirSchematicName() {
        LayoutFile layout = RealBuilds.layout();
        assertEquals(100, layout.floorY());
        assertEquals(new BuildBox(-45, 50, -45, 45, 160, 45), layout.gate());
        assertTrue(layout.isGate("depths_gate_v1"));
        assertTrue(layout.isGate("depths_gate_v12"));
        assertFalse(layout.isGate("depths_gate_vx"));
        assertEquals("mine_t01_coal", layout.zoneOf("depths_mine_t01_coal_v1").id());
        assertEquals("combat_t01_graveyard", layout.zoneOf("depths_combat_t01_graveyard_v3").id());
        assertNull(layout.zoneOf("spawn_base_v1"));
        DepthsLayout.LayoutZone t06 = layout.zones().stream().filter(z -> z.id().equals("mine_t06_lapis")).findFirst().orElseThrow();
        assertEquals("east", t06.entrance());
        assertEquals("south", t06.exit());
        assertEquals(-8, t06.centerZ());
    }

    @Test
    void wiresAMineZone() {
        DepthsLayout d = DepthsWiring.layout(RealBuilds.layout(), RealBuilds.meta("depths_mine_t01_coal_v1"));
        assertNull(d.hub());
        assertEquals(1, d.built().size());
        DepthsLayout.BuiltZone z = d.built().getFirst();
        assertEquals("mine", z.wing());
        assertEquals(1, z.tier());
        assertEquals(new DepthsLayout.Pos(-88.5, 100, -7.5, 90, 0), z.arrival());
        assertEquals(new BuildBox(-82, 100, -10, -82, 104, -6), z.gate());
        assertEquals(new BuildBox(-160, 100, -11, -160, 104, -5), z.exit());
        assertNull(z.arena());
        assertEquals(List.of(new DepthsLayout.BuiltField("ore_field", new BuildBox(-137, 97, -28, -98, 99, 11), "coal_ore", "cobblestone", 10)),
                z.fields());
    }

    @Test
    void wiresAForestAndACombatZone() {
        DepthsLayout.BuiltZone forest = DepthsWiring.layout(RealBuilds.layout(), RealBuilds.meta("depths_forest_t01_oak_v1")).built().getFirst();
        assertEquals("oak_log", forest.fields().getFirst().block());
        assertEquals("stripped_oak_log", forest.fields().getFirst().depleted());
        assertEquals(new BuildBox(152, 100, -11, 159, 104, -5), forest.exit());
        DepthsLayout.BuiltZone combat = DepthsWiring.layout(RealBuilds.layout(), RealBuilds.meta("depths_combat_t01_graveyard_v1")).built().getFirst();
        assertEquals(new BuildBox(-33, 99, -141, 17, 115, -94), combat.arena());
        assertEquals(new BuildBox(-11, 99, -160, -5, 105, -160), combat.exit()); // exit_passage, not the safe exit_path
        assertEquals(new BuildBox(-10, 100, -83, -6, 104, -83), combat.gate());
        assertTrue(combat.fields().isEmpty());
    }

    @Test
    void wiresTheGate() {
        DepthsLayout d = DepthsWiring.layout(RealBuilds.layout(), RealBuilds.meta("depths_gate_v1"));
        assertTrue(d.built().isEmpty());
        assertEquals(new DepthsLayout.Pos(7.5, 100, 14.5, 165, 0), d.hub().spawn());
        assertEquals(new BuildBox(-45, 100, -11, -37, 104, -5), d.hub().wingGates().get("mine"));
        assertEquals(3, d.hub().wingGates().size());
    }
}
