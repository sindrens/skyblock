package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;

/**
 * What a Sponge schematic ({@code .schem}, version 2 or 3) says about itself, read without WorldEdit.
 *
 * @param version     Sponge format version
 * @param dataVersion Minecraft data version the blocks were saved with
 * @param width       size on x
 * @param height      size on y
 * @param length      size on z
 * @param minX        world position of the region's minimum corner when it was copied ({@code Offset})
 * @param originX     world position of the copy point (WorldEdit origin) when it was copied
 * @param nonAir      blocks that are not air (cave/void air count as air), -1 if unknown
 * @param blockEntities number of block entities (chests, signs, banners...)
 * @param entities    number of entities
 */
public record SchematicInfo(int version, int dataVersion, int width, int height, int length, int minX, int minY, int minZ,
                            int originX, int originY, int originZ, long nonAir, int blockEntities, int entities) {

    public long volume() {
        return (long) width * height * length;
    }

    /** The region this schematic covers when pasted with its copy point at {@code x, y, z}. */
    public BuildBox pastedAt(int x, int y, int z) {
        int dx = minX - originX;
        int dy = minY - originY;
        int dz = minZ - originZ;
        return new BuildBox(x + dx, y + dy, z + dz, x + dx + width - 1, y + dy + height - 1, z + dz + length - 1);
    }
}
