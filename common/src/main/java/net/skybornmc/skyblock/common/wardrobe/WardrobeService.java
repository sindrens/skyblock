package net.skybornmc.skyblock.common.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The wardrobe (docs/plans/WARDROBE.md), published by the wardrobe module:
 * {@code core.services().optional(WardrobeService.class)}. Main thread unless noted.
 *
 * <p>Sets are database rows per <b>profile</b> ({@code ProfileService#profileKey}); worn pieces are real armor items that
 * are only a view of those rows (locked to the armor slots, copies are deleted). Set bonuses reach other systems only
 * through the {@code BonusService} (source id {@code wardrobe}), so they always respect the family caps.</p>
 */
public interface WardrobeService {

    /** Bonus source id in the {@code BonusService}. */
    String BONUS_SOURCE = "wardrobe";

    /** Set id → number of its pieces the player wears right now (merged entries count for both sets). Empty while loading. */
    Map<String, Integer> wornPieces(Player player);

    /** Set ids the profile owns (active entries); only for online players' active profiles, empty otherwise. */
    Set<String> ownedSets(UUID profileKey);

    /** Set level of an owned set (0-50), 0 when not owned. Set levels arrive with W2; until then every set is level 0. */
    int setLevel(UUID profileKey, String setId);

    /**
     * Entry point for set XP from other modules (Depths blocks, crops, kills, selling …), WARDROBE §3.1. The wardrobe
     * decides which worn sets earn it. Does nothing while set levels are off ({@code wardrobe.yml → levels.enabled}, W2).
     *
     * @param activity e.g. {@code mining}, {@code foraging}, {@code farming}, {@code combat}, {@code fishing}, {@code sell}
     */
    void setXp(Player player, String activity, double amount);

    /** Enchant slots of a worn piece for E5 (EN12): 1, a 2nd at set level 25; the chestplate's mythic slot is separate. */
    int enchantSlots(UUID profileKey, String setId, ArmorPiece piece);

    /** True for a worn wardrobe piece (a view item; other systems must never trade, list or store it). */
    boolean isSetPiece(ItemStack stack);

    /**
     * Makes a new Set Token for a set (crates, quests, staff): writes its database row, then completes on the main thread
     * with the item. Completes exceptionally for an unknown set or a database error. Audited.
     *
     * @param origin short id of the source, e.g. {@code crate:inferno}
     * @param owner  the player it is made for (account), or null
     */
    CompletableFuture<ItemStack> createToken(String setId, String origin, UUID owner, Actor actor);

    /** Opens the staff view of a player's wardrobe (the Wardrobe button in {@code /inspect}). */
    void openStaffView(Player staff, UUID target, String targetName, Menu parent);
}
