package net.skybornmc.skyblock.common.teleport;

/**
 * Pure rules of the teleport warmup (unit-tested): when a teleport may start, and what cancels a running warmup.
 *
 * @param cancelOnMove   moving further than {@code moveDistance} blocks from the start spot cancels
 * @param moveDistance   allowed distance (blocks, 3D) before a move cancels
 * @param cancelOnDamage taking damage cancels
 * @param cancelOnCombat getting a new combat tag (dealing or taking combat damage) cancels
 * @param blockInCombat  a warmup teleport can't start while combat-tagged (bypass permission skips it)
 */
public record TeleportRules(boolean cancelOnMove, double moveDistance, boolean cancelOnDamage, boolean cancelOnCombat,
                            boolean blockInCombat) {

    /** Defaults of {@code teleport.yml}. */
    public static final TeleportRules DEFAULTS = new TeleportRules(true, 1.0, true, true, false);

    /** Result of {@link #start}: refused (with a reason), instant or a warmup. */
    public enum Start { FROZEN, COOLDOWN, IN_COMBAT, INSTANT, WARMUP }

    /**
     * Whether a requested teleport may start.
     *
     * @param warmupSeconds      the reason's warmup
     * @param bypass             the player has the reason's bypass permission
     * @param cooldownLeftMillis cooldown left for this reason
     * @param frozen             the player is frozen by staff
     * @param combatLeftMillis   combat tag left
     */
    public Start start(int warmupSeconds, boolean bypass, long cooldownLeftMillis, boolean frozen, long combatLeftMillis) {
        if (frozen) {
            return Start.FROZEN;
        }
        if (bypass) {
            return Start.INSTANT;
        }
        if (cooldownLeftMillis > 0) {
            return Start.COOLDOWN;
        }
        if (warmupSeconds <= 0) {
            return Start.INSTANT;
        }
        if (blockInCombat && combatLeftMillis > 0) {
            return Start.IN_COMBAT;
        }
        return Start.WARMUP;
    }
}
