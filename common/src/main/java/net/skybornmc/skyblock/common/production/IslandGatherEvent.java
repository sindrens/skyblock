package net.skybornmc.skyblock.common.production;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A member mined an island generator ore (PHASE-7 M7.2, the "island gather hook" of ENCHANTS §3.1). Fired by the production
 * module on the main thread after the break went through (MONITOR), only while custom generators are on, only for players
 * who count for that island (stored members on their own island, never coop players or visitors) and only for ore blocks
 * of the generator table (not cobblestone).
 *
 * <p>Information only: the block is already broken and its drops are decided. {@link #tier()} is the Depths mine tier the ore
 * matches (coal 1 … Emberite 10), for book rarity bands. {@link #generatorMade()} is true only when the island's generator
 * really made this block (a placed or piston-moved ore is false); book rolls and pillar XP look at it.</p>
 */
public final class IslandGatherEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final UUID islandId;
    private final String oreId;
    private final int tier;
    private final boolean generatorMade;

    public IslandGatherEvent(Player player, Block block, UUID islandId, String oreId, int tier, boolean generatorMade) {
        super(player);
        this.block = block;
        this.islandId = islandId;
        this.oreId = oreId;
        this.tier = tier;
        this.generatorMade = generatorMade;
    }

    /** The broken block (already air or its replacement when listeners run). */
    public Block block() {
        return block;
    }

    public UUID islandId() {
        return islandId;
    }

    /** Ore id from {@code generators.yml → ores} ({@code coal}, {@code ashstone} …). */
    public String oreId() {
        return oreId;
    }

    /** The Depths mine tier this ore matches (1–10). */
    public int tier() {
        return tier;
    }

    /** True when the island's generator made this block (remembered position, not placed or moved). */
    public boolean generatorMade() {
        return generatorMade;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
