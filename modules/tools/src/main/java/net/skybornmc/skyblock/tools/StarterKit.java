package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Starter kit rules (owner decision): the Quartermaster gives a basic pickaxe, axe, hoe, sword and rod, re-claimable
 * every 24 h, and only the pieces you don't have (any custom tool of that type in your inventory or ender chest counts).
 */
final class StarterKit {

    private StarterKit() {
    }

    /** Milliseconds until the next claim (0 = can claim now). {@code lastClaim == null} = never claimed. */
    static long remainingMillis(Long lastClaim, long now, long cooldownMillis) {
        if (lastClaim == null || cooldownMillis <= 0) {
            return 0;
        }
        long next = lastClaim + cooldownMillis;
        return Math.max(0, next - now);
    }

    /** The kit pieces the player doesn't have, in kit order. */
    static List<ToolKind> missing(Collection<ToolKind> kit, Collection<ToolKind> have) {
        List<ToolKind> out = new ArrayList<>();
        for (ToolKind kind : kit) {
            if (!have.contains(kind) && !out.contains(kind)) {
                out.add(kind);
            }
        }
        return out;
    }
}
