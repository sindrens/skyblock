package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The placeholder builds as pure functions "position → {@link Piece}" (BUILDER_BRIEF §8–§11 templates, simplified).
 * Every position inside a part's box gets a piece (air included), so building twice gives the same result
 * (idempotent) and the shapes are unit-testable without a server.
 */
public final class Shapes {

    /** A position inside {@code box} → what goes there. */
    @FunctionalInterface
    public interface Shape {
        Piece at(int x, int y, int z);
    }

    /**
     * One thing to build: a box, its shape and the blocks it uses.
     *
     * @param label    for progress messages ({@code mine_t01}, {@code mine_t01 causeway}, {@code gate})
     * @param resource block id for {@link Piece#RESOURCE} ({@code null} if none)
     * @param biome    biome to set in the box ({@code null} = leave)
     */
    public record Part(String label, Box box, Shape shape, Map<String, String> palette, String resource, String biome) {
    }

    /** Wall height around zones and causeways (blocks above the floor). */
    static final int WALL_HEIGHT = 2;
    /** Lights on walls every n blocks. */
    static final int LIGHT_SPACING = 8;

    private Shapes() {
    }

    /** All parts of the set in build order: The Gate, then per zone its causeway and the zone, then the boss arena. */
    public static List<Part> parts(ZoneSet set) {
        List<Part> parts = new ArrayList<>();
        if (set.hub() != null) {
            parts.add(hub(set.hub()));
        }
        for (Zone z : set.zones()) {
            parts.addAll(zone(z, set.arena()));
        }
        if (set.arena() != null) {
            parts.add(arena(set.arena()));
        }
        return parts;
    }

    /** The zone's causeway (if any) and the zone itself, with the default tree template. */
    public static List<Part> zone(Zone zone, BossArena arena) {
        return zone(zone, arena, TreeTemplate.DEFAULT);
    }

    /** The zone's causeway (if any) and the zone itself; forest fields use {@code trees} (M4.7). */
    public static List<Part> zone(Zone zone, BossArena arena, TreeTemplate trees) {
        List<Part> parts = new ArrayList<>();
        String resource = zone.fields().isEmpty() || zone.fields().getFirst().resources().isEmpty() ? null
                : zone.fields().getFirst().resources().getFirst().block();
        if (zone.causeway() != null) {
            parts.add(new Part(zone.id() + " causeway", zone.causeway(), causeway(zone.causeway(), zone.spawn().blockY()),
                    zone.palette(), null, null));
        }
        List<Box> portals = new ArrayList<>();
        if (arena != null && arena.portal() != null && zone.id().equals(arena.portalZone())) {
            portals.add(arena.portal());
        }
        parts.add(new Part(zone.id(), zone.region(), zoneShape(zone, portals, trees), zone.palette(), resource, zone.biome()));
        return parts;
    }

    public static Part hub(Hub hub) {
        return new Part(Hub.ID, hub.region(), hubShape(hub), hub.palette(), null, null);
    }

    public static Part arena(BossArena arena) {
        return new Part(BossArena.ID, arena.region(), arenaShape(arena), arena.palette(), null, null);
    }

    // ------------------------------------------------------------------ helpers

    /** Horizontal long axis of an opening/portal box: true = along X. */
    private static boolean longX(Box b) {
        return b.sizeX() >= b.sizeZ();
    }

    /** Frame around an opening: grown by {@code side} along its long axis and {@code top} above; null if not a frame block. */
    static boolean frame(Box opening, int side, int top, int x, int y, int z) {
        if (opening.contains(x, y, z)) {
            return false;
        }
        boolean lx = longX(opening);
        int minX = opening.minX() - (lx ? side : 0);
        int maxX = opening.maxX() + (lx ? side : 0);
        int minZ = opening.minZ() - (lx ? 0 : side);
        int maxZ = opening.maxZ() + (lx ? 0 : side);
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= opening.minY() && y <= opening.maxY() + top;
    }

    /** Underside taper: at {@code depth} blocks below the floor block the island is {@code step} blocks narrower per level. */
    private static boolean underside(Box box, int x, int z, int depth, int step) {
        int inset = (depth - 1) * step;
        return x >= box.minX() + inset && x <= box.maxX() - inset && z >= box.minZ() + inset && z <= box.maxZ() - inset;
    }

    // ------------------------------------------------------------------ tier zones

    static Shape zoneShape(Zone zone, List<Box> portals) {
        return zoneShape(zone, portals, TreeTemplate.DEFAULT);
    }

    static Shape zoneShape(Zone zone, List<Box> portals, TreeTemplate trees) {
        TreeTemplate tree = trees == null ? TreeTemplate.DEFAULT : trees;
        Box r = zone.region();
        int stand = zone.spawn().blockY();
        int sx = zone.spawn().blockX();
        int sz = zone.spawn().blockZ();
        Field field = zone.fields().isEmpty() ? null : zone.fields().getFirst();
        Box arena = zone.arena();
        Box gate = zone.gate();
        Box exit = zone.exit();
        return (x, y, z) -> {
            for (Box portal : portals) {
                if (portal.contains(x, y, z)) {
                    return Piece.AIR;
                }
                if (frame(portal, 1, 1, x, y, z) || (y == portal.minY() - 1 && portal.growHorizontal(1).containsColumn(x, z))) {
                    return Piece.TRIM;
                }
            }
            if (field != null && field.box().contains(x, y, z)) {
                return zone.aspect() == Aspect.FOREST ? tree.at(field.box(), x, y, z) : mineField(field.box(), x, y, z);
            }
            if (gate.contains(x, y, z) || (exit != null && exit.contains(x, y, z))) {
                return Piece.AIR;
            }
            if (frame(gate, 1, 1, x, y, z)) {
                return Piece.TRIM;
            }
            boolean opening = gate.containsColumn(x, z) || (exit != null && exit.containsColumn(x, z));
            boolean edge = x == r.minX() || x == r.maxX() || z == r.minZ() || z == r.maxZ();
            if (y == stand - 1) {
                if (Math.abs(x - sx) <= 1 && Math.abs(z - sz) <= 1) {
                    return Piece.PAD;
                }
                if (arena != null && arena.containsColumn(x, z)) {
                    int cx = (arena.minX() + arena.maxX()) / 2;
                    int cz = (arena.minZ() + arena.maxZ()) / 2;
                    return Math.abs(x - cx) <= 2 && Math.abs(z - cz) <= 2 ? Piece.PAD : Piece.ARENA;
                }
                if (field != null && zone.aspect() == Aspect.FOREST && field.box().containsColumn(x, z)) {
                    return Piece.GROUND;
                }
                return Piece.FLOOR;
            }
            if (y < stand - 1) {
                return underside(r, x, z, stand - 1 - y, 3) ? Piece.BASE : Piece.AIR;
            }
            if (edge && !opening) {
                if (y < stand + WALL_HEIGHT) {
                    return Piece.WALL;
                }
                if (y == stand + WALL_HEIGHT && Math.floorMod(x + z, LIGHT_SPACING) == 0) {
                    return Piece.LIGHT;
                }
            }
            if (y == stand + 2 && x == sx && z == sz) {
                return Piece.LIGHT_BLOCK;
            }
            if (field != null && zone.aspect() == Aspect.MINE && y == field.box().maxY() + 2
                    && field.box().containsColumn(x, z)
                    && Math.floorMod(x - field.box().minX(), 4) == 0 && Math.floorMod(z - field.box().minZ(), 4) == 0) {
                return Piece.LIGHT_BLOCK;
            }
            if (arena != null && y == stand + 2 && arena.containsColumn(x, z)
                    && Math.floorMod(x - arena.minX(), 5) == 2 && Math.floorMod(z - arena.minZ(), 5) == 2) {
                return Piece.LIGHT_BLOCK;
            }
            return Piece.AIR;
        };
    }

    /**
     * A shallow quarry (BUILDER_BRIEF §9.2): the outer ring flush with the floor, 1-block terraces down to a pit, and
     * low ore veins in the pit. Every column's top block is ore; blocks under it are the base block.
     */
    static Piece mineField(Box f, int x, int y, int z) {
        int lx = x - f.minX();
        int lz = z - f.minZ();
        int ring = Math.min(Math.min(lx, lz), Math.min(f.maxX() - x, f.maxZ() - z));
        int top = Math.max(f.minY(), f.maxY() - (ring <= 1 ? 0 : ring <= 3 ? 1 : 2));
        boolean vein = ring >= 6 && lx % 6 == 0 && lz % 6 <= 3;
        int columnTop = vein ? Math.min(f.maxY(), top + 2) : top;
        if (y == columnTop || (vein && y > top && y < columnTop)) {
            return Piece.RESOURCE;
        }
        return y < columnTop ? Piece.BASE : Piece.AIR;
    }

    /** A forest field with the default template (per-tier templates: {@link TreeTemplate#at}). */
    static Piece forestField(Box f, int x, int y, int z) {
        return TreeTemplate.DEFAULT.at(f, x, y, z);
    }

    /** A 9-wide walled path: floor, 2-high walls with lights, a narrow underside. */
    static Shape causeway(Box c, int stand) {
        boolean alongX = c.sizeX() >= c.sizeZ();
        return (x, y, z) -> {
            int across = alongX ? z : x;
            int lo = alongX ? c.minZ() : c.minX();
            int hi = alongX ? c.maxZ() : c.maxX();
            boolean edge = across == lo || across == hi;
            if (y == stand - 1) {
                return Piece.FLOOR;
            }
            if (y < stand - 1) {
                int inset = (stand - 2 - y) * 2;
                return across >= lo + inset && across <= hi - inset ? Piece.BASE : Piece.AIR;
            }
            if (edge) {
                if (y < stand + WALL_HEIGHT) {
                    return Piece.WALL;
                }
                if (y == stand + WALL_HEIGHT && Math.floorMod(alongX ? x : z, LIGHT_SPACING) == 0) {
                    return Piece.LIGHT;
                }
            }
            return Piece.AIR;
        };
    }

    // ------------------------------------------------------------------ The Gate

    /** Brazier legs (2×2 each) and bowl around the hub center; the center itself stays free (world spawn). */
    static final int BRAZIER_LEG_TOP = 8;

    static Shape hubShape(Hub hub) {
        Box r = hub.region();
        int stand = hub.spawn().blockY();
        int sx = hub.spawn().blockX();
        int sz = hub.spawn().blockZ();
        double cx = (r.minX() + r.maxX() + 1) / 2.0;
        double cz = (r.minZ() + r.maxZ() + 1) / 2.0;
        double radius = Math.min(r.sizeX(), r.sizeZ()) / 2.0 - 2;
        List<Map.Entry<Aspect, Box>> gates = new ArrayList<>(hub.wingGates().entrySet());
        return (x, y, z) -> {
            double px = x + 0.5 - cx;
            double pz = z + 0.5 - cz;
            double d = Math.hypot(px, pz);
            boolean apron = false;
            for (Map.Entry<Aspect, Box> e : gates) {
                Box g = e.getValue();
                if (g.contains(x, y, z)) {
                    return Piece.AIR;
                }
                if (frame(g, 2, 2, x, y, z)) {
                    return Piece.gate(e.getKey());
                }
                apron |= apron(r, g, x, z);
            }
            if (y == stand - 1) {
                if (Math.abs(x - sx) <= 1 && Math.abs(z - sz) <= 1) {
                    return Piece.PAD;
                }
                if (d >= 8 && d < 9.2) {
                    return Piece.INLAY;
                }
                return d <= radius || apron ? Piece.FLOOR : Piece.AIR;
            }
            if (y < stand - 1) {
                int depth = stand - 1 - y;
                return d <= radius - (depth - 1) * 5.5 ? Piece.BASE : Piece.AIR;
            }
            if (d > radius - 1.5 && d <= radius && !apron) {
                if (y < stand + WALL_HEIGHT) {
                    return Piece.WALL;
                }
                if (y == stand + WALL_HEIGHT && Math.floorMod(x + z, 6) == 0) {
                    return Piece.LIGHT;
                }
            }
            double ax = Math.abs(px);
            double az = Math.abs(pz);
            boolean leg = ax >= 2.5 && ax <= 4.5 && az >= 2.5 && az <= 4.5;
            if (leg && y >= stand && y <= stand + BRAZIER_LEG_TOP) {
                return Piece.TRIM;
            }
            if (y == stand + BRAZIER_LEG_TOP + 1 && ax <= 5 && az <= 5) {
                return Piece.TRIM;
            }
            if (y == stand + BRAZIER_LEG_TOP + 2 && ax <= 2 && az <= 2) {
                return Piece.FIRE;
            }
            if (y == stand + 3 && d <= radius - 2 && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                return Piece.LIGHT_BLOCK;
            }
            return Piece.AIR;
        };
    }

    /** Columns between a wing gate and the hub edge, as wide as the gate (the causeway's start). */
    static boolean apron(Box hub, Box gate, int x, int z) {
        double gx = (gate.minX() + gate.maxX() + 1) / 2.0 - (hub.minX() + hub.maxX() + 1) / 2.0;
        double gz = (gate.minZ() + gate.maxZ() + 1) / 2.0 - (hub.minZ() + hub.maxZ() + 1) / 2.0;
        if (Math.abs(gx) >= Math.abs(gz)) {
            boolean inZ = z >= gate.minZ() && z <= gate.maxZ();
            return inZ && (gx < 0 ? x >= hub.minX() && x <= gate.maxX() : x >= gate.minX() && x <= hub.maxX());
        }
        boolean inX = x >= gate.minX() && x <= gate.maxX();
        return inX && (gz < 0 ? z >= hub.minZ() && z <= gate.maxZ() : z >= gate.minZ() && z <= hub.maxZ());
    }

    // ------------------------------------------------------------------ world boss arena

    static Shape arenaShape(BossArena arena) {
        Box r = arena.region();
        int stand = arena.spawn().blockY();
        double cx = (r.minX() + r.maxX() + 1) / 2.0;
        double cz = (r.minZ() + r.maxZ() + 1) / 2.0;
        double radius = Math.min(r.sizeX(), r.sizeZ()) / 2.0 - 6;
        List<int[]> braziers = new ArrayList<>();
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30);
            int bx = (int) Math.floor(cx + (radius - 4) * Math.cos(a));
            int bz = (int) Math.floor(cz + (radius - 4) * Math.sin(a));
            if (arena.returnPortal() == null || !arena.returnPortal().growHorizontal(4).containsColumn(bx, bz)) {
                braziers.add(new int[]{bx, bz}); // none right in front of the return portal
            }
        }
        Box ret = arena.returnPortal();
        return (x, y, z) -> {
            if (ret != null) {
                if (ret.contains(x, y, z)) {
                    return Piece.AIR;
                }
                if (frame(ret, 1, 1, x, y, z)) {
                    return Piece.TRIM;
                }
            }
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            if (y == stand - 1) {
                if (Math.abs(x + 0.5 - cx) <= 2.5 && Math.abs(z + 0.5 - cz) <= 2.5) {
                    return Piece.PAD;
                }
                if (d <= radius) {
                    return d > radius - 2 ? Piece.INLAY : Piece.FLOOR;
                }
                return d <= radius + 2 ? Piece.FLOOR : Piece.AIR;
            }
            if (y < stand - 1) {
                return d <= radius + 2 - (stand - 2 - y) * 6 ? Piece.BASE : Piece.AIR;
            }
            if (d > radius && d <= radius + 2) {
                if (y < stand + WALL_HEIGHT) {
                    return Piece.WALL;
                }
                if (y == stand + WALL_HEIGHT && Math.floorMod(x + z, LIGHT_SPACING) == 0) {
                    return Piece.LIGHT;
                }
            }
            for (int[] b : braziers) {
                if (b[0] == x && b[1] == z) {
                    if (y == stand) {
                        return Piece.TRIM;
                    }
                    if (y == stand + 1) {
                        return Piece.FIRE;
                    }
                }
            }
            if (y == stand + 4 && d <= radius && Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0) {
                return Piece.LIGHT_BLOCK;
            }
            return Piece.AIR;
        };
    }
}
