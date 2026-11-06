package net.skybornmc.skyblock.islandtools;

import java.util.List;
import java.util.Locale;

/**
 * Owner 3 Nov 2026: extra drops, Smelter and shards only on blocks that belong to the tool ("not on every block"):
 * {@code islandtools.yml relevant.<kind>} lists block ids, with {@code *} at the start or end as a wildcard
 * ({@code *_ore}, {@code *_log}). Pure.
 */
final class Relevant {

    private Relevant() {
    }

    /** True when {@code blockId} (like {@code iron_ore}) matches one of {@code patterns}. */
    static boolean matches(List<String> patterns, String blockId) {
        if (patterns == null || blockId == null) {
            return false;
        }
        String id = blockId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        for (String raw : patterns) {
            String p = raw.strip().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (p.isEmpty()) {
                continue;
            }
            boolean start = p.startsWith("*");
            boolean end = p.endsWith("*") && p.length() > 1;
            String core = p.substring(start ? 1 : 0, end ? p.length() - 1 : p.length());
            if (start && end ? id.contains(core) : start ? id.endsWith(core) : end ? id.startsWith(core) : id.equals(core)) {
                return true;
            }
        }
        return false;
    }
}
