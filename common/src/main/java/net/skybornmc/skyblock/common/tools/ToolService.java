package net.skybornmc.skyblock.common.tools;

import net.skybornmc.skyblock.common.audit.Actor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Custom tools (M4.2), published by the tools module: {@code core.services().optional(ToolService.class)}.
 * Built so the enchants module (Phase E, docs/plans/ENCHANTS.md) can plug in: slots, levels and the perk list live here,
 * enchant rules (conflicts, costs, books) live in the enchants module. Main-thread only unless noted.
 *
 * <p>The item carries the live tool data (persistent data on the item, so it travels through trades, chests and the
 * auction house); the {@code tools} table mirrors it for staff lookups and atomic operations.</p>
 *
 * <p>Applying an enchant atomically (ENCHANTS §4.2): in one database transaction consume the book, debit the crystals
 * and call {@link #savePerks(Connection, UUID, List)}; after it commits call {@link #setPerks} on the main thread with
 * the same list to update the item.</p>
 */
public interface ToolService {

    /** The tool data of a custom tool item, or empty for anything else. */
    Optional<ToolInfo> read(ItemStack stack);

    default boolean isTool(ItemStack stack) {
        return read(stack).isPresent();
    }

    /**
     * Depths drops per block/log with this item (M4.9): 1 + Forge tier fortune + level fortune + the tool's
     * extra-drops bonus family, as an expected value (1.15 = 15% chance of one extra drop). 1.0 for anything that is
     * not a custom tool.
     */
    default double dropMultiplier(ItemStack stack) {
        return 1.0;
    }

    /** Highest tool level (50). */
    int maxLevel();

    /** Levels that open perk slots 1-4 (5 / 15 / 30 / 45). */
    List<Integer> slotLevels();

    /** Unlocked slots for a level and prestige count (see {@link ToolSlots#unlocked}). */
    default int slots(int level, int prestige) {
        return ToolSlots.unlocked(level, prestige, slotLevels());
    }

    /** ENCHANTS §2.4 gate for this tool: max level of an enchant whose highest level is {@code enchantMax}. */
    default int maxEnchantLevel(ToolInfo tool, int enchantMax) {
        return ToolSlots.maxEnchantLevel(enchantMax, ToolSlots.effectiveLevel(tool.level(), tool.prestige(), maxLevel()), maxLevel());
    }

    /**
     * Replaces the perks of the tool item in {@code holder}'s inventory with the given id (slots must be unlocked and not
     * overlap). Re-renders the lore, saves the database row in the background and writes an audit row.
     *
     * @return false if the tool is not in the holder's inventory or a perk is in a locked/overlapping slot
     */
    boolean setPerks(Player holder, UUID toolId, List<ToolPerk> perks, Actor actor, String reason);

    /** SQL for a caller's own transaction: stores the perk list of a tool row. Any thread (inside a database task). */
    void savePerks(Connection connection, UUID toolId, List<ToolPerk> perks) throws SQLException;

    /**
     * How perk names show in the lore and tool menu, e.g. {@code "Vein Rend II"} (registered by the enchants module).
     * Until one is registered the raw id and level are shown.
     */
    void perkNames(Function<ToolPerk, String> namer);

    /**
     * TU4 (TUTORIAL.md §6, TQ15): the Forge starter kit (tier 1 of every starter kind) for the starter chest or the
     * Starter pack of a profile. With {@code markClaimed} the profile's starter kit claim is saved first (atomic, the 24 h
     * re-claim timer starts); while that timer still runs the result is empty (no second kit on one profile). The tool
     * rows are saved and an audit row ({@code starter_kit}, source {@code tutorial}) is written. Completes on the main
     * thread; an empty list when the tools module can't make them.
     *
     * @param profileKey the profile the tools belong to (claim key)
     * @param holder     the player who will own them (tool rows), usually the profile's player
     * @param holderName their name (item signature, audit)
     */
    default java.util.concurrent.CompletableFuture<List<ItemStack>> starterKit(UUID profileKey, UUID holder, String holderName,
                                                                               boolean markClaimed) {
        return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }
}
