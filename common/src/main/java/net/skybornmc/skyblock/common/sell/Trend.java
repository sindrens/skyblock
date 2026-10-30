package net.skybornmc.skyblock.common.sell;

import java.util.Locale;

/**
 * Which way a sell price moved over the last hour (M3.4). Shown as a word and a color, not as an arrow: the arrows
 * from the phase doc are not on the Bedrock-safe symbol list ({@code style.yml → bedrock-safe-symbols}).
 */
public enum Trend {
    RISING,
    FALLING,
    STEADY;

    /** Lowercase key for messages ({@code sell.trend.<key>}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
