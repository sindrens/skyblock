package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.DepthsLayout;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BUILDS: the builders' layout (server/builds/depths_layout.yml) merged into the shipped zones.yml. */
class BuildLayoutMergeTest {

    static ZoneSet shipped;
    static List<DepthsLayout.LayoutZone> zones;
    static BuildBox gate;

    @BeforeAll
    static void load() throws IOException, InvalidConfigurationException {
        try (InputStream in = BuildLayoutMergeTest.class.getClassLoader().getResourceAsStream("zones.yml")) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> problems = new ArrayList<>();
            shipped = ZoneParser.parse(yaml, "zones.yml", problems::add);
            assertEquals(List.of(), problems);
        }
        // a test-side reading of the real layout file (the worlds module's parser is tested there)
        YamlConfiguration layout = YamlConfiguration.loadConfiguration(new File("../../server/builds/depths_layout.yml"));
        zones = new ArrayList<>();
        for (Map<?, ?> z : layout.getMapList("zones")) {
            Map<?, ?> from = (Map<?, ?>) z.get("from");
            Map<?, ?> to = (Map<?, ?>) z.get("to");
            Map<?, ?> center = (Map<?, ?>) z.get("center");
            zones.add(new DepthsLayout.LayoutZone((String) z.get("id"), (String) z.get("wing"), (Integer) z.get("tier"),
                    new BuildBox(i(from, "x"), i(from, "y"), i(from, "z"), i(to, "x"), i(to, "y"), i(to, "z")), i(center, "x"), i(center, "z"),
                    (String) z.get("entrance"), (String) z.get("exit")));
        }
        gate = new BuildBox(-45, 50, -45, 45, 160, 45);
    }

    private static int i(Map<?, ?> m, String k) {
        return ((Number) m.get(k)).intValue();
    }

    private static Zone zone(ZoneSet set, String id) {
        return set.zone(id).orElseThrow();
    }

    static final DepthsLayout.BuiltZone MINE_T01 = new DepthsLayout.BuiltZone("depths_mine_t01_coal_v1", "mine", 1,
            new DepthsLayout.Pos(-88.5, 100, -7.5, 90, 0), new BuildBox(-82, 100, -10, -82, 104, -6),
            new BuildBox(-160, 100, -11, -160, 104, -5), null,
            List.of(new DepthsLayout.BuiltField("ore_field", new BuildBox(-137, 97, -28, -98, 99, 11), "coal_ore", "cobblestone", 10)));

    @Test
    void layoutOnlyRecentresPlaceholdersAndCauseways() {
        BuildLayoutMerge.Result r = BuildLayoutMerge.merge(shipped, new DepthsLayout(100, gate, zones, List.of(), null));
        Zone t01 = zone(r.set(), "mine_t01");
        assertFalse(t01.built());
        assertEquals(Box.of(-80, 96, -12, -46, 105, -4), t01.causeway()); // from the zone's east side to The Gate's west edge
        assertEquals(Box.of(-81, 100, -10, -81, 104, -6), t01.gate());
        assertEquals(Box.of(-160, 100, -10, -160, 104, -6), t01.exit());
        assertEquals(-7.5, t01.spawn().z()); // moved with the gate
        assertEquals(shipped.zone("mine_t01").orElseThrow().region().minY(), t01.region().minY()); // placeholder height kept
        Zone t02 = zone(r.set(), "mine_t02");
        assertEquals(Box.of(-192, 96, -12, -161, 105, -4), t02.causeway());
        // the U-turn: tier 6 leaves south, tier 7 arrives from the north
        assertEquals(Box.of(-684, 96, 32, -676, 105, 63), zone(r.set(), "mine_t07").causeway());
        assertEquals(Box.of(-12, 96, -80, -4, 105, -46), zone(r.set(), "combat_t01").causeway());
        assertEquals(Box.of(46, 96, -12, 79, 105, -4), zone(r.set(), "forest_t01").causeway());
        // The Gate: layout footprint, wing gates on the new centre lines
        Hub hub = r.set().hub();
        assertEquals(-45, hub.region().minX());
        assertEquals(45, hub.region().maxZ());
        assertEquals(-12, hub.wingGates().get(Aspect.MINE).minZ());
        assertEquals(-12, hub.wingGates().get(Aspect.COMBAT).minX());
        assertFalse(hub.built());
        // names, tiers and unlock levels are untouched
        for (Zone before : shipped.zones()) {
            Zone after = zone(r.set(), before.id());
            assertEquals(before.name(), after.name());
            assertEquals(before.tier(), after.tier());
            assertEquals(before.requiredLevel(), after.requiredLevel());
            assertEquals(before.fields().size(), after.fields().size());
        }
        assertEquals(shipped.arena().region(), r.set().arena().region());
        assertEquals(Box.of(111, 100, -89, 113, 103, -89), r.set().arena().portal()); // moved with combat_t10 (layout x 64..143)
        assertNoLayoutProblems(r.set());
    }

    @Test
    void appliedZoneTakesTheBuildData() {
        BuildLayoutMerge.Result r = BuildLayoutMerge.merge(shipped, new DepthsLayout(100, gate, zones, List.of(MINE_T01), null));
        Zone t01 = zone(r.set(), "mine_t01");
        assertTrue(t01.built());
        assertEquals(Box.of(-160, 50, -48, -81, 169, 31), t01.region());
        assertEquals(new Point(-88.5, 100, -7.5, 90, 0), t01.spawn());
        assertEquals(Box.of(-82, 100, -10, -82, 104, -6), t01.gate());
        assertEquals(1, t01.fields().size());
        Field ore = t01.fields().getFirst();
        assertEquals("ore", ore.id()); // keeps the engine region id mine_t01_ore
        assertEquals(Box.of(-137, 97, -28, -98, 99, 11), ore.box());
        assertEquals(new Field.Resource("coal_ore", "cobblestone", 10), ore.resources().getFirst());
        assertEquals("The Old Shaft", t01.name());
        assertTrue(r.changes().stream().anyMatch(c -> c.startsWith("mine_t01: wired to build depths_mine_t01_coal_v1")));
        assertTrue(r.unmatched().isEmpty());
        assertNoLayoutProblems(r.set());

        // a second merge (another build applied later) keeps the wired zone as it is
        BuildLayoutMerge.Result again = BuildLayoutMerge.merge(r.set(), new DepthsLayout(100, gate, zones, List.of(), null));
        assertEquals(t01, zone(again.set(), "mine_t01"));
    }

    @Test
    void appliedGateSetsSpawnAndMarksBuilt() {
        DepthsLayout.BuiltHub hubBuild = new DepthsLayout.BuiltHub("depths_gate_v1", new DepthsLayout.Pos(7.5, 100, 14.5, 165, 0),
                Map.of("mine", new BuildBox(-45, 100, -11, -37, 104, -5)));
        BuildLayoutMerge.Result r = BuildLayoutMerge.merge(shipped, new DepthsLayout(100, gate, zones, List.of(), hubBuild));
        Hub hub = r.set().hub();
        assertTrue(hub.built());
        assertEquals(Box.of(-45, 50, -45, 45, 160, 45), hub.region());
        assertEquals(new Point(7.5, 100, 14.5, 165, 0), hub.spawn());
        assertEquals(Box.of(-45, 100, -11, -37, 104, -5), hub.wingGates().get(Aspect.MINE));
        assertNoLayoutProblems(r.set());
    }

    @Test
    void mergedFileRoundTrips() throws InvalidConfigurationException {
        ZoneSet merged = BuildLayoutMerge.merge(shipped, new DepthsLayout(100, gate, zones, List.of(MINE_T01), null)).set();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(ZoneYaml.write(merged));
        List<String> problems = new ArrayList<>();
        assertEquals(merged, ZoneParser.parse(yaml, "zones.yml", problems::add));
        assertEquals(List.of(), problems);
    }

    @Test
    void unknownBuiltZoneIsReported() {
        DepthsLayout.BuiltZone ghost = new DepthsLayout.BuiltZone("depths_mine_t40_x_v1", "mine", 40, null, null, null, null, List.of());
        BuildLayoutMerge.Result r = BuildLayoutMerge.merge(shipped, new DepthsLayout(100, gate, zones, List.of(ghost), null));
        assertEquals(1, r.unmatched().size());
    }

    @Test
    void gateInsetFromTheEdgeIsAccepted() {
        Box zone = Box.of(-160, 50, -48, -81, 169, 31);
        assertTrue(Box.of(-82, 100, -10, -82, 104, -6).nearEdgeOf(zone, 3));
        assertFalse(Box.of(-120, 100, -10, -120, 104, -6).nearEdgeOf(zone, 3));
        assertFalse(Box.of(-79, 100, -10, -79, 104, -6).nearEdgeOf(zone, 3)); // outside the footprint
    }

    /** Overlaps, edges, spawns and fields are fine (the border check needs a world). */
    private static void assertNoLayoutProblems(ZoneSet set) {
        List<String> problems = ZoneValidator.validate(set, 1000);
        assertEquals(List.of(), problems);
    }
}
