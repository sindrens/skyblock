package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The season summary written at the season reset (RANKS_TITLES_ACHIEVEMENTS §C6, AC4): per profile the tiers done,
 * boost points, finished achievements, its island's tiers and points, and the titles won. Pure, unit-tested.
 */
final class SeasonSummary {

    record UnlockRow(UUID profileKey, UUID owner, String achievement, int tier) {
    }

    record IslandUnlockRow(UUID islandId, String achievement, int tier) {
    }

    /**
     * One history row. {@code titles}: reward titles of the personal achievements this profile finished this season
     * (island titles depend on each member's days on the island, so the island part shows tiers and points only).
     */
    record Entry(int season, UUID profileKey, UUID owner, int tiers, int points, int completed, int islandTiers, int islandPoints,
                 List<String> titles) {

        Entry {
            titles = List.copyOf(titles);
        }

        /** Titles as stored (comma separated, cut to {@code maxLength} at a comma). */
        String titlesText(int maxLength) {
            StringBuilder out = new StringBuilder();
            for (String t : titles) {
                int extra = (out.isEmpty() ? 0 : 1) + t.length();
                if (out.length() + extra > maxLength) {
                    break;
                }
                if (!out.isEmpty()) {
                    out.append(',');
                }
                out.append(t);
            }
            return out.toString();
        }

        static List<String> parseTitles(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.strip());
                }
            }
            return out;
        }
    }

    private SeasonSummary() {
    }

    /**
     * Builds the rows.
     *
     * @param memberIsland island of every stored member (profile key → island id) at the reset
     * @param memberOwner  account of those members (profile key → player uuid)
     * @param teamIslands  islands that are team islands (team-only achievements count only there)
     */
    static List<Entry> compute(int season, AchievementRegistry registry, int[] perTier, List<UnlockRow> unlocks,
                               List<IslandUnlockRow> islandUnlocks, Map<UUID, UUID> memberIsland, Map<UUID, UUID> memberOwner,
                               Set<UUID> teamIslands) {
        Map<UUID, Map<String, Integer>> byProfile = new LinkedHashMap<>();
        Map<UUID, UUID> owners = new HashMap<>(memberOwner);
        for (UnlockRow row : unlocks) {
            byProfile.computeIfAbsent(row.profileKey(), k -> new HashMap<>()).merge(row.achievement(), row.tier(), Math::max);
            owners.putIfAbsent(row.profileKey(), row.owner());
        }
        Map<UUID, Map<String, Integer>> byIsland = new HashMap<>();
        for (IslandUnlockRow row : islandUnlocks) {
            byIsland.computeIfAbsent(row.islandId(), k -> new HashMap<>()).merge(row.achievement(), row.tier(), Math::max);
        }
        Set<UUID> profiles = new LinkedHashSet<>(byProfile.keySet());
        for (Map.Entry<UUID, UUID> e : memberIsland.entrySet()) {
            if (byIsland.containsKey(e.getValue())) {
                profiles.add(e.getKey());
            }
        }
        List<Entry> out = new ArrayList<>();
        for (UUID profile : profiles) {
            UUID owner = owners.get(profile);
            if (owner == null) {
                continue;
            }
            Map<String, Integer> mine = byProfile.getOrDefault(profile, Map.of());
            int tiers = 0;
            int completed = 0;
            List<String> titles = new ArrayList<>();
            for (AchievementDef def : registry.all()) {
                int u = Math.min(def.tiers(), mine.getOrDefault(def.id(), 0));
                tiers += u;
                if (u >= def.tiers()) {
                    completed++;
                    if (def.hasTitle() && !titles.contains(def.title().id())) {
                        titles.add(def.title().id());
                    }
                }
            }
            int points = BoostMath.personalPoints(registry, mine, perTier).values().stream().mapToInt(Integer::intValue).sum();
            UUID island = memberIsland.get(profile);
            Map<String, Integer> islandMap = island == null ? Map.of() : byIsland.getOrDefault(island, Map.of());
            boolean team = island != null && teamIslands.contains(island);
            int islandTiers = 0;
            for (IslandAchievementDef def : registry.island()) {
                if (!IslandProgression.applies(def, team)) {
                    continue;
                }
                int u = Math.min(def.tiers(), islandMap.getOrDefault(def.id(), 0));
                islandTiers += u;
            }
            int islandPoints = BoostMath.islandPoints(registry, islandMap, perTier);
            if (tiers == 0 && islandTiers == 0) {
                continue;
            }
            out.add(new Entry(season, profile, owner, tiers, points, completed, islandTiers, islandPoints, titles));
        }
        return out;
    }
}
