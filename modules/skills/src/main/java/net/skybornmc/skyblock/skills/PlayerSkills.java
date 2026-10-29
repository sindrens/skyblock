package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One online player's skill XP (cache entry). Main-thread writes; reads from other threads (placeholders) may see a
 * value one action old, which is harmless.
 *
 * <p><b>Loaded vs. not loaded:</b> normally the stored XP is loaded before join. If it is not (rare: a quick rejoin
 * while the previous session was still saving, or a database hiccup), gains are kept as <i>deltas</i> on top of
 * nothing and {@link #merge} adds them to the stored values once they arrive, so nothing earned is lost or
 * overwritten. Levels are only computed while loaded.</p>
 */
final class PlayerSkills {

    private static final int N = SkillType.values().length;

    /** A level change caused by a gain or merge. */
    record LevelChange(SkillType skill, int from, int to) {
    }

    /** One row to persist: absolute total XP and level. */
    record Row(UUID player, SkillType skill, double xp, int level) {
    }

    private final UUID uuid;
    private final double[] xp = new double[N];
    private final int[] level = new int[N];
    private final boolean[] dirty = new boolean[N];
    private volatile boolean loaded;
    private boolean loading;

    PlayerSkills(UUID uuid) {
        this.uuid = uuid;
    }

    /** A loaded entry with stored totals. */
    static PlayerSkills loaded(UUID uuid, Map<SkillType, Double> stored, SkillCurve curve) {
        PlayerSkills p = new PlayerSkills(uuid);
        p.merge(stored, curve);
        return p;
    }

    UUID uuid() {
        return uuid;
    }

    boolean isLoaded() {
        return loaded;
    }

    boolean isLoading() {
        return loading;
    }

    void loading(boolean loading) {
        this.loading = loading;
    }

    /** Total XP (or the delta so far while not loaded). */
    double xp(SkillType skill) {
        return xp[skill.ordinal()];
    }

    /** Level (0 while not loaded). */
    int level(SkillType skill) {
        return loaded ? level[skill.ordinal()] : 0;
    }

    /**
     * Adds XP. Returns the level change, or {@code null} if the level stayed the same (always {@code null} while
     * not loaded).
     */
    LevelChange add(SkillType skill, double amount, SkillCurve curve) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return null;
        }
        int i = skill.ordinal();
        xp[i] += amount;
        if (!loaded) {
            return null;
        }
        dirty[i] = true;
        int before = level[i];
        level[i] = curve.levelFor(xp[i]);
        return level[i] != before ? new LevelChange(skill, before, level[i]) : null;
    }

    /**
     * Sets a total (staff). Only while loaded. Returns the level change or {@code null}; levels can go down here.
     */
    LevelChange set(SkillType skill, double total, SkillCurve curve) {
        if (!loaded || !(total >= 0) || Double.isInfinite(total)) {
            return null;
        }
        int i = skill.ordinal();
        xp[i] = total;
        dirty[i] = true;
        int before = level[i];
        level[i] = curve.levelFor(total);
        return level[i] != before ? new LevelChange(skill, before, level[i]) : null;
    }

    /**
     * Applies the stored totals: stored + deltas gained while not loaded. Skills with a delta become dirty. Returns
     * level changes caused by the deltas (stored level → new level). Calling it on a loaded entry does nothing.
     */
    List<LevelChange> merge(Map<SkillType, Double> stored, SkillCurve curve) {
        List<LevelChange> changes = new ArrayList<>();
        if (loaded) {
            return changes;
        }
        for (SkillType skill : SkillType.values()) {
            int i = skill.ordinal();
            double base = Math.max(0, stored.getOrDefault(skill, 0.0));
            double delta = xp[i];
            xp[i] = base + delta;
            level[i] = curve.levelFor(xp[i]);
            if (delta > 0) {
                dirty[i] = true;
                int from = curve.levelFor(base);
                if (from != level[i]) {
                    changes.add(new LevelChange(skill, from, level[i]));
                }
            }
        }
        loaded = true;
        loading = false;
        return changes;
    }

    /** Recomputes levels after a curve change (no events). Changed levels become dirty so the stored level follows. */
    void recompute(SkillCurve curve) {
        if (!loaded) {
            return;
        }
        for (int i = 0; i < N; i++) {
            int now = curve.levelFor(xp[i]);
            if (now != level[i]) {
                level[i] = now;
                dirty[i] = true;
            }
        }
    }

    boolean hasDirty() {
        for (boolean d : dirty) {
            if (d) {
                return true;
            }
        }
        return false;
    }

    /** Rows to save (absolute values) and clears the dirty flags. Empty while not loaded. */
    List<Row> drainDirty() {
        List<Row> rows = new ArrayList<>();
        if (!loaded) {
            return rows;
        }
        for (SkillType skill : SkillType.values()) {
            int i = skill.ordinal();
            if (dirty[i]) {
                rows.add(new Row(uuid, skill, xp[i], level[i]));
                dirty[i] = false;
            }
        }
        return rows;
    }

    /** Marks rows dirty again after a failed save. */
    void markDirty(List<Row> rows) {
        for (Row row : rows) {
            dirty[row.skill().ordinal()] = true;
        }
    }

    /** XP gained while not loaded (skills with a positive delta only). */
    Map<SkillType, Double> deltas() {
        Map<SkillType, Double> out = new EnumMap<>(SkillType.class);
        if (loaded) {
            return out;
        }
        for (SkillType skill : SkillType.values()) {
            if (xp[skill.ordinal()] > 0) {
                out.put(skill, xp[skill.ordinal()]);
            }
        }
        return out;
    }

    /** Totals of every skill. */
    Map<SkillType, Double> totals() {
        Map<SkillType, Double> out = new EnumMap<>(SkillType.class);
        for (SkillType skill : SkillType.values()) {
            out.put(skill, xp[skill.ordinal()]);
        }
        return out;
    }
}
