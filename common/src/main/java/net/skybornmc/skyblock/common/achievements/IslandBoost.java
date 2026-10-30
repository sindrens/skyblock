package net.skybornmc.skyblock.common.achievements;

import java.util.Locale;

/**
 * The four island boosts of island achievements (RANKS_TITLES_ACHIEVEMENTS §C4, owner 355): one pool of island points
 * per island, each point adds a fixed step up to the boost's own cap. Read them with
 * {@link AchievementService#islandBoostPercent}. Island XP is fed into the progression module's {@code ISLAND_XP}
 * family by the achievements module itself; the other three are read by the systems that apply them (island
 * production, island crystal sources, Island Fuel) once those exist.
 */
public enum IslandBoost {
    /** Island production: generator, crop growth, mob loot and spawner rate as one factor. */
    PRODUCTION,
    /** Crystals from island missions and island sources. */
    CRYSTALS,
    /** Pillar XP of the island (progression ISLAND_XP family). */
    ISLAND_XP,
    /** Fuel efficiency: less Island Fuel burned per offline hour. */
    FUEL;

    /** Lowercase id used in configs and messages, e.g. {@code island_xp}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
