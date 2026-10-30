package net.skybornmc.skyblock.common.tutorial;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The custom starter chest and starter pack (TUTORIAL.md §6, owner answer 158), published by the tutorial module:
 * {@code core.services().optional(StarterContents.class)}. Without it the islands module fills the chest from its own
 * {@code islands.yml → starter-chest.items} list. Main thread.
 */
public interface StarterContents {

    /**
     * Fills the starter chest of a brand-new island (main thread, right after the paste). Plain items and the Field Guide
     * go in at once; the starter tools follow a moment later (the Forge starter kit claim of that profile is saved first,
     * TQ15). {@code chest} may be {@code null} when the build has no chest (placeholder failed): the contents then go to
     * the owner's inventory and {@code /box}.
     *
     * @param ownerProfile profile key of the island leader
     * @return what went in at once ({@code material:amount}), for the island audit row
     */
    List<String> fillChest(Block chest, UUID ownerProfile);

    /**
     * Gives the Starter pack (starter tools not yet claimed on this profile, the Field Guide, bread) to a new player who
     * joined a team instead of making an island (§6.1). Overflow goes to {@code /box}. Once per account; false when not
     * given (already given, or the account finished the tutorial before).
     */
    boolean givePack(Player player);
}
