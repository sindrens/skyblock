package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One island's pillars in memory: total XP and level per pillar, and the synergy milestones reached. XP only grows
 * (levels never go down within a season). Main thread only; unit-tested.
 */
final class IslandPillars {

    /** A level change from one gain. */
    record LevelChange(Pillar pillar, int from, int to) {
    }

    private final double[] xp = new double[Pillar.values().length];
    private final int[] level = new int[Pillar.values().length];
    private final Set<Integer> milestones = new TreeSet<>();

    double xp(Pillar pillar) {
        return xp[pillar.ordinal()];
    }

    int level(Pillar pillar) {
        return level[pillar.ordinal()];
    }

    Map<Pillar, Integer> levels() {
        Map<Pillar, Integer> out = new EnumMap<>(Pillar.class);
        for (Pillar p : Pillar.values()) {
            out.put(p, level[p.ordinal()]);
        }
        return out;
    }

    Set<Integer> milestones() {
        return milestones;
    }

    /**
     * Adds XP (ignored when not positive or not finite) and recomputes the level.
     *
     * @return the level change, or {@code null} if the level stayed
     */
    LevelChange add(Pillar pillar, double amount, PillarCurve curve) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return null;
        }
        int i = pillar.ordinal();
        xp[i] += amount;
        int before = level[i];
        int after = Math.max(before, curve.levelFor(xp[i]));
        level[i] = after;
        return after > before ? new LevelChange(pillar, before, after) : null;
    }

    /**
     * Adds stored XP and the stored level (load) without a level-up event; the level becomes the higher of the stored
     * level and what the XP gives (levels never go down within a season, even after a curve change).
     */
    void addStored(Pillar pillar, double amount, int storedLevel, PillarCurve curve) {
        int i = pillar.ordinal();
        if (amount > 0 && Double.isFinite(amount)) {
            xp[i] += amount;
        }
        level[i] = Math.max(Math.max(level[i], Math.max(0, storedLevel)), curve.levelFor(xp[i]));
    }

    /** Recomputes the levels after a curve change in a reload: they can rise, never fall (season rule). */
    void relevel(PillarCurve curve) {
        for (Pillar p : Pillar.values()) {
            level[p.ordinal()] = Math.max(level[p.ordinal()], curve.levelFor(xp[p.ordinal()]));
        }
    }
}
