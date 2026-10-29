package net.skybornmc.skyblock.depths.zones;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapesTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    private static Shapes.Shape shapeOf(Zone z, ZoneSet set) {
        List<Shapes.Part> parts = Shapes.zone(z, set.arena());
        return parts.getLast().shape();
    }

    @Test
    void resourcesOnlyInsideFieldsAndOpeningsClear() {
        for (Zone z : set.zones()) {
            Shapes.Shape shape = shapeOf(z, set);
            Box r = z.region();
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int y = r.minY(); y <= r.maxY(); y++) {
                    for (int zz = r.minZ(); zz <= r.maxZ(); zz++) {
                        Piece p = shape.at(x, y, zz);
                        if (p == Piece.RESOURCE) {
                            int fx = x, fy = y, fz = zz;
                            assertTrue(z.fields().stream().anyMatch(f -> f.box().contains(fx, fy, fz)), z.id() + " resource outside field");
                        }
                        if (z.gate().contains(x, y, zz) || (z.exit() != null && z.exit().contains(x, y, zz))) {
                            assertEquals(Piece.AIR, p, z.id() + " opening must be empty");
                        }
                    }
                }
            }
        }
    }

    @Test
    void spawnIsSafe() {
        for (Zone z : set.zones()) {
            Shapes.Shape shape = shapeOf(z, set);
            Point s = z.spawn();
            assertEquals(Piece.PAD, shape.at(s.blockX(), s.blockY() - 1, s.blockZ()), z.id());
            assertEquals(Piece.AIR, shape.at(s.blockX(), s.blockY(), s.blockZ()), z.id());
            assertEquals(Piece.AIR, shape.at(s.blockX(), s.blockY() + 1, s.blockZ()), z.id());
        }
        Shapes.Shape hub = Shapes.hubShape(set.hub());
        Point hs = set.hub().spawn();
        assertEquals(Piece.PAD, hub.at(hs.blockX(), hs.blockY() - 1, hs.blockZ()));
        assertEquals(Piece.AIR, hub.at(hs.blockX(), hs.blockY(), hs.blockZ()));
        // the world spawn (0.5, 100, 0.5) stays free under the brazier
        assertEquals(Piece.FLOOR, hub.at(0, 99, 0));
        for (int y = 100; y <= 104; y++) {
            Piece p = hub.at(0, y, 0);
            assertTrue(p == Piece.AIR || p == Piece.LIGHT_BLOCK, "passable at y " + y + ": " + p);
        }
        Shapes.Shape arena = Shapes.arenaShape(set.arena());
        Point as = set.arena().spawn();
        assertNotEquals(Piece.AIR, arena.at(as.blockX(), as.blockY() - 1, as.blockZ()));
        assertEquals(Piece.AIR, arena.at(as.blockX(), as.blockY(), as.blockZ()));
    }

    @Test
    void mineFieldIsAWalkableQuarry() {
        Zone z = set.zone("mine_t01").orElseThrow();
        Box f = z.fields().getFirst().box();
        int exposedTops = 0;
        int[][] tops = new int[f.sizeX()][f.sizeZ()];
        for (int x = f.minX(); x <= f.maxX(); x++) {
            for (int zz = f.minZ(); zz <= f.maxZ(); zz++) {
                int top = Integer.MIN_VALUE;
                for (int y = f.minY(); y <= f.maxY(); y++) {
                    Piece p = Shapes.mineField(f, x, y, zz);
                    assertTrue(p == Piece.RESOURCE || p == Piece.BASE || p == Piece.AIR);
                    if (p != Piece.AIR) {
                        top = y;
                    }
                }
                assertEquals(Piece.RESOURCE, Shapes.mineField(f, x, top, zz), "column top is ore");
                tops[x - f.minX()][zz - f.minZ()] = top;
                exposedTops++;
            }
        }
        assertTrue(exposedTops >= 300, "BUILDER_BRIEF: ≥ 300 exposed ore");
        // outer ring flush with the floor (step in without jumping)
        assertEquals(z.spawn().blockY() - 1, tops[0][0]);
        // terraces: neighbouring columns differ by at most one block outside the veins
        for (int i = 0; i < f.sizeX() - 1; i++) {
            for (int j = 0; j < f.sizeZ(); j++) {
                int lx = i;
                int a = tops[i][j];
                int b = tops[i + 1][j];
                boolean vein = isVein(f, lx, j) || isVein(f, lx + 1, j);
                if (!vein) {
                    assertTrue(Math.abs(a - b) <= 1, "step at " + i + "," + j);
                }
            }
        }
    }

    private static boolean isVein(Box f, int lx, int lz) {
        int ring = Math.min(Math.min(lx, lz), Math.min(f.sizeX() - 1 - lx, f.sizeZ() - 1 - lz));
        return ring >= 6 && lx % 6 == 0 && lz % 6 <= 3;
    }

    @Test
    void forestTreesAreReachable() {
        Zone z = set.zone("forest_t01").orElseThrow();
        Box f = z.fields().getFirst().box();
        int trees = 0;
        for (int lx = 0; lx < f.sizeX(); lx++) {
            for (int lz = 0; lz < f.sizeZ(); lz++) {
                int logs = 0;
                int maxRel = -1;
                for (int y = f.minY(); y <= f.maxY(); y++) {
                    Piece p = Shapes.forestField(f, f.minX() + lx, y, f.minZ() + lz);
                    if (p == Piece.RESOURCE) {
                        logs++;
                        maxRel = y - f.minY();
                    }
                    if (p == Piece.LEAVES) {
                        assertTrue(y - f.minY() >= 3, "leaves start 3 blocks above the ground");
                    }
                }
                if (logs > 0) {
                    trees++;
                    assertTrue(logs >= 4 && logs <= 5, "4–5 logs per trunk");
                    assertTrue(maxRel <= 4, "no log higher than 5 above the ground");
                }
            }
        }
        assertTrue(trees >= 16 && trees <= 24, "16–24 trees, was " + trees);
    }

    @Test
    void hubGatesAreOpenAndConnectToTheCauseways() {
        Shapes.Shape hub = Shapes.hubShape(set.hub());
        for (Aspect aspect : Aspect.values()) {
            Box gate = set.hub().wingGates().get(aspect);
            for (int x = gate.minX(); x <= gate.maxX(); x++) {
                for (int y = gate.minY(); y <= gate.maxY(); y++) {
                    for (int z = gate.minZ(); z <= gate.maxZ(); z++) {
                        assertEquals(Piece.AIR, hub.at(x, y, z), aspect + " wing gate open");
                    }
                }
            }
            Zone first = set.zones(aspect).getFirst();
            Box c = first.causeway();
            Box h = set.hub().region();
            // the hub column next to the causeway's middle has floor (no hole between hub and causeway)
            int cx = (c.minX() + c.maxX()) / 2;
            int cz = (c.minZ() + c.maxZ()) / 2;
            int hx = Math.max(h.minX(), Math.min(h.maxX(), cx));
            int hz = Math.max(h.minZ(), Math.min(h.maxZ(), cz));
            assertEquals(Piece.FLOOR, hub.at(hx, 99, hz), aspect + " apron");
        }
    }

    @Test
    void causewayHasFloorAndWalls() {
        Zone z = set.zone("mine_t02").orElseThrow();
        Box c = z.causeway();
        Shapes.Shape s = Shapes.causeway(c, 100);
        boolean alongX = c.sizeX() >= c.sizeZ();
        int mid = alongX ? (c.minX() + c.maxX()) / 2 : (c.minZ() + c.maxZ()) / 2;
        int lo = alongX ? c.minZ() : c.minX();
        int hi = alongX ? c.maxZ() : c.maxX();
        for (int a = lo; a <= hi; a++) {
            int x = alongX ? mid : a;
            int zz = alongX ? a : mid;
            assertEquals(Piece.FLOOR, s.at(x, 99, zz));
            Piece expected = a == lo || a == hi ? Piece.WALL : Piece.AIR;
            assertEquals(expected, s.at(x, 100, zz));
            assertEquals(expected, s.at(x, 101, zz));
        }
    }

    @Test
    void bossPortalIsFramedInTierTen() {
        Zone c10 = set.zone("combat_t10").orElseThrow();
        Shapes.Shape shape = shapeOf(c10, set);
        Box portal = set.arena().portal();
        assertEquals(Piece.AIR, shape.at(portal.minX(), portal.minY(), portal.minZ()));
        assertEquals(Piece.TRIM, shape.at(portal.minX(), portal.maxY() + 1, portal.minZ()));
        Shapes.Shape arena = Shapes.arenaShape(set.arena());
        Box back = set.arena().returnPortal();
        assertEquals(Piece.AIR, arena.at(back.minX(), back.minY(), back.minZ()));
        assertEquals(Piece.TRIM, arena.at(back.minX(), back.maxY() + 1, back.minZ()));
    }

    @Test
    void shapesAreDeterministic() {
        Zone z = set.zone("forest_t07").orElseThrow();
        Shapes.Shape a = shapeOf(z, set);
        Shapes.Shape b = shapeOf(z, set);
        Box r = z.region();
        for (int x = r.minX(); x <= r.maxX(); x += 3) {
            for (int y = r.minY(); y <= r.maxY(); y++) {
                for (int zz = r.minZ(); zz <= r.maxZ(); zz += 3) {
                    assertEquals(a.at(x, y, zz), b.at(x, y, zz));
                }
            }
        }
    }
}
