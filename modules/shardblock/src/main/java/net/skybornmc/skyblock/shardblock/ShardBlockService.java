package net.skybornmc.skyblock.shardblock;

import org.bukkit.inventory.ItemStack;

/**
 * Published by the shardblock module (ECON-SHARDS step 4). The credit shop contract (section 12) and staff tools create
 * shard block items through it; there is no other way to get one besides crates ({@code material: custom:shard_block}).
 */
public interface ShardBlockService {

    /** New level-0 shard blocks ({@code source} is recorded on the item, e.g. {@code credit_shop}). */
    ItemStack create(int amount, String source);
}
