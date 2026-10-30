package net.skybornmc.skyblock.common.island;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * The live island upgrade effects (PHASE-5 M5.4) that the islands module answers through its upgrade hooks
 * ({@link IslandService#spawnerStackLimit}, {@link IslandService#spawnerRateMultiplier},
 * {@link IslandService#entityLimitBonusPercent}, {@link IslandService#bankCapacityLevel},
 * {@link IslandService#bankInterestLevel}). Set by the progression module with {@link IslandService#upgradeEffects};
 * every method is called on the main thread, often, and must only read memory. The defaults mean "no upgrade".
 *
 * <p>Size, member slots and warp slots are not here: they are stored by the islands module and changed through its
 * setters ({@link IslandService#setSize}, {@link IslandService#setMemberSlots}, {@link IslandService#setWarpSlots}).</p>
 */
public interface IslandUpgradeEffects {

    /** Spawners one stacked spawner block may hold; empty = the start value of {@code stacking.yml}. */
    default OptionalInt spawnerStackLimit(UUID islandId) {
        return OptionalInt.empty();
    }

    /** Multiplier on every spawner's output (1.0 = no upgrade). */
    default double spawnerRateMultiplier(UUID islandId) {
        return 1.0;
    }

    /** Extra percent on the island entity limits (0 = no upgrade). */
    default int entityLimitBonusPercent(UUID islandId) {
        return 0;
    }

    /** Bank Capacity level (0 = start capacity). */
    default int bankCapacityLevel(UUID islandId) {
        return 0;
    }

    /** Bank Interest level (0 = start payout cap). */
    default int bankInterestLevel(UUID islandId) {
        return 0;
    }
}
