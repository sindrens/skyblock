package net.skybornmc.skyblock.common.production;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A member harvested ripe crops on their own island (PHASE-7 M7.3, the hook for the E6 hoe enchants). Fired by the
 * production module on the main thread after the harvest went through (MONITOR), only while farming is on, only for
 * players who count for that island (stored members on their own island in survival, never coop players or visitors) and
 * only for ripe crops (full age, or melons, pumpkins, sugar cane, cactus and torchflowers that really grew).
 *
 * <p>Information only: the block is already broken (or picked, for sweet berries) and its drops are decided.
 * {@link #xpEligible()} follows the PD11 rule (a hoe and no bone meal): book rolls, tool XP and set XP should only use those
 * harvests.</p>
 */
public final class IslandCropHarvestEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final UUID islandId;
    private final String cropId;
    private final int tier;
    private final int crops;
    private final ItemStack tool;
    private final boolean withHoe;
    private final boolean boneMealed;

    public IslandCropHarvestEvent(Player player, Block block, UUID islandId, String cropId, int tier, int crops, ItemStack tool,
                                  boolean withHoe, boolean boneMealed) {
        super(player);
        this.block = block;
        this.islandId = islandId;
        this.cropId = cropId;
        this.tier = tier;
        this.crops = crops;
        this.tool = tool;
        this.withHoe = withHoe;
        this.boneMealed = boneMealed;
    }

    /** The harvested block (already air, or the bush that was picked). */
    public Block block() {
        return block;
    }

    public UUID islandId() {
        return islandId;
    }

    /** Crop id from {@code crops.yml → crops} ({@code wheat}, {@code melon} …). */
    public String cropId() {
        return cropId;
    }

    /** The crop tier (1–11). */
    public int tier() {
        return tier;
    }

    /** Ripe crop blocks in this harvest (a sugar cane or cactus column can be several). */
    public int crops() {
        return crops;
    }

    /** A copy of the item in the main hand (air when empty). */
    public ItemStack tool() {
        return tool == null ? null : tool.clone();
    }

    /** Broken or picked with a hoe. */
    public boolean withHoe() {
        return withHoe;
    }

    /** The crop was grown with bone meal (drops normally, gives no XP or books). */
    public boolean boneMealed() {
        return boneMealed;
    }

    /** PD11: with a hoe and not bone-mealed; only these give skill XP, and should give tool XP, set XP and books. */
    public boolean xpEligible() {
        return withHoe && !boneMealed;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
