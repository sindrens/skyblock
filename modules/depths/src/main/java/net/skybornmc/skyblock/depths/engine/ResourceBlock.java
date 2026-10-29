package net.skybornmc.skyblock.depths.engine;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Objects;

/**
 * One kind of resource block in a region: breaking {@code block} shows the player {@code depleted} for
 * {@code respawnTicks} ticks, then the real block again.
 *
 * @param block        the real block in the world (matched by material, any block state)
 * @param depleted     what the player sees while it is depleted (a full block state, e.g. {@code minecraft:tuff})
 * @param respawnTicks how long it stays depleted for that player (default 10 s = 200 ticks)
 */
public record ResourceBlock(Material block, BlockData depleted, int respawnTicks) {

    public ResourceBlock {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(depleted, "depleted");
        if (respawnTicks < 1) {
            throw new IllegalArgumentException("respawnTicks must be >= 1");
        }
    }
}
