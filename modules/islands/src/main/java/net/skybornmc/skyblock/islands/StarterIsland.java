package net.skybornmc.skyblock.islands;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The built-in placeholder starter island "Ember Meadow" (BUILDER_BRIEF §6.3), used when no schematic file exists or
 * FastAsyncWorldEdit is missing. Pure description of blocks relative to the home block (0,0,0 = the grass block players
 * stand on; they stand at 0,1,0 facing north). The real build from the builders replaces it via a schematic.
 *
 * <p>Layout (facing north from home): starter chest 3 ahead with the "Open /menu" sign beside it, an oak tree to the
 * left, a 2×2 pond (infinite water) and 2×4 farmland to the right, a flat 5×3 stone pad for the first cobble generator
 * ahead-right, a few flowers and boulders; underneath, a tapered cone of dirt and stone with one coal and one iron speck.
 * No lava (it comes from the chest), nothing valuable, footprint 15×15.</p>
 */
public final class StarterIsland {

    /** Block kinds (mapped to materials by name when placed). */
    public enum Piece {
        GRASS_BLOCK, DIRT, COARSE_DIRT, STONE, COBBLESTONE, ANDESITE, OAK_LOG, OAK_LEAVES, WATER, FARMLAND, CHEST, OAK_SIGN,
        POPPY, DANDELION, CORNFLOWER, COAL_ORE, IRON_ORE;

        public boolean solid() {
            return switch (this) {
                case WATER, POPPY, DANDELION, CORNFLOWER, OAK_SIGN -> false;
                default -> true;
            };
        }
    }

    /** Relative block position. */
    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    public static final Pos HOME = new Pos(0, 1, 0);
    public static final Pos CHEST = new Pos(0, 1, -3);
    public static final Pos SIGN = new Pos(-1, 1, -3);
    /** Radius² of the top layer (a rough circle 13-15 blocks wide). */
    static final int TOP_RADIUS_SQ = 42;

    private static final Map<Pos, Piece> BLOCKS = Collections.unmodifiableMap(build());

    private StarterIsland() {
    }

    /** Every block of the islet, in placement order (ground first, then decoration, then the tree). */
    public static Map<Pos, Piece> blocks() {
        return BLOCKS;
    }

    private static Map<Pos, Piece> build() {
        Map<Pos, Piece> b = new LinkedHashMap<>();
        // Underside: an inverted cone, radius shrinking by 1 per layer.
        int[] radiusSq = {TOP_RADIUS_SQ, 30, 20, 12, 6, 2, 0};
        for (int layer = 0; layer < radiusSq.length; layer++) {
            int y = -layer;
            for (int x = -7; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    if (x * x + z * z > radiusSq[layer]) {
                        continue;
                    }
                    Piece piece;
                    if (layer == 0) {
                        piece = Piece.GRASS_BLOCK;
                    } else if (layer <= 2) {
                        piece = Piece.DIRT;
                    } else {
                        piece = Piece.STONE;
                    }
                    b.put(new Pos(x, y, z), piece);
                }
            }
        }
        b.put(new Pos(1, -4, 0), Piece.COAL_ORE);
        b.put(new Pos(-1, -5, 0), Piece.IRON_ORE);

        // Home spot: 3×3 coarse dirt, a stone path tile toward the chest.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                b.put(new Pos(x, 0, z), Piece.COARSE_DIRT);
            }
        }
        b.put(new Pos(0, 0, -2), Piece.STONE);

        // Starter chest (filled by code) and the sign beside it.
        b.put(CHEST, Piece.CHEST);
        b.put(SIGN, Piece.OAK_SIGN);

        // Pond: 2×2 water (four sources = infinite), to the right.
        for (int x = 3; x <= 4; x++) {
            for (int z = 0; z <= 1; z++) {
                b.put(new Pos(x, 0, z), Piece.WATER);
            }
        }
        // Farmland 2×4 beside the pond (within 4 blocks of water, so it stays hydrated).
        for (int x = 3; x <= 4; x++) {
            for (int z = 2; z <= 5; z++) {
                b.put(new Pos(x, 0, z), Piece.FARMLAND);
            }
        }
        // Flat 5×3 stone pad for the first cobble generator, ahead-right, next to the pond.
        for (int x = 1; x <= 5; x++) {
            for (int z = -4; z <= -2; z++) {
                b.put(new Pos(x, 0, z), Piece.STONE);
            }
        }
        // Flowers (phoenix red/yellow, one cornflower) and two small boulders.
        b.put(new Pos(-2, 1, -3), Piece.POPPY);
        b.put(new Pos(2, 1, 3), Piece.DANDELION);
        b.put(new Pos(-2, 1, 3), Piece.POPPY);
        b.put(new Pos(-3, 1, -1), Piece.CORNFLOWER);
        b.put(new Pos(-5, 1, -3), Piece.ANDESITE);
        b.put(new Pos(-5, 1, -2), Piece.COBBLESTONE);
        b.put(new Pos(1, 1, 5), Piece.ANDESITE);

        // Oak tree to the left: 5 logs, natural crown (every leaf within 4 blocks of a log).
        Pos trunk = new Pos(-4, 1, 1);
        for (int i = 0; i < 5; i++) {
            b.put(trunk.offset(0, i, 0), Piece.OAK_LOG);
        }
        for (int dy = 3; dy <= 4; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                        continue;
                    }
                    b.putIfAbsent(trunk.offset(dx, dy, dz), Piece.OAK_LEAVES);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) {
                    continue;
                }
                b.putIfAbsent(trunk.offset(dx, 5, dz), Piece.OAK_LEAVES);
            }
        }
        return b;
    }

    /**
     * Vanilla leaf distance (1-7) for every leaf: steps to the nearest log through leaves (6 directions). Leaves at 7
     * decay, so placing them with these values makes the tree behave like a natural one.
     */
    public static Map<Pos, Integer> leafDistances(Map<Pos, Piece> blocks) {
        Map<Pos, Integer> distance = new HashMap<>();
        Deque<Pos> queue = new ArrayDeque<>();
        blocks.forEach((pos, piece) -> {
            if (piece == Piece.OAK_LOG) {
                distance.put(pos, 0);
                queue.add(pos);
            }
        });
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            Pos pos = queue.poll();
            int d = distance.get(pos);
            if (d >= 7) {
                continue;
            }
            for (int[] dir : dirs) {
                Pos next = pos.offset(dir[0], dir[1], dir[2]);
                if (blocks.get(next) == Piece.OAK_LEAVES && !distance.containsKey(next)) {
                    distance.put(next, d + 1);
                    queue.add(next);
                }
            }
        }
        Map<Pos, Integer> leaves = new HashMap<>();
        blocks.forEach((pos, piece) -> {
            if (piece == Piece.OAK_LEAVES) {
                leaves.put(pos, Math.min(7, distance.getOrDefault(pos, 7)));
            }
        });
        return leaves;
    }
}
