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

    // Set XP action ids for {@link #setXp} (WARDROBE §3.1; amounts per unit in wardrobe.yml → xp.actions.*).
    // The wardrobe feeds Depths blocks/logs (skill XP events), Depths kills, island generator blocks, grown logs, crops
    // and fishing itself; the others are sent by their modules when they exist.
    /** One item sold ({@code /sell}, satchel, sell wand). */
    String XP_SELL_ITEM = "sell_item";
    /** One island item sold (half rate). */
    String XP_SELL_ISLAND_ITEM = "sell_island_item";
    /** A mini-boss with 5%+ of the damage. */
    String XP_MINI_BOSS = "mini_boss";
    /** A world boss with 5%+ of the damage. */
    String XP_WORLD_BOSS = "world_boss";
    /** One second on a KOTH capture point. */
    String XP_KOTH_TICK = "koth_tick";
    /** A KOTH capture. */
    String XP_KOTH_CAPTURE = "koth_capture";
    /** An envoy crate claimed. */
    String XP_ENVOY = "envoy";
    /** A warzone player kill (the caller applies the victim rules: same victim once per 10 min, victim 30+ min played). */
    String XP_WARZONE_KILL = "warzone_kill";
    /** One fish caught (fed by the wardrobe itself once fishing is available). */
    String XP_FISH = "fish";

    /** Set id → number of its pieces the player wears right now (merged entries count for both sets). Empty while loading. */
    Map<String, Integer> wornPieces(Player player);

    /** Set ids the profile owns (active entries); only for online players' active profiles, empty otherwise. */
    Set<String> ownedSets(UUID profileKey);

    /**
     * Set level (0-50) of a set for an online profile: the highest active entry that contains the set (merged entries
     * count for both of their sets), 0 when not owned or offline.
     */
    int setLevel(UUID profileKey, String setId);

    /**
     * Entry point for set XP from other modules, WARDROBE §3.1 (main thread). The wardrobe decides which worn sets earn it
     * (2-3 pieces 50%, 4 pieces 100%, only sets whose activity it is) and applies the hourly caps (sell). Does nothing while
     * set levels are off ({@code wardrobe.yml → levels.enabled}).
     *
     * @param action an action id ({@link #XP_SELL_ITEM}, {@link #XP_MINI_BOSS} …): {@code amount} is the number of units,
     *               the XP per unit comes from {@code wardrobe.yml → xp.actions}. A plain XP activity ({@code mining},
     *               {@code foraging}, {@code farming}, {@code combat}, {@code boss}, {@code fishing}, {@code sell},
     *               {@code conquest}, {@code pvp}) is also accepted: then {@code amount} is raw set XP.
     */
    void setXp(Player player, String action, double amount);

    /** Bonus multiplier of a set level right now (0x at 0, 1x at 25, 2x at 50; the fixed value while levels are off). */
    double levelMultiplier(int level);

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
