package net.skybornmc.skyblock.achievements;

import java.util.List;

/** Something with rising goals, one per tier: personal ({@link AchievementDef}) and island ({@link IslandAchievementDef}) achievements. */
interface Tiered {

    String id();

    /** One goal per tier, strictly rising. */
    List<Long> goals();

    default int tiers() {
        return goals().size();
    }

    default long goal(int tier) {
        return goals().get(tier - 1);
    }

    default long finalGoal() {
        return goals().getLast();
    }
}
