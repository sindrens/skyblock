package net.skybornmc.skyblock.depths.zones;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneLayoutTest {

    static final double BORDER_RADIUS = 1000;
    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    @Test
    void tierCountsMatchWorldMd() {
        assertEquals(12, set.zones(Aspect.MINE).size());
        assertEquals(11, set.zones(Aspect.FOREST).size());
        assertEquals(10, set.zones(Aspect.COMBAT).size());
        assertEquals("mine_t01", set.zones(Aspect.MINE).getFirst().id());
        assertEquals("combat_t10", set.zones(Aspect.COMBAT).getLast().id());
    }

    @Test
    void everythingInsideTheBorderWithoutOverlaps() {
        assertEquals(List.of(), ZoneValidator.validate(set, BORDER_RADIUS));
        // and with room to spare (Bedrock render distance, border visuals)
        assertEquals(List.of(), ZoneValidator.validate(set, BORDER_RADIUS - 64));
    }

    @Test
    void zonesAreChunkAlignedAndSized() {
        for (Zone z : set.zones()) {
            Box r = z.region();
            assertEquals(0, Math.floorMod(r.minX(), 16), z.id());
            assertEquals(0, Math.floorMod(r.minZ(), 16), z.id());
            assertEquals(0, Math.floorMod(r.maxX() + 1, 16), z.id());
            assertEquals(0, Math.floorMod(r.maxZ() + 1, 16), z.id());
            assertEquals(80, r.sizeX(), z.id());
            assertEquals(80, r.sizeZ(), z.id());
            assertEquals(5, z.gate().sizeY(), z.id());
            assertEquals(5, Math.max(z.gate().sizeX(), z.gate().sizeZ()), z.id());
            for (Field f : z.fields()) {
                assertEquals(40, f.box().sizeX(), z.id());
                assertEquals(40, f.box().sizeZ(), z.id());
            }
        }
        Box arena = set.arena().region();
        assertEquals(0, Math.floorMod(arena.minX(), 16));
        assertEquals(0, Math.floorMod(arena.minZ(), 16));
    }

    @Test
    void wingsGoTheRightWayAndBendBack() {
        Zone m1 = set.zone("mine_t01").orElseThrow();
        Zone m6 = set.zone("mine_t06").orElseThrow();
        Zone m7 = set.zone("mine_t07").orElseThrow();
        Zone m12 = set.zone("mine_t12").orElseThrow();
        assertTrue(m1.region().maxX() < set.hub().region().minX(), "mine starts west of The Gate");
        assertTrue(m6.region().minX() < m1.region().minX(), "mine goes west");
        assertEquals(m6.region().minX(), m7.region().minX(), "the U turns in place");
        assertTrue(m7.region().minZ() > m6.region().maxZ(), "mine returns on the south row");
        assertEquals(m1.region().minX(), m12.region().minX(), "the last mine tier ends next to The Gate");

        Zone f1 = set.zone("forest_t01").orElseThrow();
        assertTrue(f1.region().minX() > set.hub().region().maxX(), "forest starts east");
        Zone c1 = set.zone("combat_t01").orElseThrow();
        Zone c10 = set.zone("combat_t10").orElseThrow();
        assertTrue(c1.region().maxZ() < set.hub().region().minZ(), "combat starts north");
        assertTrue(c10.region().maxZ() < set.hub().region().minZ() && c10.region().minZ() >= c1.region().minZ(),
                "combat tier 10 ends near The Gate");

        Box boss = set.arena().region();
        double bossZ = (boss.minZ() + boss.maxZ() + 1) / 2.0;
        assertTrue(bossZ < -800 && bossZ > -900, "boss arena at the far north apex (~Z -850), was " + bossZ);
        for (Zone z : set.zones(Aspect.COMBAT)) {
            assertTrue(boss.minZ() > -BORDER_RADIUS && boss.maxZ() < z.region().minZ(), "boss arena beyond every combat tier");
        }
        assertTrue(c10.region().contains(set.arena().portal()), "boss portal in combat tier 10");
        assertEquals("combat_t10", set.arena().portalZone());
    }

    @Test
    void causewaysConnectGatesAndExits() {
        for (Aspect aspect : Aspect.values()) {
            List<Zone> wing = set.zones(aspect);
            for (int i = 0; i < wing.size(); i++) {
                Zone z = wing.get(i);
                Box c = z.causeway();
                assertNotNull(c, z.id());
                assertTrue(c.growHorizontal(1).intersectsColumns(z.gate()), z.id() + ": causeway reaches the gate");
                if (i == 0) {
                    assertTrue(c.growHorizontal(1).intersectsColumns(set.hub().region()), z.id() + ": causeway starts at The Gate");
                    Box wingGate = set.hub().wingGates().get(aspect);
                    assertTrue(wingGate.minX() <= z.gate().maxX() && wingGate.maxX() >= z.gate().minX()
                            || wingGate.minZ() <= z.gate().maxZ() && wingGate.maxZ() >= z.gate().minZ(),
                            z.id() + ": lines up with the wing gate");
                } else {
                    Zone prev = wing.get(i - 1);
                    assertNotNull(prev.exit(), prev.id());
                    assertTrue(c.growHorizontal(1).intersectsColumns(prev.exit()), z.id() + ": causeway reaches " + prev.id() + "'s exit");
                }
                if (i == wing.size() - 1) {
                    assertNull(z.exit(), z.id() + " is the last tier");
                }
            }
        }
    }

    @Test
    void spawnsFaceIntoTheZone() {
        for (Zone z : set.zones()) {
            Point s = z.spawn();
            assertEquals(100, s.blockY(), z.id());
            double dist = Math.min(Math.min(Math.abs(s.x() - z.gate().minX()), Math.abs(s.x() - z.gate().maxX())),
                    Math.min(Math.abs(s.z() - z.gate().minZ()), Math.abs(s.z() - z.gate().maxZ())));
            assertTrue(dist <= 8, z.id() + ": arrival pad next to the gate");
            // one step in the facing direction moves away from the gate
            double yaw = Math.toRadians(s.yaw());
            double nx = s.x() - Math.sin(yaw) * 5;
            double nz = s.z() + Math.cos(yaw) * 5;
            double gx = (z.gate().minX() + z.gate().maxX() + 1) / 2.0;
            double gz = (z.gate().minZ() + z.gate().maxZ() + 1) / 2.0;
            assertTrue(Math.hypot(nx - gx, nz - gz) > Math.hypot(s.x() - gx, s.z() - gz), z.id() + ": faces away from the gate");
        }
    }

    @Test
    void writesTheDefaultFile() throws IOException {
        // Regenerates build/generated/zones.yml; copy it to src/main/resources/zones.yml after layout changes.
        Path out = Path.of("build", "generated", "zones.yml");
        Files.createDirectories(out.getParent());
        Files.writeString(out, ZoneYaml.write(set));
        assertTrue(Files.size(out) > 1000);
    }
}
