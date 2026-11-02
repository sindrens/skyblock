package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.List;

/**
 * The ready-made farm on every new island (owner 3 Nov 2026: "one premade farm that has water and farmlands" and "more
 * on the starter island"). Pure plan of blocks relative to the island's paste origin (y 0 = the surface, islands.yml
 * {@code surface-y}); {@link IslandBuilder} places it after the schematic, <b>only into air</b>, so it never overwrites
 * the builders' island.
 *
 * <pre>
 * (looking down, x grows to the right, the island is on the left)
 *   R R R R R R R R C     R = rim (mossy stone bricks), C = composter / crafting table on the rim corners
 *   P f f f f f f R       P = path (coarse dirt) along the island edge
 *   P w w w w w w R       w = wheat on farmland, f = empty farmland (for the player to plant)
 *   P ~ ~ ~ ~ ~ ~ R       ~ = water (one row, sealed by the path and the rim)
 *   P w w w w w w R
 *   P f f f f f f R
 *   R R R R R R R R T
 * </pre>
 * The plot is {@code width} long (along x, from {@code offsetX}) and {@code rows} farm rows on each side of the water row.
 * Underneath: one layer of dirt and one of stone.
 */
final class StarterFarm {

    /** One block: offset from the paste origin, a block id and optional block state ({@code age=0}, {@code moisture=7}). */
    record Piece(int dx, int dy, int dz, String block) {
    }

    private StarterFarm() {
    }

    /**
     * @param offsetX first x of the plot (the path column), relative to the origin
     * @param offsetZ the water row's z
     * @param width   farm columns (along x) between the path and the rim
     * @param rows    farm rows on each side of the water row
     * @param planted rows next to the water (on each side) that already have wheat; the outer ones stay empty
     * @param crop    the crop block id ({@code wheat})
     */
    static List<Piece> plan(int offsetX, int offsetZ, int width, int rows, int planted, String crop) {
        List<Piece> out = new ArrayList<>();
        if (width < 1 || rows < 1) {
            return out;
        }
        int x0 = offsetX;               // path
        int x1 = offsetX + width + 1;   // rim
        int zMin = offsetZ - rows - 1;  // rim
        int zMax = offsetZ + rows + 1;  // rim
        for (int x = x0; x <= x1; x++) {
            for (int z = zMin; z <= zMax; z++) {
                out.add(new Piece(x, -1, z, "dirt"));
                out.add(new Piece(x, -2, z, "stone"));
                boolean rim = x == x1 || z == zMin || z == zMax;
                if (rim) {
                    out.add(new Piece(x, 0, z, "mossy_stone_bricks"));
                } else if (x == x0) {
                    out.add(new Piece(x, 0, z, "coarse_dirt"));
                } else if (z == offsetZ) {
                    out.add(new Piece(x, 0, z, "water"));
                } else {
                    out.add(new Piece(x, 0, z, "farmland[moisture=7]"));
                    if (Math.abs(z - offsetZ) <= planted && crop != null && !crop.isBlank()) {
                        out.add(new Piece(x, 1, z, crop + "[age=0]"));
                    }
                }
            }
        }
        out.add(new Piece(x1, 1, zMin, "composter"));
        out.add(new Piece(x1, 1, zMax, "crafting_table"));
        return out;
    }
}
