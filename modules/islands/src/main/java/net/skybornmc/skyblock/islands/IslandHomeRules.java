package net.skybornmc.skyblock.islands;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared island homes (PHASE-5 M5.5 Homestead, owner P5-21) without Bukkit: names and the limit. The island home is the
 * first, free home; named homes come on top, one per Homestead rank ({@code IslandService#extraIslandHomes}).
 */
final class IslandHomeRules {

    static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,16}");

    private IslandHomeRules() {
    }

    /** What {@code /is sethome <name>} may do. */
    enum SetResult {
        /** A new named home (a free slot). */
        NEW,
        /** An existing home moves (never needs a free slot). */
        MOVE,
        /** No free slot: every allowed named home is used (or none is allowed without Homestead). */
        LIMIT,
        /** Not a valid name. */
        BAD_NAME
    }

    /** The stored form of a typed name (trimmed, lowercase), or null when it is not a valid name. */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String name = typed.trim().toLowerCase(Locale.ROOT);
        return NAME.matcher(name).matches() ? name : null;
    }

    /**
     * @param existing the island's named homes now
     * @param name     the normalized name (null = invalid)
     * @param allowed  named homes the island may have ({@code extraIslandHomes}: Homestead ranks)
     */
    static SetResult set(Set<String> existing, String name, int allowed) {
        if (name == null) {
            return SetResult.BAD_NAME;
        }
        if (existing.contains(name)) {
            return SetResult.MOVE;
        }
        return existing.size() < Math.max(0, allowed) ? SetResult.NEW : SetResult.LIMIT;
    }

    /** Homes the island has in total, the island home included (shown as "2/3 homes"). */
    static int total(int named) {
        return 1 + Math.max(0, named);
    }
}
