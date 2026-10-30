package net.skybornmc.skyblock.depths.drops;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * The drops of one Depths block/log were rolled and are about to go into the satchel (M4.9). Main thread, once per
 * accepted break, only fired when a listener is registered for it. Hook for custom enchants (E2: book drops, gathering
 * effects): change the amounts or add extra satchel items here. Everything set here is still capped by the satchel
 * (a full satchel refused the break before this event).
 */
public final class DepthsDropEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final ItemStack tool;
    private final String wing;
    private final int tier;
    private final String item;
    private int items;
    private long crystals;
    private final Map<String, Integer> extra;
    private final double crystalsPerBlock;

    public DepthsDropEvent(Player player, Block block, ItemStack tool, String wing, int tier, String item, int items, long crystals,
                           Map<String, Integer> rare) {
        this(player, block, tool, wing, tier, item, items, crystals, rare, 0);
    }

    /** @param crystalsPerBlock the tier's average crystals per block before bonuses (E2: Crystal Geode / Amber Resin worth) */
    public DepthsDropEvent(Player player, Block block, ItemStack tool, String wing, int tier, String item, int items, long crystals,
                           Map<String, Integer> rare, double crystalsPerBlock) {
        super(player);
        this.crystalsPerBlock = Double.isFinite(crystalsPerBlock) ? Math.max(0, crystalsPerBlock) : 0;
        this.block = block;
        this.tool = tool;
        this.wing = wing;
        this.tier = tier;
        this.item = item;
        this.items = items;
        this.crystals = crystals;
        this.extra = new HashMap<>(rare);
    }

    public Block getBlock() {
        return block;
    }

    /** Item in the main hand (do not modify it here). */
    public ItemStack getTool() {
        return tool;
    }

    /** {@code mine} or {@code forest}. */
    public String getWing() {
        return wing;
    }

    public int getTier() {
        return tier;
    }

    /** The tier's drop item id. */
    public String getItem() {
        return item;
    }

    public int getItems() {
        return items;
    }

    public void setItems(int items) {
        this.items = Math.max(0, items);
    }

    public long getCrystals() {
        return crystals;
    }

    public void setCrystals(long crystals) {
        this.crystals = Math.max(0, crystals);
    }

    /** The tier's average crystals per block before bonuses (0 when unknown). */
    public double getCrystalsPerBlock() {
        return crystalsPerBlock;
    }

    /** Extra satchel items (rare finds so far), item id → amount. Add drop item ids registered in depths.yml. */
    public Map<String, Integer> getExtra() {
        return extra;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
