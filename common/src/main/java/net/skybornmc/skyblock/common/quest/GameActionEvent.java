package net.skybornmc.skyblock.common.quest;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player finished a game action that quests (PHASE-10 M10.7) and later the battle pass count, fired by the module that
 * owns the action, on the main thread, <b>after</b> it succeeded and was saved or paid: a forge upgrade (tools), adding or
 * raising an enchant (enchants), a first visit of the day to another player's island and a like (islands), a ripe crop
 * harvest on the own island (production). Never fired for
 * staff actions, refunds or undo. Listeners only read it.
 */
public final class GameActionEvent extends PlayerEvent {

    /** A tool was forged to its next tier ({@code /forge}). */
    public static final String FORGE = "forge";
    /** An enchant was added to or raised on a tool or armor piece ({@code /enchanter}: apply, upgrade, combine). */
    public static final String ENCHANT = "enchant";
    /** First visit today of this player to another player's island (the same rule as the island's visitor count). */
    public static final String VISIT_ISLAND = "visit_island";
    /** The player liked an island ({@code /is like}, once per island per day). */
    public static final String LIKE_ISLAND = "like_island";
    /**
     * Ripe crops harvested on the player's own island (PHASE-7 M7.3, production module; amount = crop blocks): only members,
     * never coop players or visitors, never bone-mealed or placed blocks.
     */
    public static final String HARVEST_CROP = "harvest_crop";

    private static final HandlerList HANDLERS = new HandlerList();

    private final String action;
    private final long amount;

    public GameActionEvent(@NotNull Player player, @NotNull String action, long amount) {
        super(player);
        this.action = action;
        this.amount = Math.max(0, amount);
    }

    /** One of the constants above. */
    public @NotNull String action() {
        return action;
    }

    /** How many times (usually 1). */
    public long amount() {
        return amount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
