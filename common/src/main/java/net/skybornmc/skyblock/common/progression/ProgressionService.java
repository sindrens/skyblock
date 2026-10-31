package net.skybornmc.skyblock.common.progression;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Island progression (Phase 5), published by the progression module: {@code core.services().optional(ProgressionService.class)}.
 * M5.1 covers the pillars; the island level (M5.2), tasks, upgrades and masteries build on the same service later.
 *
 * <p><b>Pillar XP</b> comes from the <b>base</b> skill XP members earn (before rank, booster and personal boosts, RK20),
 * plus island crops, island trees and spawner mobs killed by a player. Coop players and visitors give nothing (REVIEW A4);
 * XP a member earned stays with the island (A5). Island boosts (the balance bonus and sources registered with
 * {@link #islandXpBoost}) add up in one family, {@code ISLAND_XP}, capped at {@code pillars.yml → island-xp.cap-percent}.</p>
 *
 * <p>Curve: XP for the next level = {@code base × growth^level} (no cap), so five members earning five times the XP gain
 * only {@code ln 5 / ln growth} levels more (team-size damping). Levels never go down within a season.</p>
 *
 * <p>Every method is for the main thread. Values come from memory (all islands of the season are loaded at start); an
 * unknown island reads as level 0. Events: {@link PillarLevelUpEvent}, {@link PillarMilestoneEvent}.</p>
 */
public interface ProgressionService {

    /** Pillars players see and that count for "all pillars" (Conquest only once {@code conquest.enabled} is on). */
    List<Pillar> shownPillars();

    default boolean shown(Pillar pillar) {
        return shownPillars().contains(pillar);
    }

    /** True once the stored pillars were loaded (before that, levels read 0 and gains are kept until the load ends). */
    boolean ready();

    /** The pillar's level on the island (0 for unknown islands). */
    int pillarLevel(UUID islandId, Pillar pillar);

    /** Total pillar XP on the island this season. */
    double pillarXp(UUID islandId, Pillar pillar);

    /** Progress to the next pillar level, 0.0 to 1.0. */
    double pillarProgress(UUID islandId, Pillar pillar);

    /** Every pillar's level on the island (all five, hidden Conquest included). */
    Map<Pillar, Integer> pillarLevels(UUID islandId);

    /** The lowest level among the shown pillars (synergy milestones look at this). */
    int lowestPillarLevel(UUID islandId);

    /** Sum of the shown pillars' levels: island level source 1 (M5.2, 1 point per pillar level). */
    int totalPillarLevels(UUID islandId);

    /** Total XP needed to reach {@code level} from 0 on the pillar curve. */
    double xpForPillarLevel(int level);

    /** The level a total pillar XP amount gives. */
    int pillarLevelFor(double totalXp);

    /** Balance bonus of the island in percent (+2% per shown pillar within 10 levels of the highest, max +10%). */
    double balanceBonusPercent(UUID islandId);

    /** The whole ISLAND_XP family of the island after its cap, in percent (balance bonus + registered boosts). */
    double islandXpBonusPercent(UUID islandId);

    /** Synergy milestones (all shown pillars at that level) the island has reached this season. */
    Set<Integer> milestonesReached(UUID islandId);

    /** The configured synergy milestone levels, lowest first (default 10, 25, 40, 60). */
    List<Integer> milestones();

    /**
     * Adds pillar XP for a source outside the skills (Conquest events, staff tools): the island's ISLAND_XP boosts are
     * applied on top of {@code baseXp}. Level-ups fire {@link PillarLevelUpEvent}; staff actors are audited. Main thread.
     *
     * @param source short reason for logs, e.g. {@code "koth"}, {@code "staff"}
     * @return the XP actually added (0 for unknown islands, non-positive amounts or a disabled module)
     */
    double addPillarXp(UUID islandId, Pillar pillar, double baseXp, String source, Actor actor);

    /**
     * Registers (or with {@code null} removes) a source of the island ISLAND_XP family, e.g. island achievements
     * ("Island XP +5%"): percent per island id. Values add up with the balance bonus, then the family cap applies.
     */
    void islandXpBoost(String id, ToDoubleFunction<UUID> percentByIsland);

    // ------------------------------------------------------------------ island level (M5.2)

    /**
     * The dynamic island level ({@code floor(total points ÷ divisor)}, owner P5-1): 0 for unknown islands or before the
     * first calculation. From memory; recalculated off the main thread after changes (debounced).
     */
    default int islandLevel(UUID islandId) {
        return 0;
    }

    /** True once the island has a calculated level (the scoreboard Level line shows only then). */
    default boolean hasIslandLevel(UUID islandId) {
        return false;
    }

    /** Progress to the next island level, 0.0 to 1.0. */
    default double islandLevelProgress(UUID islandId) {
        return 0;
    }

    /** Total level points of the island (all eight sources after their caps). */
    default double islandLevelPoints(UUID islandId) {
        return 0;
    }

    /** Points per source after the caps (every source present, 0 when it gives nothing). */
    default Map<LevelSource, Double> islandLevelBreakdown(UUID islandId) {
        return Map.of();
    }

    /** Position of the island on its own board (solo or team), 0 when it is not ranked (yet). */
    default int islandRank(UUID islandId) {
        return 0;
    }

    /**
     * Registers (or with {@code null} removes) the points of an earned source that lives in another module: upgrades and
     * masteries ({@link LevelSource#UPGRADES}), island achievement points ({@link LevelSource#ACHIEVEMENTS}), collection
     * milestones ({@link LevelSource#COLLECTIONS}). Raw points per island id, before the caps; called on the main thread
     * when the level is recalculated. Call {@link #requestLevelUpdate} when the value changes.
     */
    default void levelPointsProvider(LevelSource source, ToDoubleFunction<UUID> pointsByIsland) {
    }

    /** Asks for a (debounced) recalculation of the island's level, e.g. after an upgrade was bought. Main thread. */
    default void requestLevelUpdate(UUID islandId) {
    }

    /** Ranked islands, best first (from the cached boards, refreshed every few minutes). Any thread. */
    default java.util.List<IslandTop.Entry> islandTop(IslandTop.Kind kind, IslandTop.Board board, int limit) {
        return java.util.List.of();
    }

    /** Opens {@code /is level} for {@code player} (their own island); {@code parent} = Back target or null. Main thread. */
    default void openLevelMenu(org.bukkit.entity.Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
    }

    /** Opens {@code /is top}; {@code parent} = Back target or null. Main thread. */
    default void openTopMenu(org.bukkit.entity.Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
    }

    // ------------------------------------------------------------------ island upgrades (M5.4)

    /** True when island upgrades can be opened (the upgrade catalog is loaded and switched on). */
    default boolean upgradesAvailable() {
        return false;
    }

    /** Opens {@code /is upgrade} for {@code player} (their own island); {@code parent} = Back target or null. Main thread. */
    default void openUpgradeMenu(org.bukkit.entity.Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
    }

    /** The bought level of an upgrade ({@code upgrades.yml} id, e.g. {@code island_size}) on the island; 0 = none. */
    default int upgradeLevel(UUID islandId, String upgradeId) {
        return 0;
    }

    // ------------------------------------------------------------------ island masteries (M5.5)

    /** True when island masteries can be opened ({@code /is masteries}, the Masteries tab of {@code /is upgrade}). */
    default boolean masteriesAvailable() {
        return false;
    }

    /** Opens {@code /is masteries} (the upgrade menu on its Masteries tab) for {@code player}'s own island. Main thread. */
    default void openMasteryMenu(org.bukkit.entity.Player player, net.skybornmc.skyblock.common.menu.Menu parent) {
    }

    /** The bought rank of a mastery ({@code masteries.yml} id, e.g. {@code wings}) on the island; 0 = none. */
    default int masteryRank(UUID islandId, String masteryId) {
        return 0;
    }
}
