package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The default Depths layout (owner decision: wings bend into a U inside the 2000 border). Pure math, used to generate
 * the shipped {@code zones.yml}; at runtime only that file counts.
 *
 * <pre>
 *                       boss arena (own island, far north apex)
 *                 C5 ── C6
 *                 C4    C7          C = Combat: out north (C1–C5), back on the east row (C6–C10)
 *                 C3    C8
 *                 C2    C9
 *                 C1    C10
 *  M6 … M2 M1 ── THE GATE ── F1 F2 … F6      M = Mine: out west (M1–M6), back on the south row (M7–M12)
 *  M7 … M11 M12             F11 … F7       F = Forest: out east (F1–F6), back on the south row (F7–F11)
 * </pre>
 *
 * <p>All tier zones are chunk aligned. Each zone's {@code gate} sits on the wall facing the previous tier (The Gate
 * for tier 1), its {@code exit} on the wall facing the next; a 9-wide {@code causeway} (7 walkable + walls) leads
 * into every zone.</p>
 */
public final class ZoneLayout {

    /**
     * Layout parameters (blocks).
     *
     * @param floorY       standing level of every floor (the floor block is one below)
     * @param hubHalf      The Gate spans {@code -hubHalf .. hubHalf-1} on x and z
     * @param zoneSize     tier zone width (square)
     * @param gap          causeway length between two zones of a row (and between The Gate and tier 1)
     * @param rowGap       distance between the outward and the return row
     * @param rowStart     perpendicular start of the outward row (its zones span rowStart .. rowStart+zoneSize-1)
     * @param centerline   perpendicular coordinate of the outward row's causeways and gates
     * @param fieldSize    ore/log field width (square, centered in the zone)
     * @param arenaSize    combat fighting area width (square, centered)
     * @param bossSize     world boss arena island width (square)
     * @param bossGap      empty distance between the farthest combat zone and the boss arena
     * @param mineOut      mine tiers on the outward row (the rest come back)
     * @param forestOut    forest tiers on the outward row
     * @param combatOut    combat tiers on the outward row
     */
    public record Params(int floorY, int hubHalf, int zoneSize, int gap, int rowGap, int rowStart, int centerline,
                         int fieldSize, int arenaSize, int bossSize, int bossGap, int mineOut, int forestOut, int combatOut) {

        public static Params defaults() {
            return new Params(100, 48, 80, 32, 48, -48, 0, 40, 50, 112, 192, 6, 6, 5);
        }

        int outCount(Aspect aspect) {
            return switch (aspect) {
                case MINE -> mineOut;
                case FOREST -> forestOut;
                case COMBAT -> combatOut;
            };
        }
    }

    /** Opening half-width (5 wide), causeway half-width (9 wide incl. walls), opening height. */
    static final int OPENING_HALF = 2;
    static final int CAUSEWAY_HALF = 4;
    static final int OPENING_HEIGHT = 5;
    /** Zone box below/above the standing level (underside and headroom). */
    static final int ZONE_BELOW = 6;
    static final int ZONE_ABOVE = 10;
    /** Arrival pad distance from the gate wall. */
    static final int PAD_INSET = 5;
    /** The Gate: box below/above the standing level, wing gate opening (9 × 12) and its distance from the center. */
    static final int HUB_BELOW = 9;
    static final int HUB_ABOVE = 16;
    static final int WING_GATE_HALF = 4;
    static final int WING_GATE_HEIGHT = 12;
    static final int WING_GATE_U = 43;
    static final int HUB_PAD_Z = 38;

    private final Params p;

    public ZoneLayout(Params params) {
        this.p = params;
    }

    /** Direction a wing leaves The Gate. */
    public static Side out(Aspect aspect) {
        return switch (aspect) {
            case MINE -> Side.WEST;
            case FOREST -> Side.EAST;
            case COMBAT -> Side.NORTH;
        };
    }

    /** Side of the outward row where the return row lies. */
    public static Side returnSide(Aspect aspect) {
        return switch (aspect) {
            case MINE, FOREST -> Side.SOUTH;
            case COMBAT -> Side.EAST;
        };
    }

    /** The full default zone set from {@link TierTable#defaults()}. */
    public ZoneSet build(String world) {
        List<Zone> zones = new ArrayList<>();
        List<TierTable.Tier> tiers = TierTable.defaults();
        for (Aspect aspect : Aspect.values()) {
            List<TierTable.Tier> wing = tiers.stream().filter(t -> t.aspect() == aspect).toList();
            for (int i = 0; i < wing.size(); i++) {
                zones.add(zone(aspect, wing, i));
            }
        }
        return new ZoneSet(world, p.floorY(), hub(), bossArena(tiers), zones);
    }

    // ------------------------------------------------------------------ geometry helpers

    /** World interval of the distances {@code u0..u1} from the center in direction {@code d}. */
    static int[] along(Side d, int u0, int u1) {
        return d.positive() ? new int[]{u0, u1} : new int[]{-u1 - 1, -u0 - 1};
    }

    /** World coordinate of distance {@code u} in direction {@code d}. */
    static int alongCoord(Side d, int u) {
        return d.positive() ? u : -u - 1;
    }

    private static Box box(Side d, int[] alongRange, int[] perpRange, int y0, int y1) {
        return d.alongX()
                ? Box.of(alongRange[0], y0, perpRange[0], alongRange[1], y1, perpRange[1])
                : Box.of(perpRange[0], y0, alongRange[0], perpRange[1], y1, alongRange[1]);
    }

    private int pitch() {
        return p.zoneSize() + p.gap();
    }

    private int rowShift() {
        return p.zoneSize() + p.rowGap();
    }

    private static int sign(Aspect aspect) {
        return returnSide(aspect).positive() ? 1 : -1;
    }

    private record Slot(int row, int along, int near, int perpMin, int perpMax, int centerline) {
    }

    private Slot slot(Aspect aspect, int index) {
        int out = p.outCount(aspect);
        int row = index < out ? 0 : 1;
        int along = row == 0 ? index : out - 1 - (index - out);
        int near = p.hubHalf() + p.gap() + along * pitch();
        int shift = row * sign(aspect) * rowShift();
        int perpMin = p.rowStart() + shift;
        return new Slot(row, along, near, perpMin, perpMin + p.zoneSize() - 1, p.centerline() + shift);
    }

    /** Where a zone is entered: the wall and the coordinate along that wall of the opening's center. */
    private record Entry(Side wall, int center) {
    }

    private Entry entry(Aspect aspect, int index) {
        Side d = out(aspect);
        Slot s = slot(aspect, index);
        int out = p.outCount(aspect);
        if (s.row() == 0) {
            return new Entry(d.opposite(), s.centerline());
        }
        if (index == out) {
            return new Entry(returnSide(aspect).opposite(), alongCoord(d, s.near() + p.zoneSize() / 2));
        }
        return new Entry(d, s.centerline());
    }

    static Box opening(Box zone, Side wall, int center, int y, int half, int height) {
        return switch (wall) {
            case NORTH -> Box.of(center - half, y, zone.minZ(), center + half, y + height - 1, zone.minZ());
            case SOUTH -> Box.of(center - half, y, zone.maxZ(), center + half, y + height - 1, zone.maxZ());
            case WEST -> Box.of(zone.minX(), y, center - half, zone.minX(), y + height - 1, center + half);
            case EAST -> Box.of(zone.maxX(), y, center - half, zone.maxX(), y + height - 1, center + half);
        };
    }

    static Point inside(Box zone, Side wall, int center, int inset, int y) {
        Side facing = wall.opposite();
        return switch (wall) {
            case NORTH -> new Point(center + 0.5, y, zone.minZ() + inset + 0.5, facing.yaw(), 0);
            case SOUTH -> new Point(center + 0.5, y, zone.maxZ() - inset + 0.5, facing.yaw(), 0);
            case WEST -> new Point(zone.minX() + inset + 0.5, y, center + 0.5, facing.yaw(), 0);
            case EAST -> new Point(zone.maxX() - inset + 0.5, y, center + 0.5, facing.yaw(), 0);
        };
    }

    // ------------------------------------------------------------------ zones

    private Zone zone(Aspect aspect, List<TierTable.Tier> wing, int index) {
        TierTable.Tier tier = wing.get(index);
        Side d = out(aspect);
        Slot s = slot(aspect, index);
        int y = p.floorY();
        Box region = box(d, along(d, s.near(), s.near() + p.zoneSize() - 1), new int[]{s.perpMin(), s.perpMax()},
                y - ZONE_BELOW, y + ZONE_ABOVE);
        Entry in = entry(aspect, index);
        Box gate = opening(region, in.wall(), in.center(), y, OPENING_HALF, OPENING_HEIGHT);
        Point spawn = inside(region, in.wall(), in.center(), PAD_INSET, y);
        Box exit = null;
        if (index + 1 < wing.size()) {
            Entry next = entry(aspect, index + 1);
            exit = opening(region, next.wall().opposite(), next.center(), y, OPENING_HALF, OPENING_HEIGHT);
        }
        Box causeway = causeway(aspect, index, s, y);

        List<Field> fields = new ArrayList<>();
        Box arena = null;
        int fieldOff = (p.zoneSize() - p.fieldSize()) / 2;
        int arenaOff = (p.zoneSize() - p.arenaSize()) / 2;
        switch (aspect) {
            case MINE -> fields.add(new Field("ore", Box.of(region.minX() + fieldOff, y - 3, region.minZ() + fieldOff,
                    region.minX() + fieldOff + p.fieldSize() - 1, y - 1, region.minZ() + fieldOff + p.fieldSize() - 1),
                    List.of(new Field.Resource(tier.resource(), tier.depleted(), null))));
            case FOREST -> fields.add(new Field("logs", Box.of(region.minX() + fieldOff, y, region.minZ() + fieldOff,
                    region.minX() + fieldOff + p.fieldSize() - 1, y + 7, region.minZ() + fieldOff + p.fieldSize() - 1),
                    List.of(new Field.Resource(tier.resource(), tier.depleted(), null))));
            case COMBAT -> arena = Box.of(region.minX() + arenaOff, y, region.minZ() + arenaOff,
                    region.minX() + arenaOff + p.arenaSize() - 1, y + 10, region.minZ() + arenaOff + p.arenaSize() - 1);
        }
        String id = aspect.id() + "_t" + (tier.tier() < 10 ? "0" : "") + tier.tier();
        return new Zone(id, aspect, tier.tier(), tier.name(), tier.resourceName(), tier.level(), false, region, spawn,
                gate, exit, causeway, fields, arena, tier.biome(), tier.palette());
    }

    private Box causeway(Aspect aspect, int index, Slot s, int y) {
        Side d = out(aspect);
        int y0 = y - 4;
        int y1 = y + 5;
        int out = p.outCount(aspect);
        int[] perp = {s.centerline() - CAUSEWAY_HALF, s.centerline() + CAUSEWAY_HALF};
        if (s.row() == 0) {
            int from = index == 0 ? p.hubHalf() : s.near() - p.gap();
            return box(d, along(d, from, s.near() - 1), perp, y0, y1);
        }
        if (index == out) {
            Slot prev = slot(aspect, index - 1);
            int[] between = sign(aspect) > 0 ? new int[]{prev.perpMax() + 1, s.perpMin() - 1}
                    : new int[]{s.perpMax() + 1, prev.perpMin() - 1};
            int c = alongCoord(d, s.near() + p.zoneSize() / 2);
            int[] alongRange = {c - CAUSEWAY_HALF, c + CAUSEWAY_HALF};
            return d.alongX() ? Box.of(alongRange[0], y0, between[0], alongRange[1], y1, between[1])
                    : Box.of(between[0], y0, alongRange[0], between[1], y1, alongRange[1]);
        }
        int far = s.near() + p.zoneSize();
        return box(d, along(d, far, far + p.gap() - 1), perp, y0, y1);
    }

    // ------------------------------------------------------------------ The Gate and the boss arena

    private Hub hub() {
        int y = p.floorY();
        int h = p.hubHalf();
        Box region = Box.of(-h, y - HUB_BELOW, -h, h - 1, y + HUB_ABOVE, h - 1);
        Map<Aspect, Box> gates = new EnumMap<>(Aspect.class);
        for (Aspect aspect : Aspect.values()) {
            Side d = out(aspect);
            gates.put(aspect, box(d, along(d, WING_GATE_U, WING_GATE_U + 1),
                    new int[]{p.centerline() - WING_GATE_HALF, p.centerline() + WING_GATE_HALF}, y, y + WING_GATE_HEIGHT - 1));
        }
        Point spawn = new Point(0.5, y, HUB_PAD_Z + 0.5, Side.NORTH.yaw(), 0);
        return new Hub("The Gate", false, region, spawn, gates, TierTable.hubPalette());
    }

    private BossArena bossArena(List<TierTable.Tier> tiers) {
        Aspect aspect = Aspect.COMBAT;
        Side d = out(aspect);
        int y = p.floorY();
        int farthest = p.hubHalf() + p.gap() + (p.combatOut() - 1) * pitch() + p.zoneSize() - 1;
        int u0 = farthest + 1 + p.bossGap();
        Slot row0 = slot(aspect, 0);
        int shift = sign(aspect) * rowShift();
        int lo = Math.min(row0.perpMin(), row0.perpMin() + shift);
        int hi = Math.max(row0.perpMax(), row0.perpMax() + shift);
        int mid = Math.floorDiv(lo + hi + 1, 2);
        int half = p.bossSize() / 2;
        Box region = box(d, along(d, u0, u0 + p.bossSize() - 1), new int[]{mid - half, mid + half - 1}, y - HUB_BELOW, y + 10);
        Point spawn = d.alongX()
                ? new Point(alongCoord(d, u0 + 14) + 0.5, y, mid + 0.5, d.yaw(), 0)
                : new Point(mid + 0.5, y, alongCoord(d, u0 + 14) + 0.5, d.yaw(), 0);
        Box returnPortal = box(d, along(d, u0 + 8, u0 + 8), new int[]{mid - 1, mid + 1}, y, y + 3);

        int lastIndex = (int) tiers.stream().filter(t -> t.aspect() == aspect).count() - 1;
        Slot last = slot(aspect, lastIndex);
        Box portal = box(d, along(d, last.near() + 8, last.near() + 8),
                new int[]{last.centerline() - 1, last.centerline() + 1}, y, y + 3);
        String lastId = aspect.id() + "_t" + (lastIndex + 1 < 10 ? "0" : "") + (lastIndex + 1);
        return new BossArena("World Boss Arena", false, region, spawn, lastId, portal, returnPortal, TierTable.arenaPalette());
    }
}
