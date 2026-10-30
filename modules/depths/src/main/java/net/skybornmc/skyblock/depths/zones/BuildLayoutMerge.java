package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.DepthsLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Merges the builders' layout ({@code builds/depths_layout.yml}) and the wiring data of applied builds into the
 * current {@link ZoneSet}, so the tier zones match the pasted builds. Names, tiers, unlock levels, palettes, biomes and
 * the boss arena are always kept. Zones are matched by wing + tier. Pure (unit-tested).
 *
 * <ul>
 *   <li><b>Every zone</b>: causeway recomputed from the layout (9 wide incl. walls, centred on the zone side, from the
 *       previous zone or The Gate to this zone's entrance side).</li>
 *   <li><b>Zones applied now</b>: region = the layout cuboid, spawn = arrival pad, gate/exit/arena/fields from the build
 *       metadata (missing ones keep their value), {@code built: true}.</li>
 *   <li><b>Zones built earlier</b> ({@code built: true}): nothing else changes.</li>
 *   <li><b>Placeholder zones</b>: x/z of the region from the layout (height kept), gate and exit re-centred on the
 *       layout's sides (5×5), spawn moved with the gate.</li>
 *   <li><b>The Gate</b>: region x/z from the layout (whole cuboid once its build is applied), wing gates re-centred on the
 *       tier-1 zones (or the build's causeway starts), spawn = the build's {@code player_spawn}.</li>
 * </ul>
 */
public final class BuildLayoutMerge {

    /** Opening half-width (5 wide) and height of placeholder gates/exits, causeway half-width (9 wide incl. walls). */
    static final int OPENING_HALF = 2;
    static final int OPENING_HEIGHT = 5;
    static final int CAUSEWAY_HALF = 4;

    /** Merged set and one line per change. */
    public record Result(ZoneSet set, List<String> changes, List<String> unmatched) {
    }

    private BuildLayoutMerge() {
    }

    public static Result merge(ZoneSet current, DepthsLayout layout) {
        List<String> changes = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        Map<String, DepthsLayout.LayoutZone> planned = new HashMap<>();
        for (DepthsLayout.LayoutZone lz : layout.zones()) {
            planned.put(key(lz.wing(), lz.tier()), lz);
        }
        Map<String, DepthsLayout.BuiltZone> applying = new HashMap<>();
        for (DepthsLayout.BuiltZone b : layout.built()) {
            applying.put(key(b.wing(), b.tier()), b);
        }
        int floorY = layout.floorY() > 0 ? layout.floorY() : current.floorY();
        Box gateBox = layout.gate() == null ? (current.hub() == null ? null : current.hub().region()) : box(layout.gate());

        List<Zone> zones = new ArrayList<>();
        Map<String, int[]> moved = new HashMap<>();
        for (Zone z : current.zones()) {
            String k = key(z.aspect().id(), z.tier());
            DepthsLayout.LayoutZone lz = planned.get(k);
            if (lz == null) {
                zones.add(z);
                continue;
            }
            Side entrance = side(lz.entrance());
            Side exitSide = side(lz.exit());
            Box layoutRegion = box(lz.region());
            Box neighbour = z.tier() == 1 ? gateBox
                    : Optional.ofNullable(planned.get(key(z.aspect().id(), z.tier() - 1))).map(p -> box(p.region())).orElse(null);
            Box causeway = neighbour == null || entrance == null ? z.causeway()
                    : causeway(layoutRegion, entrance, neighbour, lz.centerX(), lz.centerZ(), floorY);
            DepthsLayout.BuiltZone built = applying.get(k);
            Zone merged;
            if (built != null) {
                merged = wire(z, layoutRegion, causeway, built);
                changes.add(z.id() + ": wired to build " + built.build() + " (built: true, region " + layoutRegion + ", spawn "
                        + ZoneYaml.point(merged.spawn()) + ")");
            } else if (z.built()) {
                merged = with(z, z.region(), z.spawn(), z.gate(), z.exit(), causeway, z.fields(), z.arena(), true);
                if (!same(z.causeway(), causeway)) {
                    changes.add(z.id() + ": causeway " + causeway);
                }
            } else {
                Box region = new Box(layoutRegion.minX(), z.region().minY(), layoutRegion.minZ(), layoutRegion.maxX(),
                        z.region().maxY(), layoutRegion.maxZ());
                // a placeholder zone the layout moved: everything inside moves with it
                int dx = Math.floorDiv(region.minX() + region.maxX(), 2) - Math.floorDiv(z.region().minX() + z.region().maxX(), 2);
                int dz = Math.floorDiv(region.minZ() + region.maxZ(), 2) - Math.floorDiv(z.region().minZ() + z.region().maxZ(), 2);
                if (dx != 0 || dz != 0) {
                    moved.put(z.id(), new int[]{dx, dz});
                }
                List<Field> fields = new ArrayList<>();
                for (Field f : z.fields()) {
                    fields.add(new Field(f.id(), move(f.box(), dx, dz), f.resources()));
                }
                Box arena = move(z.arena(), dx, dz);
                Box oldGate = move(z.gate(), dx, dz);
                Point spawn = new Point(z.spawn().x() + dx, z.spawn().y(), z.spawn().z() + dz, z.spawn().yaw(), z.spawn().pitch());
                Box gate = entrance == null ? oldGate : opening(region, entrance, lz.centerX(), lz.centerZ(), floorY);
                Box exit = exitSide == null ? move(z.exit(), dx, dz) : opening(region, exitSide, lz.centerX(), lz.centerZ(), floorY);
                Point shifted = shift(spawn, oldGate, gate, entrance);
                if (region.contains(shifted.x(), shifted.y(), shifted.z())) {
                    spawn = shifted;
                }
                merged = with(z, region, spawn, gate, exit, causeway, fields, arena, false);
                if (!same(z.region(), region) || !same(z.gate(), gate) || !same(z.exit(), exit) || !same(z.causeway(), causeway)) {
                    changes.add(z.id() + ": placeholder re-centred on the layout (gate " + gate + ", causeway " + causeway + ")");
                }
            }
            zones.add(merged);
        }
        for (DepthsLayout.BuiltZone b : layout.built()) {
            boolean found = current.zones().stream().anyMatch(z -> z.aspect().id().equals(b.wing()) && z.tier() == b.tier());
            if (!found) {
                unmatched.add(b.build() + " (" + b.wing() + " tier " + b.tier() + " is not in zones.yml)");
            }
        }

        Hub hub = current.hub();
        if (hub != null && gateBox != null && layout.gate() != null) {
            hub = mergeHub(hub, gateBox, layout, current, changes);
        }
        BossArena arena = current.arena();
        if (arena != null && arena.portal() != null && arena.portalZone() != null && moved.containsKey(arena.portalZone())) {
            int[] d = moved.get(arena.portalZone());
            arena = new BossArena(arena.name(), arena.built(), arena.region(), arena.spawn(), arena.portalZone(),
                    move(arena.portal(), d[0], d[1]), arena.returnPortal(), arena.palette());
            changes.add("boss-arena: portal moved with " + arena.portalZone() + " to " + arena.portal());
        }
        return new Result(new ZoneSet(current.world(), current.floorY(), hub, arena, zones), changes, unmatched);
    }

    private static Hub mergeHub(Hub hub, Box gateBox, DepthsLayout layout, ZoneSet current, List<String> changes) {
        DepthsLayout.BuiltHub built = layout.hub();
        boolean nowBuilt = built != null;
        Box region = nowBuilt ? gateBox
                : new Box(gateBox.minX(), hub.region().minY(), gateBox.minZ(), gateBox.maxX(), hub.region().maxY(), gateBox.maxZ());
        Point spawn = hub.spawn();
        if (nowBuilt && built.spawn() != null) {
            DepthsLayout.Pos p = built.spawn();
            spawn = new Point(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        }
        if (!region.contains(spawn.x(), spawn.y(), spawn.z())) {
            spawn = hub.spawn();
        }
        Map<Aspect, Box> gates = new EnumMap<>(Aspect.class);
        for (Map.Entry<Aspect, Box> e : hub.wingGates().entrySet()) {
            Aspect aspect = e.getKey();
            Box old = e.getValue();
            BuildBox stub = nowBuilt ? built.wingGates().get(aspect.id()) : null;
            Box gate = old;
            if (stub != null) {
                gate = box(stub);
            } else {
                DepthsLayout.LayoutZone first = layout.zones().stream()
                        .filter(z -> z.wing().equalsIgnoreCase(aspect.id()) && z.tier() == 1).findFirst().orElse(null);
                if (first != null) {
                    boolean alongX = old.sizeX() <= old.sizeZ(); // gate plane spans z when it sits on a west/east edge
                    int center = alongX ? first.centerZ() : first.centerX();
                    int oldCenter = alongX ? Math.floorDiv(old.minZ() + old.maxZ(), 2) : Math.floorDiv(old.minX() + old.maxX(), 2);
                    int d = center - oldCenter;
                    gate = alongX ? new Box(old.minX(), old.minY(), old.minZ() + d, old.maxX(), old.maxY(), old.maxZ() + d)
                            : new Box(old.minX() + d, old.minY(), old.minZ(), old.maxX() + d, old.maxY(), old.maxZ());
                }
            }
            if (!region.contains(gate)) {
                gate = old;
            }
            gates.put(aspect, gate);
        }
        Hub merged = new Hub(hub.name(), hub.built() || nowBuilt, region, spawn, gates, hub.palette());
        if (nowBuilt) {
            changes.add("gate: wired to build " + built.build() + " (built: true, region " + region + ", spawn " + ZoneYaml.point(spawn) + ")");
        } else if (!same(hub.region(), region) || !hub.wingGates().equals(gates)) {
            changes.add("gate: region " + region + ", wing gates re-centred on the tier-1 zones");
        }
        return merged;
    }

    /** A zone applied now: layout region, everything else from the build (missing parts keep their value). */
    private static Zone wire(Zone z, Box region, Box causeway, DepthsLayout.BuiltZone b) {
        Point spawn = z.spawn();
        if (b.arrival() != null) {
            DepthsLayout.Pos p = b.arrival();
            spawn = new Point(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        }
        Box gate = b.gate() == null ? z.gate() : box(b.gate());
        Box exit = b.exit() == null ? z.exit() : box(b.exit());
        Box arena = b.arena() == null ? z.arena() : box(b.arena());
        List<Field> fields = z.fields();
        if (!b.fields().isEmpty()) {
            fields = new ArrayList<>();
            for (int i = 0; i < b.fields().size(); i++) {
                DepthsLayout.BuiltField f = b.fields().get(i);
                // keep the existing field id (engine region <zone>_<id>) so other configs keep matching
                String id = i < z.fields().size() ? z.fields().get(i).id() : fieldId(f.id());
                Field.Resource old = i < z.fields().size() && !z.fields().get(i).resources().isEmpty()
                        ? z.fields().get(i).resources().getFirst() : null;
                String depleted = f.depleted() != null ? f.depleted() : old == null ? null : old.depleted();
                Integer respawn = f.respawnSeconds() != null ? f.respawnSeconds() : old == null ? null : old.respawnSeconds();
                fields.add(new Field(id, box(f.box()), List.of(new Field.Resource(f.block(), depleted, respawn))));
            }
        }
        return with(z, region, spawn, gate, exit, causeway, fields, arena, true);
    }

    private static Box move(Box b, int dx, int dz) {
        return b == null ? null : new Box(b.minX() + dx, b.minY(), b.minZ() + dz, b.maxX() + dx, b.maxY(), b.maxZ() + dz);
    }

    private static String fieldId(String regionId) {
        String id = regionId.toLowerCase(Locale.ROOT);
        return id.endsWith("_field") ? id.substring(0, id.length() - "_field".length()) : id;
    }

    private static Zone with(Zone z, Box region, Point spawn, Box gate, Box exit, Box causeway, List<Field> fields, Box arena, boolean built) {
        return new Zone(z.id(), z.aspect(), z.tier(), z.name(), z.resourceName(), z.requiredLevel(), built, region, spawn, gate, exit,
                causeway, fields, arena, z.biome(), z.palette());
    }

    /**
     * The causeway between {@code neighbour} (The Gate or the previous zone) and the zone's {@code entrance} side, 9 wide
     * centred on the zone's centre line, floor at {@code floorY - 1}; {@code null} if they touch.
     */
    static Box causeway(Box zone, Side entrance, Box neighbour, int centerX, int centerZ, int floorY) {
        int y0 = floorY - 4;
        int y1 = floorY + 5;
        int from;
        int to;
        switch (entrance) {
            case EAST -> {
                from = zone.maxX() + 1;
                to = neighbour.minX() - 1;
            }
            case WEST -> {
                from = neighbour.maxX() + 1;
                to = zone.minX() - 1;
            }
            case SOUTH -> {
                from = zone.maxZ() + 1;
                to = neighbour.minZ() - 1;
            }
            default -> {
                from = neighbour.maxZ() + 1;
                to = zone.minZ() - 1;
            }
        }
        if (to < from) {
            return null;
        }
        return entrance.alongX()
                ? new Box(from, y0, centerZ - CAUSEWAY_HALF, to, y1, centerZ + CAUSEWAY_HALF)
                : new Box(centerX - CAUSEWAY_HALF, y0, from, centerX + CAUSEWAY_HALF, y1, to);
    }

    /** A 5×5 opening in the plane of the zone's {@code side}, centred on the zone's centre line. */
    static Box opening(Box zone, Side side, int centerX, int centerZ, int floorY) {
        int top = floorY + OPENING_HEIGHT - 1;
        return switch (side) {
            case EAST -> new Box(zone.maxX(), floorY, centerZ - OPENING_HALF, zone.maxX(), top, centerZ + OPENING_HALF);
            case WEST -> new Box(zone.minX(), floorY, centerZ - OPENING_HALF, zone.minX(), top, centerZ + OPENING_HALF);
            case SOUTH -> new Box(centerX - OPENING_HALF, floorY, zone.maxZ(), centerX + OPENING_HALF, top, zone.maxZ());
            case NORTH -> new Box(centerX - OPENING_HALF, floorY, zone.minZ(), centerX + OPENING_HALF, top, zone.minZ());
        };
    }

    /** Moves the spawn along the entrance wall by as much as the gate moved, so it stays in front of it. */
    private static Point shift(Point spawn, Box oldGate, Box newGate, Side entrance) {
        if (entrance == null || oldGate == null || newGate == null) {
            return spawn;
        }
        if (entrance.alongX()) {
            double d = (newGate.minZ() + newGate.maxZ()) / 2.0 - (oldGate.minZ() + oldGate.maxZ()) / 2.0;
            return new Point(spawn.x(), spawn.y(), spawn.z() + d, spawn.yaw(), spawn.pitch());
        }
        double d = (newGate.minX() + newGate.maxX()) / 2.0 - (oldGate.minX() + oldGate.maxX()) / 2.0;
        return new Point(spawn.x() + d, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    static Side side(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "north" -> Side.NORTH;
            case "south" -> Side.SOUTH;
            case "east" -> Side.EAST;
            case "west" -> Side.WEST;
            default -> null;
        };
    }

    static Box box(BuildBox b) {
        return new Box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private static boolean same(Box a, Box b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String key(String wing, int tier) {
        return wing.toLowerCase(Locale.ROOT) + ":" + tier;
    }
}
