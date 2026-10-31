package net.skybornmc.skyblock.depths.combat;

/**
 * The phase of one mini-boss fight (M8.2): {@link Phase#NORMAL} until its health share falls to the {@code below}
 * of its {@code enrage} ability, then {@link Phase#ENRAGED} for the rest of the fight (never back, also when the health
 * scaling of R3-10 raises its max health, because the share left is kept). A mini-boss without {@code enrage} stays
 * normal. The boss bar and the zone's warning line follow this; the enrage effect itself is the ability ({@link
 * MobAbilities}), which uses the same rule. Pure; main thread only.
 */
public final class MiniBossPhases {

    /** The phases of a mini-boss fight, in order. */
    public enum Phase {
        NORMAL, ENRAGED
    }

    private final double enrageBelow;
    private Phase phase = Phase.NORMAL;

    /** @param enrageBelow health share (0-1) below which it enrages; NaN or out of range = never */
    public MiniBossPhases(double enrageBelow) {
        this.enrageBelow = enrageBelow > 0 && enrageBelow < 1 ? enrageBelow : Double.NaN;
    }

    /** A fight that never changes phase. */
    public static MiniBossPhases none() {
        return new MiniBossPhases(Double.NaN);
    }

    public Phase phase() {
        return phase;
    }

    public boolean enraged() {
        return phase == Phase.ENRAGED;
    }

    /** True if this fight has an enrage phase at all. */
    public boolean hasEnrage() {
        return !Double.isNaN(enrageBelow);
    }

    /**
     * Updates the phase after a hit.
     *
     * @param healthAfter health left after the hit (0 or less = dead: no phase change, the fight is over)
     * @param maxHealth   current max health (scaled)
     * @return the new phase if it changed with this hit, otherwise {@code null}
     */
    public Phase update(double healthAfter, double maxHealth) {
        if (phase == Phase.ENRAGED || !hasEnrage() || !(maxHealth > 0) || !(healthAfter > 0)) {
            return null;
        }
        if (healthAfter / maxHealth <= enrageBelow) { // same rule as MobCombatMath.enrageDue (the effect itself)
            phase = Phase.ENRAGED;
            return phase;
        }
        return null;
    }
}
