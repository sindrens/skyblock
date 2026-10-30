package net.skybornmc.skyblock.worlds.protection;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Who bypasses protection flags in a world (pure, unit-tested): the general permission
 * ({@code protection.bypass-permission}) or the world's own one ({@code protection.world-bypass-permissions},
 * {@code world=permission}), and by default only in creative mode ({@code protection.bypass-needs-creative}), so a
 * builder walking around in survival never breaks something by accident. Build-mode hooks (essentials {@code /build})
 * are checked by the service on top of this.
 */
public final class BypassRule {

    private final String general;
    private final Map<String, String> perWorld;
    private final boolean needsCreative;

    public BypassRule(String general, Map<String, String> perWorld, boolean needsCreative) {
        this.general = general == null ? "" : general.trim();
        this.perWorld = Map.copyOf(perWorld);
        this.needsCreative = needsCreative;
    }

    /** Parses {@code world=permission} lines; broken lines go to {@code warn}. */
    public static BypassRule parse(String general, List<String> worldLines, boolean needsCreative, Consumer<String> warn) {
        Map<String, String> map = new HashMap<>();
        for (String line : worldLines) {
            int eq = line == null ? -1 : line.indexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                warn.accept("protection.world-bypass-permissions: '" + line + "' is not 'world=permission'");
                continue;
            }
            map.put(line.substring(0, eq).trim().toLowerCase(Locale.ROOT), line.substring(eq + 1).trim());
        }
        return new BypassRule(general, map, needsCreative);
    }

    /** True if a player with these permissions, in this game mode, bypasses protection in {@code world}. */
    public boolean bypasses(String world, boolean creative, Predicate<String> hasPermission) {
        if (needsCreative && !creative) {
            return false;
        }
        return mayBypass(world, hasPermission);
    }

    /** True if the player has a bypass permission for the world (game mode not checked; for hints). */
    public boolean mayBypass(String world, Predicate<String> hasPermission) {
        if (!general.isEmpty() && hasPermission.test(general)) {
            return true;
        }
        String own = perWorld.get(world.toLowerCase(Locale.ROOT));
        return own != null && !own.isEmpty() && hasPermission.test(own);
    }

    public boolean needsCreative() {
        return needsCreative;
    }
}
