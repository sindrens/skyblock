package net.skybornmc.skyblock.depths.zones;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How the placeholder build plants the trees of one forest tier (M4.7), from {@code depths.yml → trees.<tier>}. Pure:
 * "field position → {@link Piece}", unit-tested without a server.
 *
 * <p>Trees stand on a grid ({@code spacing}): one trunk per cell, the field's middle cell stays free (a clearing). The
 * crown sits <b>on top of</b> the trunk by default ({@code crownOverlap = 0}), so every log has an open side and can be
 * chopped: leaves are decoration and can't be broken. Logs never go higher than {@code max-trunk-height} above the
 * ground (reach, BUILDER_BRIEF §10.2); the crown is cut off at the top of the field box.</p>
 *
 * @param shape        crown shape
 * @param trunkMin     lowest trunk (logs)
 * @param trunkMax     highest trunk; trees alternate between min and max
 * @param trunkWidth   1 (thin) or 2 (2×2 trunk: jungle, dark oak, pale oak)
 * @param crownRadius  widest crown layer: blocks from the trunk
 * @param crownHeight  crown layers
 * @param crownOverlap how many of the top logs the crown wraps (0 = none: every log reachable)
 * @param spacing      grid cell size (one tree per cell)
 */
public record TreeTemplate(Shape shape, int trunkMin, int trunkMax, int trunkWidth, int crownRadius, int crownHeight,
                           int crownOverlap, int spacing) {

    /** Crown shapes. */
    public enum Shape {
        /** Rounded: full layers, the top one a size smaller (oak, birch, dark oak). */
        ROUND,
        /** Narrowing to a tip (spruce). */
        CONE,
        /** One wide flat layer and a smaller one on it (acacia, cherry). */
        FLAT,
        /** A hat with a hanging skirt (crimson / warped fungi). */
        CAP;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Shape> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Shape s : values()) {
                if (s.id().equalsIgnoreCase(id.trim())) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    /** Oak-like default: 4–5 logs, round crown of 3 layers right above the trunk, one tree every 8 blocks. */
    public static final TreeTemplate DEFAULT = new TreeTemplate(Shape.ROUND, 4, 5, 1, 2, 3, 0, 8);

    public TreeTemplate {
        if (shape == null) {
            throw new IllegalArgumentException("shape is required");
        }
    }

    /**
     * Problems that make this template unusable (empty = fine).
     *
     * @param maxTrunk highest trunk allowed (config {@code forest.max-trunk-height})
     */
    public List<String> problems(int maxTrunk) {
        List<String> out = new ArrayList<>();
        if (spacing < 4 || spacing > 32) {
            out.add("spacing must be 4-32");
        }
        if (trunkWidth < 1 || trunkWidth > 2) {
            out.add("trunk-width must be 1 or 2");
        }
        if (trunkMin < 1 || trunkMax < trunkMin) {
            out.add("trunk-min must be at least 1 and trunk-max at least trunk-min");
        }
        if (trunkMax > maxTrunk) {
            out.add("trunk-max " + trunkMax + " is above forest.max-trunk-height " + maxTrunk + " (logs out of reach)");
        }
        if (crownRadius < 0 || crownHeight < 0 || crownOverlap < 0) {
            out.add("crown-radius, crown-height and crown-overlap can't be negative");
        }
        if (crownOverlap > trunkMin - 1 && crownOverlap > 0) {
            out.add("crown-overlap must stay below trunk-min");
        }
        int anchor = spacing / 2;
        if (anchor - crownRadius < 0 || anchor + trunkWidth - 1 + crownRadius > spacing - 1) {
            out.add("crown-radius " + crownRadius + " doesn't fit in a cell of " + spacing + " (max "
                    + Math.max(0, Math.min(anchor, spacing - anchor - trunkWidth)) + ")");
        }
        return out;
    }

    /** True if logs inside the crown can't be reached (warned at load, allowed for real-build look-alikes). */
    public boolean hidesLogs() {
        return crownOverlap > 0;
    }

    /** Trunk anchor (min corner) of the cell that holds field-local column {@code l}. */
    int anchorOf(int l) {
        return l / spacing * spacing + spacing / 2;
    }

    /** True if a tree stands with its trunk anchor at field-local column (lx, lz). */
    public boolean treeAt(Box f, int lx, int lz) {
        int half = spacing / 2;
        if (lx % spacing != half || lz % spacing != half) {
            return false;
        }
        if (lx + trunkWidth - 1 > f.sizeX() - half || lz + trunkWidth - 1 > f.sizeZ() - half) {
            return false;
        }
        int midX = (f.sizeX() / 2) / spacing * spacing + half;
        int midZ = (f.sizeZ() / 2) / spacing * spacing + half;
        return !(lx == midX && lz == midZ);
    }

    /** Trunk height of the tree anchored at (lx, lz): alternates between min and max over the grid. */
    public int trunkHeight(int lx, int lz) {
        int range = Math.max(0, trunkMax - trunkMin) + 1;
        return trunkMin + Math.floorMod(lx / spacing + lz / spacing, range);
    }

    /** What goes at a world position inside the field box {@code f}: a log, leaves, an invisible light or air. */
    public Piece at(Box f, int x, int y, int z) {
        int lx = x - f.minX();
        int lz = z - f.minZ();
        int rel = y - f.minY();
        int tx = anchorOf(lx);
        int tz = anchorOf(lz);
        if (treeAt(f, tx, tz)) {
            int h = trunkHeight(tx, tz);
            int dx = offset(lx, tx);
            int dz = offset(lz, tz);
            if (dx == 0 && dz == 0 && rel < h) {
                return Piece.RESOURCE;
            }
            int layer = rel - (h - crownOverlap);
            if (layer >= 0 && layer < crownHeight && leaf(layer, Math.abs(dx), Math.abs(dz))) {
                return Piece.LEAVES;
            }
        }
        boolean anchorColumn = lx % spacing >= spacing / 2 && lx % spacing < spacing / 2 + trunkWidth
                && lz % spacing >= spacing / 2 && lz % spacing < spacing / 2 + trunkWidth;
        if (rel == 1 && lx % 4 == 0 && lz % 4 == 0 && !anchorColumn) {
            return Piece.LIGHT_BLOCK;
        }
        return Piece.AIR;
    }

    /** Distance from the trunk footprint along one axis (0 inside the trunk). */
    private int offset(int l, int anchor) {
        if (l < anchor) {
            return l - anchor;
        }
        int last = anchor + trunkWidth - 1;
        return l > last ? l - last : 0;
    }

    /** Leaves at crown layer {@code layer} (0 = bottom), {@code ax}/{@code az} blocks from the trunk. */
    boolean leaf(int layer, int ax, int az) {
        int top = crownHeight - 1;
        return switch (shape) {
            case ROUND -> square(layer == top && top > 0 ? crownRadius - 1 : crownRadius, ax, az, 2);
            case CONE -> square(top == 0 ? crownRadius : crownRadius - (layer * crownRadius) / top, ax, az, 1);
            case FLAT -> square(layer == 0 ? crownRadius : crownRadius - 1, ax, az, 2);
            case CAP -> layer == 0 && top > 0
                    ? (ax == crownRadius || az == crownRadius) && !(ax == crownRadius && az == crownRadius) && ax <= crownRadius && az <= crownRadius
                    : square(layer >= 2 ? crownRadius - 1 : crownRadius, ax, az, 2);
        };
    }

    /** Inside a square of {@code radius}, without its corners when the radius is at least {@code cutFrom}. */
    private static boolean square(int radius, int ax, int az, int cutFrom) {
        if (radius < 0 || ax > radius || az > radius) {
            return false;
        }
        return !(radius >= cutFrom && radius > 0 && ax == radius && az == radius);
    }
}
