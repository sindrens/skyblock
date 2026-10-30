package net.skybornmc.skyblock.depths.zones;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeTemplateTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    private static TreeTemplates shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = TreeTemplateTest.class.getClassLoader().getResourceAsStream("depths-drops-defaults.yml")) {
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        TreeTemplates t = TreeTemplates.parse(y.getConfigurationSection(TreeTemplates.SECTION), 5, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        return t;
    }

    @Test
    void shippedTemplatesCoverEveryForestTier() throws Exception {
        TreeTemplates t = shipped();
        for (Zone z : set.zones(Aspect.FOREST)) {
            assertTrue(t.all().containsKey(z.tier()), "template for forest tier " + z.tier());
        }
        assertEquals(TreeTemplate.Shape.CONE, t.get(3).shape());
        assertEquals(2, t.get(6).trunkWidth());
        assertSame(TreeTemplate.DEFAULT, t.get(99), "unknown tier uses the default tree");
    }

    /** Every log of every shipped template has an open side (leaves can't be broken), sits low enough and in the box. */
    @Test
    void everyLogCanBeChopped() throws Exception {
        TreeTemplates templates = shipped();
        for (Zone z : set.zones(Aspect.FOREST)) {
            TreeTemplate tree = templates.get(z.tier());
            Box f = z.fields().getFirst().box();
            int logs = 0;
            int leaves = 0;
            for (int x = f.minX(); x <= f.maxX(); x++) {
                for (int y = f.minY(); y <= f.maxY(); y++) {
                    for (int zz = f.minZ(); zz <= f.maxZ(); zz++) {
                        Piece p = tree.at(f, x, y, zz);
                        if (p == Piece.LEAVES) {
                            leaves++;
                        }
                        if (p != Piece.RESOURCE) {
                            continue;
                        }
                        logs++;
                        assertTrue(y - f.minY() < 5, z.id() + ": log higher than 5 above the ground");
                        boolean open = false;
                        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                        for (int[] d : sides) {
                            int nx = x + d[0];
                            int nz = zz + d[1];
                            Piece n = f.contains(nx, y, nz) ? tree.at(f, nx, y, nz) : Piece.AIR;
                            open |= n == Piece.AIR || n == Piece.LIGHT_BLOCK;
                        }
                        assertTrue(open, z.id() + ": log at " + x + "," + y + "," + zz + " is walled in");
                    }
                }
            }
            assertTrue(logs >= 16 * 4, z.id() + ": at least 16 trees, logs " + logs);
            assertTrue(leaves > logs / 2, z.id() + ": trees have crowns");
        }
    }

    @Test
    void defaultMatchesTheOldForestAndLeavesTheMiddleFree() {
        Box f = set.zone("forest_t01").orElseThrow().fields().getFirst().box();
        TreeTemplate d = TreeTemplate.DEFAULT;
        int mid = (f.sizeX() / 2) / d.spacing() * d.spacing() + d.spacing() / 2;
        assertFalse(d.treeAt(f, mid, mid), "clearing in the middle");
        assertTrue(d.treeAt(f, 4, 4));
        assertEquals(4, d.trunkHeight(4, 4));
        assertEquals(5, d.trunkHeight(12, 4));
        assertEquals(Piece.RESOURCE, d.at(f, f.minX() + 4, f.minY(), f.minZ() + 4));
        assertEquals(Piece.LEAVES, d.at(f, f.minX() + 4, f.minY() + 4, f.minZ() + 4), "crown right above a 4-log trunk");
        assertEquals(Piece.AIR, d.at(f, f.minX() + 5, f.minY() + 3, f.minZ() + 4), "nothing next to the trunk");
    }

    @Test
    void wideTrunksAndShapes() {
        Box f = Box.of(0, 0, 0, 39, 7, 39);
        TreeTemplate wide = new TreeTemplate(TreeTemplate.Shape.ROUND, 4, 4, 2, 2, 2, 0, 8);
        assertTrue(wide.problems(5).isEmpty());
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                assertEquals(Piece.RESOURCE, wide.at(f, 4 + dx, 0, 4 + dz), "2x2 trunk");
            }
        }
        assertEquals(Piece.LEAVES, wide.at(f, 7, 4, 5), "crown reaches 2 past the trunk");
        TreeTemplate cone = new TreeTemplate(TreeTemplate.Shape.CONE, 4, 4, 1, 2, 3, 0, 8);
        assertEquals(Piece.LEAVES, cone.at(f, 6, 4, 4), "cone base radius 2");
        assertEquals(Piece.AIR, cone.at(f, 6, 6, 4), "cone tip is narrow");
        assertEquals(Piece.LEAVES, cone.at(f, 4, 6, 4), "cone tip on the trunk");
        TreeTemplate cap = new TreeTemplate(TreeTemplate.Shape.CAP, 4, 4, 1, 2, 3, 0, 8);
        assertEquals(Piece.LEAVES, cap.at(f, 6, 4, 4), "skirt at the edge");
        assertEquals(Piece.AIR, cap.at(f, 5, 4, 4), "skirt is hollow");
        assertEquals(Piece.LEAVES, cap.at(f, 5, 5, 4), "hat is full");
    }

    @Test
    void brokenTemplatesAreReportedAndFallBack() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                trees:
                  1: {shape: blob}
                  2: {trunk-min: 4, trunk-max: 9}
                  3: {trunk-width: 1, crown-radius: 4}
                  4: {crown-overlap: 2}
                  x: {shape: round}
                  5: {shape: flat, crown-radius: 3, crown-height: 2}
                """);
        List<String> problems = new ArrayList<>();
        TreeTemplates t = TreeTemplates.parse(y.getConfigurationSection("trees"), 5, problems::add);
        assertSame(TreeTemplate.DEFAULT, t.get(1));
        assertSame(TreeTemplate.DEFAULT, t.get(2));
        assertSame(TreeTemplate.DEFAULT, t.get(3));
        assertTrue(t.get(4).hidesLogs(), "allowed, but warned");
        assertEquals(TreeTemplate.Shape.FLAT, t.get(5).shape());
        assertEquals(5, problems.size(), problems.toString());
    }

    @Test
    void generateUsesTheTierTemplate() throws Exception {
        TreeTemplates templates = shipped();
        Zone z = set.zone("forest_t06").orElseThrow();
        Box f = z.fields().getFirst().box();
        ZoneCommands.Selection sel = ZoneCommands.select(set, z.id(), templates);
        Shapes.Shape shape = sel.parts().getLast().shape();
        assertEquals(Piece.RESOURCE, shape.at(f.minX() + 5, f.minY(), f.minZ() + 5), "dark oak: 2x2 trunk");
        Shapes.Shape plain = ZoneCommands.select(set, z.id()).parts().getLast().shape();
        assertEquals(Piece.AIR, plain.at(f.minX() + 5, f.minY(), f.minZ() + 5), "default: thin trunk");
    }
}
