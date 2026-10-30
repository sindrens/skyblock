package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanity checks of a {@link ZoneSet}: everything inside the world border, no two structures (zones, causeways, The
 * Gate, the boss arena) sharing a column, fields/gates/spawns where they belong, tiers numbered 1..n per wing.
 * Problems are warnings: the zones still load.
 */
public final class ZoneValidator {

    /** How far inside the zone edge a gate or exit may sit (builders' door planes are inset). */
    static final int EDGE_INSET = 3;

    private ZoneValidator() {
    }

    /** A named footprint for the overlap check. */
    private record Part(String name, Box box) {
    }

    /**
     * @param borderRadius half the world border size (border centered at 0,0), or {@code <= 0} to skip the border check
     */
    public static List<String> validate(ZoneSet set, double borderRadius) {
        List<String> problems = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        Hub hub = set.hub();
        if (hub != null) {
            parts.add(new Part("hub", hub.region()));
            inside(problems, "hub.spawn", hub.spawn(), hub.region());
            hub.wingGates().forEach((aspect, gate) -> {
                if (!hub.region().contains(gate)) {
                    problems.add("hub.wing-gates." + aspect.id() + " is not inside the hub region");
                }
            });
        }
        BossArena arena = set.arena();
        if (arena != null) {
            parts.add(new Part("boss-arena", arena.region()));
            inside(problems, "boss-arena.spawn", arena.spawn(), arena.region());
            if (arena.returnPortal() != null && !arena.region().contains(arena.returnPortal())) {
                problems.add("boss-arena.return-portal is not inside the boss arena region");
            }
            if (arena.returnPortal() != null && arena.returnPortal().contains(arena.spawn().x(), arena.spawn().y(), arena.spawn().z())) {
                problems.add("boss-arena.spawn is inside the return portal (players would bounce back)");
            }
            if (arena.portal() != null) {
                Zone holder = arena.portalZone() == null ? null : set.zone(arena.portalZone()).orElse(null);
                if (holder == null) {
                    problems.add("boss-arena.portal-zone '" + arena.portalZone() + "' is not a zone");
                } else {
                    if (!holder.region().contains(arena.portal())) {
                        problems.add("boss-arena.portal is not inside zone " + holder.id());
                    }
                    if (arena.portal().contains(holder.spawn().x(), holder.spawn().y(), holder.spawn().z())) {
                        problems.add("zone " + holder.id() + " spawn is inside the boss portal");
                    }
                }
            }
        }
        for (Zone z : set.zones()) {
            String n = "zones." + z.id();
            parts.add(new Part(n, z.region()));
            if (z.causeway() != null) {
                parts.add(new Part(n + ".causeway", z.causeway()));
            }
            inside(problems, n + ".spawn", z.spawn(), z.region());
            if (!z.gate().nearEdgeOf(z.region(), EDGE_INSET)) {
                problems.add(n + ".gate is not on the zone's edge");
            }
            if (z.exit() != null && !z.exit().nearEdgeOf(z.region(), EDGE_INSET)) {
                problems.add(n + ".exit is not on the zone's edge");
            }
            if (z.arena() != null && !z.region().contains(z.arena())) {
                problems.add(n + ".arena is not inside the zone");
            }
            for (Field f : z.fields()) {
                if (!z.region().contains(f.box())) {
                    problems.add(n + ".fields." + f.id() + " is not inside the zone");
                }
                if (f.box().containsColumn(z.spawn().blockX(), z.spawn().blockZ())) {
                    problems.add(n + ".spawn is inside field " + f.id());
                }
            }
        }
        if (borderRadius > 0) {
            for (Part part : parts) {
                Box b = part.box();
                if (b.minX() < -borderRadius || b.maxX() + 1 > borderRadius || b.minZ() < -borderRadius || b.maxZ() + 1 > borderRadius) {
                    problems.add(part.name() + " " + b + " reaches outside the world border (radius " + (long) borderRadius + ")");
                }
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                if (parts.get(i).box().intersectsColumns(parts.get(j).box())) {
                    problems.add(parts.get(i).name() + " overlaps " + parts.get(j).name());
                }
            }
        }
        for (Aspect aspect : Aspect.values()) {
            List<Zone> wing = set.zones(aspect);
            for (int i = 0; i < wing.size(); i++) {
                if (wing.get(i).tier() != i + 1) {
                    problems.add(aspect.id() + " wing: tiers must be numbered 1.." + wing.size() + " without gaps (found tier "
                            + wing.get(i).tier() + " at position " + (i + 1) + ")");
                    break;
                }
            }
        }
        return problems;
    }

    private static void inside(List<String> problems, String name, Point p, Box region) {
        if (!region.contains(p.x(), p.y(), p.z())) {
            problems.add(name + " is not inside its region");
        }
    }
}
