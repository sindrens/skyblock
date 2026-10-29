package net.skybornmc.skyblock.depths.zones;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneParserTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void roundTripsTheGeneratedLayout() throws Exception {
        ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");
        List<String> problems = new ArrayList<>();
        ZoneSet parsed = ZoneParser.parse(yaml(ZoneYaml.write(set)), "zones.yml", problems::add);
        assertEquals(List.of(), problems);
        assertEquals(set, parsed);
    }

    @Test
    void shippedFileIsValid() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("zones.yml")) {
            assertNotNull(in, "zones.yml resource");
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        ZoneSet set = ZoneParser.parse(y, "zones.yml", problems::add);
        assertEquals(List.of(), problems);
        assertEquals(33, set.zones().size());
        assertNotNull(set.hub());
        assertNotNull(set.arena());
        assertEquals(List.of(), ZoneValidator.validate(set, ZoneLayoutTest.BORDER_RADIUS));
        for (Zone z : set.zones()) {
            assertFalse(z.built(), z.id() + " ships as a placeholder");
        }
    }

    @Test
    void everyBlockInTheShippedFileExists() throws ClassNotFoundException {
        // Material can't be initialized without a server; its constant names are enough here.
        java.util.Set<String> materials = new java.util.HashSet<>();
        for (java.lang.reflect.Field f : Class.forName("org.bukkit.Material", false, getClass().getClassLoader()).getDeclaredFields()) {
            if (f.isEnumConstant() && !f.getName().startsWith("LEGACY_")) {
                materials.add(f.getName());
            }
        }
        assertTrue(materials.size() > 1000, "material list readable");
        ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");
        List<String> blocks = new ArrayList<>();
        set.zones().forEach(z -> {
            blocks.addAll(z.palette().values());
            z.fields().forEach(f -> f.resources().forEach(r -> {
                blocks.add(r.block());
                blocks.add(r.depleted());
            }));
        });
        blocks.addAll(set.hub().palette().values());
        blocks.addAll(set.arena().palette().values());
        for (Piece piece : Piece.values()) {
            blocks.add(piece.defaultBlock());
        }
        for (String block : blocks) {
            String id = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
            assertTrue(materials.contains(id.toUpperCase(java.util.Locale.ROOT)), "unknown block " + block);
        }
    }

    @Test
    void brokenZonesAreSkippedWithAReason() throws Exception {
        String text = """
                world: depths
                zones:
                  good:
                    aspect: mine
                    tier: 1
                    region: { corner-1: "0, 90, 0", corner-2: "79, 110, 79" }
                    spawn: "5.5, 100, 5.5"
                    gate: { corner-1: "0, 100, 3", corner-2: "0, 104, 7" }
                    fields:
                      ore:
                        corner-1: [20, 97, 20]
                        corner-2: "59 99 59"
                        resources:
                          - { block: "minecraft:IRON_ORE", depleted: tuff, respawn-seconds: 0 }
                          - { depleted: tuff }
                  no_region:
                    aspect: forest
                    tier: 1
                    spawn: "0, 100, 0"
                    gate: { corner-1: "0, 100, 0", corner-2: "0, 104, 4" }
                  bad_aspect:
                    aspect: fishing
                    tier: 1
                  dupe:
                    aspect: mine
                    tier: 1
                    region: { corner-1: "200, 90, 0", corner-2: "279, 110, 79" }
                    spawn: "205.5, 100, 5.5"
                    gate: { corner-1: "200, 100, 3", corner-2: "200, 104, 7" }
                """;
        List<String> problems = new ArrayList<>();
        ZoneSet set = ZoneParser.parse(yaml(text), "zones.yml", problems::add);
        assertEquals(1, set.zones().size());
        Zone good = set.zones().getFirst();
        assertEquals("good", good.id());
        assertEquals(0, good.requiredLevel());
        assertNull(good.exit());
        assertEquals(Box.of(20, 97, 20, 59, 99, 59), good.fields().getFirst().box());
        Field.Resource res = good.fields().getFirst().resources().getFirst();
        assertEquals("iron_ore", res.block());
        assertNull(res.respawnSeconds(), "out of range falls back to the engine default");
        assertEquals(1, good.fields().getFirst().resources().size());
        assertTrue(problems.stream().anyMatch(p -> p.contains("respawn-seconds")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("missing 'block'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_region") && p.contains("missing 'region'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad_aspect") && p.contains("fishing")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("dupe") && p.contains("defined twice")), problems.toString());
        assertNull(set.hub());
    }

    @Test
    void validatorFindsOverlapsBorderAndMisplacedParts() throws Exception {
        String text = """
                zones:
                  a:
                    aspect: mine
                    tier: 1
                    region: { corner-1: "0, 90, 0", corner-2: "79, 110, 79" }
                    spawn: "500, 100, 500"
                    gate: { corner-1: "40, 100, 40", corner-2: "40, 104, 44" }
                  b:
                    aspect: mine
                    tier: 3
                    region: { corner-1: "70, 90, 0", corner-2: "1149, 110, 79" }
                    spawn: "75.5, 100, 5.5"
                    gate: { corner-1: "70, 100, 3", corner-2: "70, 104, 7" }
                """;
        ZoneSet set = ZoneParser.parse(yaml(text), "zones.yml", p -> { });
        List<String> problems = ZoneValidator.validate(set, 1000);
        assertTrue(problems.stream().anyMatch(p -> p.contains("zones.a overlaps zones.b")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("zones.b") && p.contains("border")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("zones.a.spawn")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("zones.a.gate")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("mine wing") && p.contains("without gaps")), problems.toString());
    }

    @Test
    void coordinatesFormats() {
        assertEquals(new Point(1.5, 100, -2.5, 180, 0), ZoneParser.point("1.5, 100, -2.5, 180", "spawn"));
        assertEquals(new Point(0, 64, 0, 0, 0), ZoneParser.point(List.of(0, 64, 0), "spawn"));
        assertThrows(IllegalArgumentException.class, () -> ZoneParser.point("1, 2", "spawn"));
        assertThrows(IllegalArgumentException.class, () -> ZoneParser.point("1, 2, 3, 4, 5, 6", "spawn"));
        assertThrows(IllegalArgumentException.class, () -> ZoneParser.corner("a, b, c", "corner-1"));
        assertThrows(IllegalArgumentException.class, () -> ZoneParser.corner("0, 9000, 0", "corner-1"));
        assertEquals("tuff", ZoneParser.normalize(" Minecraft:TUFF "));
    }

    @Test
    void paletteKeepsItsOrder() {
        Map<String, String> p = ZoneParser.orderedPalette(Map.of("wall", "a", "zzz", "b", "floor", "c"));
        assertEquals(List.of("floor", "wall", "zzz"), List.copyOf(p.keySet()));
    }
}
