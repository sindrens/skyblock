package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.3: crate yard build points to crates on pedestals. */
class PedestalsTest {

    private static BuildPoint point(String id, double x, double y, double z) {
        return new BuildPoint("spawn_crate_yard_v1", "crates", id, "spawn", x, y, z, 0f, 0f, Map.of());
    }

    /** The six pedestals of server/builds/spawn_crate_yard_v1.yml. */
    private static List<BuildPoint> yard() {
        return List.of(point("common", 80, 102, -56), point("uncommon", 84, 102, -50), point("rare", 86, 102, -43),
                point("epic", 86, 102, -35), point("legendary", 84, 102, -28), point("monthly", 80, 102, -22));
    }

    @Test
    void defaultMappingPutsSixCratesOnTheYardAndNoConqueror() {
        CrateCatalog catalog = Fixtures.bundled();
        Pedestals.Result r = Pedestals.plan(yard(), PhysicalCrates.mapping(new CratesSettings().physicalPoints),
                id -> catalog.crate(id).isPresent());
        assertEquals(List.of(), r.problems());
        assertEquals(List.of("ember", "cinder", "blaze", "inferno", "phoenix", "eclipse"),
                r.pedestals().stream().map(Pedestals.Pedestal::crateId).toList());
        Pedestals.Pedestal ember = r.pedestals().getFirst();
        assertEquals("common", ember.pointId());
        assertEquals(List.of(80, 102, -56), List.of(ember.x(), ember.y(), ember.z()));
        assertEquals("spawn", ember.world());
        assertFalse(r.pedestals().stream().anyMatch(p -> p.crateId().equals("conqueror")), "Conqueror stays /crates-only");
    }

    @Test
    void seventhPedestalEnablesConqueror() {
        CrateCatalog catalog = Fixtures.bundled();
        List<BuildPoint> points = new ArrayList<>(yard());
        points.add(point("conqueror", 90, 102, -39));
        List<String> mapping = new ArrayList<>(new CratesSettings().physicalPoints);
        mapping.add("conqueror:conqueror");
        Pedestals.Result r = Pedestals.plan(points, PhysicalCrates.mapping(mapping), id -> catalog.crate(id).isPresent());
        assertEquals(7, r.pedestals().size());
        assertEquals("conqueror", r.pedestals().getLast().crateId());
    }

    @Test
    void badLinesAreReportedAndSkipped() {
        Pedestals.Result r = Pedestals.plan(yard(), PhysicalCrates.mapping(List.of("common:ember", "uncommon:nope",
                "rare:ember", "missing:blaze")), id -> Set.of("ember", "blaze").contains(id));
        assertEquals(List.of("ember"), r.pedestals().stream().map(Pedestals.Pedestal::crateId).toList());
        assertEquals(3, r.problems().size(), r.problems().toString());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("'nope'")));
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("more than one pedestal")));
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("'missing'")));
    }

    @Test
    void noBuildYetIsQuiet() {
        Pedestals.Result r = Pedestals.plan(List.of(), PhysicalCrates.mapping(new CratesSettings().physicalPoints), id -> true);
        assertEquals(List.of(), r.pedestals());
        assertEquals(List.of(), r.problems(), "one log line from the caller instead of six");
        assertEquals(List.of(), Pedestals.plan(null, Map.of(), id -> true).pedestals());
    }

    @Test
    void blocksFromSettingsElseTheIcon() {
        Map<String, String> blocks = PhysicalCrates.mapping(new CratesSettings().physicalBlocks);
        assertEquals("minecraft:barrel[facing=up]", Pedestals.blockData("ember", blocks, "barrel"));
        assertEquals("minecraft:vault[facing=west,ominous=true]", Pedestals.blockData("eclipse", blocks, "vault"));
        assertEquals("minecraft:copper_chest", Pedestals.blockData("other", blocks, "copper_chest"));
        assertEquals("minecraft:chest", Pedestals.blockData("other", Map.of(), null));
        for (CrateCatalog.Crate c : Fixtures.bundled().crates()) {
            assertTrue(blocks.containsKey(c.id()), "every bundled crate has a block: " + c.id());
        }
    }

    @Test
    void blockAndChunkKeysAreDistinct() {
        Set<Long> keys = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -64; y <= 320; y += 64) {
                for (int z = -3; z <= 3; z++) {
                    assertTrue(keys.add(Pedestals.Pedestal.blockKey(x, y, z)), x + " " + y + " " + z);
                }
            }
        }
        Pedestals.Pedestal p = new Pedestals.Pedestal("common", "ember", "spawn", 80, 102, -56);
        assertEquals(5, p.chunkX());
        assertEquals(-4, p.chunkZ());
        assertEquals(Pedestals.Pedestal.chunkKey(5, -4), p.chunkKey());
        assertTrue(Pedestals.Pedestal.chunkKey(5, -4) != Pedestals.Pedestal.chunkKey(-4, 5));
    }

    @Test
    void clickCooldown() {
        assertTrue(PhysicalCrates.clickAllowed(null, 1000, 400));
        assertFalse(PhysicalCrates.clickAllowed(1000L, 1200, 400));
        assertTrue(PhysicalCrates.clickAllowed(1000L, 1400, 400));
        assertTrue(PhysicalCrates.clickAllowed(5000L, 1000, 400), "clock went back");
    }
}
