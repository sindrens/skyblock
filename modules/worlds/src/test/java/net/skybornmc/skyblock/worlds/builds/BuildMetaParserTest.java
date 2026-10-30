package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMetaParserTest {

    @Test
    void parsesTheSpawnBase() {
        BuildMeta m = RealBuilds.meta("spawn_base_v1");
        assertEquals("spawn_base_v1.schem", m.schematic());
        assertEquals("spawn", m.world());
        assertEquals(0, m.originX());
        assertEquals(100, m.originY());
        assertTrue(m.pasteAir());
        assertTrue(m.pasteBiomes());
        assertEquals(new BuildBox(-111, 42, -111, 112, 124, 110), m.bounds());
        assertEquals(0.5, m.playerSpawn().feetX());
        assertEquals(180f, m.playerSpawn().yaw());
        assertEquals(726904, m.statsNonAir());
        assertEquals(1, m.points("npcs").size());
        BuildPoint guide = m.points("npcs").getFirst();
        assertEquals("guide", guide.id());
        assertEquals(2, guide.blockX());
        assertEquals(22f, guide.yaw());
        assertEquals(7, m.points("holograms").size());
        assertEquals("south", m.points("holograms").getFirst().extra("facing", "?"));
        assertEquals("[5, 4]", m.points("holograms").getFirst().extra("size", "?"));
        assertEquals(5, m.points("particles").size());
        assertEquals(4, m.points("viewpoints").size());
        assertEquals(1, m.points("tutorial").size());
        BuildRegion protectedRegion = m.region("regions", "spawn_protected").orElseThrow();
        assertEquals("protected", protectedRegion.kind());
        assertEquals(new BuildBox(-112, 35, -112, 112, 180, 112), protectedRegion.box());
    }

    @Test
    void keepsPortalsAndUnknownCategories() {
        BuildMeta gate = RealBuilds.meta("depths_gate_v1");
        BuildRegion portal = gate.region("portals", "to_spawn").orElseThrow();
        assertEquals("spawn", portal.extra().get("target"));
        assertEquals("{x: 7, y: 100, z: 14}", portal.extra().get("landing"));
        assertEquals(10, gate.points("treasure_spots").size());
        assertEquals(3, gate.points("causeway_starts").size());
        assertEquals(7.5, gate.playerSpawn().feetX());
        assertEquals(165f, gate.playerSpawn().yaw());
    }

    @Test
    void marksBlockCoordinatesAndFeetCoordinates() {
        BuildMeta island = RealBuilds.meta("islands_starter_meadow_v1");
        assertFalse(island.pasteAir());
        assertFalse(island.home().integer()); // 0.50: already a feet position
        assertEquals(0.5, island.home().feetX());
        assertEquals(101, island.home().y());
        assertEquals("starter_chest", island.points("containers").getFirst().id());
        BuildMeta mine = RealBuilds.meta("depths_mine_t01_coal_v1");
        BuildPoint arrival = mine.points("arrival").getFirst();
        assertEquals(-89, arrival.x()); // block coordinates: the pad block
        assertEquals(90f, arrival.yaw());
    }

    @Test
    void reportsBrokenEntriesAndKeepsTheRest() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                schematic: x_v1.schem
                world: spawn
                origin: { x: 1, y: 2 }
                npcs:
                  - { id: a, x: 1, y: 2, z: 3 }
                  - { id: a, x: 4, y: 5, z: 6 }
                  - { id: b }
                  - { x: 7, y: 8, z: 9 }
                regions:
                  - { id: r, from: { x: 0, y: 0, z: 0 }, to: { x: 5, y: 5, z: 5 } }
                  - { id: broken, from: { x: 0 } }
                """);
        List<String> problems = new ArrayList<>();
        BuildMeta m = BuildMetaParser.parse("x_v1", yaml, problems::add);
        assertNull(m.origin());
        assertEquals(List.of("a", "npcs_4"), m.points("npcs").stream().map(BuildPoint::id).toList());
        assertEquals(1, m.regions("regions").size());
        assertEquals(4, problems.size(), problems.toString());
    }
}
