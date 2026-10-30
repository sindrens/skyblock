package net.skybornmc.skyblock.achievements;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Team Flame (AC3): members of one island who played at least N minutes on the same day. Alts on the same IP count once
 * (RANKS_TITLES_ACHIEVEMENTS §C4 anti-abuse). In memory, one day at a time (a restart starts the day's minutes again,
 * which can only delay the count, never inflate it). Pure, unit-tested; main thread.
 */
final class TeamActivity {

    private long day = Long.MIN_VALUE;
    private final Map<UUID, Map<UUID, Integer>> minutes = new HashMap<>();
    private final Map<UUID, Set<String>> active = new HashMap<>();

    /**
     * Counts one minute of a member.
     *
     * @param ipKey the member's address (alts share it); null = counted on its own
     * @return the island's active count today when this minute changed it, otherwise -1
     */
    int minute(long epochDay, UUID islandId, UUID profileKey, String ipKey, int threshold) {
        if (epochDay != day) {
            day = epochDay;
            minutes.clear();
            active.clear();
        }
        int m = minutes.computeIfAbsent(islandId, id -> new HashMap<>()).merge(profileKey, 1, Integer::sum);
        if (m != Math.max(1, threshold)) {
            return -1;
        }
        Set<String> ips = active.computeIfAbsent(islandId, id -> new HashSet<>());
        String key = ipKey == null || ipKey.isBlank() ? "profile:" + profileKey : "ip:" + ipKey;
        return ips.add(key) ? ips.size() : -1;
    }

    /** Active members (distinct IPs) of the island today. */
    int activeToday(UUID islandId) {
        Set<String> ips = active.get(islandId);
        return ips == null ? 0 : ips.size();
    }

    void clear() {
        minutes.clear();
        active.clear();
    }
}
