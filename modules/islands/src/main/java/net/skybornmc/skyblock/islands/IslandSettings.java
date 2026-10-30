package net.skybornmc.skyblock.islands;

/**
 * The {@code /is settings} values an island changed (ISL2; main thread only, pure). {@code null} = follow the server
 * default ({@link FlagDefaults}), so a changed default reaches every island that never touched that setting. Stored as
 * rows in {@code island_settings}. Reads never allocate (protection checks run per event).
 */
public final class IslandSettings {

    private final String[] values = new String[IslandFlag.cached().length];

    /** The island's own value, or null when it follows the default. */
    public String override(IslandFlag flag) {
        return values[flag.ordinal()];
    }

    /** Sets a valid value, or clears it ({@code null}, or a value the flag doesn't accept). Virtual flags are never stored. */
    public void set(IslandFlag flag, String value) {
        if (flag.virtual()) {
            return;
        }
        values[flag.ordinal()] = value == null ? null : flag.parse(value).orElse(null);
    }

    /** The effective value: the island's own, else the default. */
    public String value(IslandFlag flag, FlagDefaults defaults) {
        String own = values[flag.ordinal()];
        return own != null ? own : defaults.value(flag);
    }

    /** True if a toggle is on (own value, else the default). */
    public boolean on(IslandFlag flag, FlagDefaults defaults) {
        return IslandFlag.ON.equals(value(flag, defaults));
    }

    public boolean isEmpty() {
        for (String value : values) {
            if (value != null) {
                return false;
            }
        }
        return true;
    }
}
