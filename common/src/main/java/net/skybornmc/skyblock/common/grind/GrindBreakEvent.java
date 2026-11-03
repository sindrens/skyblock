package net.skybornmc.skyblock.common.grind;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player harvested a crop at {@code /farm} or broke a block at {@code /mine} (essentials, grind world; owner 3 Nov 2026,
 * replaces the Depths resource break). Fired on the main thread after the grind areas accepted the break, never for staff
 * in creative. Both areas are per player: the real blocks are wheat and stone, each player sees and harvests their own
 * unlocked crop or ore, so {@link #block()} is the block the player saw ({@code CARROTS}, {@code IRON_ORE}, or
 * {@code STONE} for plain stone) and {@link #drop()} what it gives. Grind blocks are never placed by players.
 * Listeners only read it (skills XP, island tasks, quests).
 */
public final class GrindBreakEvent extends PlayerEvent {

    /** {@link #area()} of a {@code /farm} harvest. */
    public static final String FARM = "farm";
    /** {@link #area()} of a {@code /mine} break. */
    public static final String MINE = "mine";

    private static final HandlerList HANDLERS = new HandlerList();

    private final String area;
    private final Material block;
    private final Material drop;
    private final int amount;
    private final Location location;

    public GrindBreakEvent(@NotNull Player player, @NotNull String area, @NotNull Material block, @NotNull Material drop, int amount,
                           @NotNull Location location) {
        super(player);
        this.area = area;
        this.block = block;
        this.drop = drop;
        this.amount = Math.max(0, amount);
        this.location = location;
    }

    /** {@link #FARM} or {@link #MINE}. */
    public @NotNull String area() {
        return area;
    }

    public boolean farm() {
        return FARM.equals(area);
    }

    /** The block the player saw and broke ({@code WHEAT}, {@code CARROTS}, {@code COAL_ORE}, {@code STONE} ...). */
    public @NotNull Material block() {
        return block;
    }

    /** The item the break gives ({@code WHEAT}, {@code CARROT}, {@code RAW_IRON}, {@code COBBLESTONE} ...). */
    public @NotNull Material drop() {
        return drop;
    }

    /** Items of {@link #drop()} (at least 1 for a normal break; the farm rolls 1-2 crops later, this says 1). */
    public int amount() {
        return amount;
    }

    /** Where the block was. */
    public @NotNull Location location() {
        return location.clone();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
