package net.skybornmc.skyblock.crates;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The pure rules of the crate yard ambience (unit-tested): which rarities get particles and when a player's walk up to a
 * crate counts as a new approach (chime once, with a cooldown, and only again after they left).
 */
final class CrateAmbient {

    private CrateAmbient() {
    }

    /** True when {@code rarity} is {@code min} or rarer (order of {@link CrateCatalog#RARITIES}; unknown = no). */
    static boolean atLeast(String rarity, String min) {
        int r = CrateCatalog.RARITIES.indexOf(rarity == null ? "" : rarity.toLowerCase(Locale.ROOT));
        int m = CrateCatalog.RARITIES.indexOf(min == null ? "" : min.trim().toLowerCase(Locale.ROOT));
        return r >= 0 && m >= 0 && r >= m;
    }

    /** Names the rarities in order, for the settings text. */
    static List<String> rarities() {
        return CrateCatalog.RARITIES;
    }

    /**
     * Per player and crate: inside the approach range or not. Coming within {@code near} blocks fires once; the player
     * must go beyond {@code far} blocks before it can fire again, and never twice within the cooldown. Main thread only.
     */
    static final class Approach {
        private final double nearSq;
        private final double farSq;
        private final long cooldownMs;
        private final Map<UUID, Map<String, State>> states = new HashMap<>();

        private static final class State {
            boolean inside;
            long last = Long.MIN_VALUE;
        }

        Approach(double near, double far, long cooldownMs) {
            this.nearSq = near * near;
            this.farSq = Math.max(far, near) * Math.max(far, near);
            this.cooldownMs = Math.max(0, cooldownMs);
        }

        /** Feeds one distance (squared, blocks) at {@code nowMs}; true when the chime should play now. */
        boolean update(UUID player, String crate, double distanceSq, long nowMs) {
            State s = states.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(crate, k -> new State());
            if (s.inside) {
                if (distanceSq > farSq) {
                    s.inside = false;
                }
                return false;
            }
            if (distanceSq > nearSq) {
                return false;
            }
            s.inside = true;
            if (s.last != Long.MIN_VALUE && nowMs - s.last < cooldownMs) {
                return false;
            }
            s.last = nowMs;
            return true;
        }

        void forget(UUID player) {
            states.remove(player);
        }

        void clear() {
            states.clear();
        }
    }
}
