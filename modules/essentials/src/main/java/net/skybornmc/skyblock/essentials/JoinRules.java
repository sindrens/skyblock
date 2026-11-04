package net.skybornmc.skyblock.essentials;

import java.util.Locale;

/** Where a joining player appears ({@code essentials.yml → join.location}, owner 4 Nov 2026). Pure, unit-tested. */
final class JoinRules {

    static final String ISLAND = "island";
    static final String LAST = "last";
    static final String SPAWN = "spawn";

    /** The decision for one join. */
    enum Where { ISLAND, LAST, SPAWN }

    private JoinRules() {
    }

    /**
     * New players always start at spawn (the start flow creates their island and brings them there). Returning players
     * follow the setting; an unknown value counts as {@code island} (the owner's default).
     */
    static Where where(String setting, boolean first) {
        if (first) {
            return Where.SPAWN;
        }
        String s = setting == null ? "" : setting.strip().toLowerCase(Locale.ROOT);
        return switch (s) {
            case LAST -> Where.LAST;
            case SPAWN -> Where.SPAWN;
            default -> Where.ISLAND;
        };
    }
}
