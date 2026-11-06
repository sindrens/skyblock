package net.skybornmc.skyblock.essentials;

import java.util.Locale;

/** Pure rules of the start flow ({@link StartGuide}); unit-tested without a server. */
final class StartRules {

    /** Account setting that remembers that the player talked to the guide (any value but empty = met). */
    static final String MET_KEY = "start.met-guide";

    private StartRules() {
    }

    /**
     * Should an island be created for this player right after joining?
     *
     * @param first          the account's first join
     * @param hasIsland      the active profile is already a member of an island
     * @param onFirstJoin    {@code start.island-on-first-join}
     * @param forIslandless  {@code start.island-for-islandless}
     * @param playing        survival or adventure (staff in creative/spectator are left alone)
     */
    static boolean createIsland(boolean first, boolean hasIsland, boolean onFirstJoin, boolean forIslandless, boolean playing) {
        if (hasIsland || !playing) {
            return false;
        }
        return first ? onFirstJoin || forIslandless : forIslandless;
    }

    /** The boss bar shows while the guide is on and the player has not met the guide yet. */
    static boolean showGuide(boolean enabled, String metValue) {
        return enabled && !met(metValue);
    }

    static boolean met(String metValue) {
        return metValue != null && !metValue.isBlank();
    }

    /** True when an NPC role is the guide's role (case and blanks ignored). */
    static boolean guideRole(String role, String configured) {
        return role != null && configured != null && !configured.isBlank()
                && role.strip().toLowerCase(Locale.ROOT).equals(configured.strip().toLowerCase(Locale.ROOT));
    }

    /** A configured command without a leading slash, or empty. */
    static String command(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        return s.startsWith("/") ? s.substring(1).strip() : s;
    }
}
