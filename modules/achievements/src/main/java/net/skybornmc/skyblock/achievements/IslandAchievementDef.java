package net.skybornmc.skyblock.achievements;

import java.util.List;

/**
 * One island achievement from {@code achievements.yml → island-achievements} (RANKS_TITLES_ACHIEVEMENTS §C4). Progress
 * is shared by the island's members; the last tier's title goes to every member who has been on the island long enough
 * ({@code island.title-min-days}, owner AC7 default 3 days).
 *
 * @param teamOnly only team islands can progress it (Team Flame); solo islands don't see it
 */
record IslandAchievementDef(String id, String name, List<String> description, String icon, IslandMetric metric,
                            List<Long> goals, AchievementDef.Title title, boolean hidden, boolean teamOnly) implements Tiered {

    IslandAchievementDef {
        description = List.copyOf(description);
        goals = List.copyOf(goals);
    }

    boolean hasTitle() {
        return title != null;
    }
}
