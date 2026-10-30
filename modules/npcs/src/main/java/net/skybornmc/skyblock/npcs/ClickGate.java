package net.skybornmc.skyblock.npcs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which entity clicks on NPCs and holograms act (UX3). Pure logic, main thread.
 *
 * <p>Root cause of "only left-click works" (owner, 31 Oct): since the 26.x protocol the client sends one interact packet
 * per hand that carries the hit position, and Paper fires only {@code PlayerInteractAtEntityEvent} for it, no plain
 * {@code PlayerInteractEntityEvent} follows. The old listener skipped the "at" event and waited for the plain one, so a
 * right-click never acted. Now every interact of the main hand acts; the off-hand copy of the same click is ignored and
 * the per-player cooldown removes any second packet (older clients/servers send "at" + plain, Bedrock taps can arrive as
 * a hit and an interact).</p>
 */
final class ClickGate {

    private final Map<UUID, Long> last = new HashMap<>();

    /** True if an interact (right-click / long-press) with {@code mainHand} may act; the off-hand copy never does. */
    static boolean interactActs(boolean mainHand) {
        return mainHand;
    }

    /** True (and remembered) if {@code player} may click now: at least {@code cooldownMs} after the last accepted click. */
    boolean pass(UUID player, long now, long cooldownMs) {
        Long previous = last.get(player);
        if (previous != null && now - previous < cooldownMs && now >= previous) {
            return false;
        }
        last.put(player, now);
        return true;
    }

    void forget(UUID player) {
        last.remove(player);
    }
}
