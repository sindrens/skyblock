package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The profile/account rule (owner decision PR1, DECISIONS_2026-10-30): progress and unlocks belong to one profile; a
 * reward title is granted to the <b>account</b> ({@link ProfileProgress#owner()}) once any of its profiles completes the
 * achievement. Each tier unlocks once per profile. Pure (unit-tested).
 */
final class Progression {

    /**
     * One tier reached.
     *
     * @param last  the final tier (the achievement is complete)
     * @param title the reward title for the account, or null (only on the last tier)
     */
    record Unlock(UUID profileKey, UUID owner, AchievementDef def, int tier, boolean last, String title) {
    }

    private Progression() {
    }

    /** Adds to a metric and returns the tiers it unlocked (none while the profile is still loading). */
    static List<Unlock> apply(ProfileProgress progress, AchievementRegistry registry, Metric metric, long amount) {
        if (!progress.apply(metric, amount)) {
            return List.of();
        }
        return evaluate(progress, registry, metric);
    }

    /** Checks every achievement of one metric. */
    static List<Unlock> evaluate(ProfileProgress progress, AchievementRegistry registry, Metric metric) {
        if (!progress.loaded()) {
            return List.of();
        }
        List<Unlock> out = new ArrayList<>();
        for (AchievementDef def : registry.byMetric(metric)) {
            check(progress, def, out);
        }
        return out;
    }

    /** Checks every achievement (after loading, or after a reload changed the goals). */
    static List<Unlock> evaluateAll(ProfileProgress progress, AchievementRegistry registry) {
        if (!progress.loaded()) {
            return List.of();
        }
        List<Unlock> out = new ArrayList<>();
        for (AchievementDef def : registry.all()) {
            check(progress, def, out);
        }
        return out;
    }

    private static void check(ProfileProgress progress, AchievementDef def, List<Unlock> out) {
        List<Integer> tiers = ProgressMath.newTiers(def, progress.value(def.metric()), progress.unlocked(def.id()));
        if (tiers.isEmpty()) {
            return;
        }
        for (int tier : tiers) {
            boolean last = tier == def.tiers();
            out.add(new Unlock(progress.key(), progress.owner(), def, tier, last, last && def.hasTitle() ? def.title().id() : null));
        }
        progress.markUnlocked(def.id(), tiers.getLast());
    }

    /** Achievements of the registry this profile completed (every tier). */
    static int completed(ProfileProgress progress, AchievementRegistry registry) {
        int done = 0;
        for (AchievementDef def : registry.all()) {
            if (progress.unlocked(def.id()) >= def.tiers()) {
                done++;
            }
        }
        return done;
    }

    /** Tiers unlocked (of achievements that still exist) and all tiers, for the summary line. */
    static int[] tierCounts(ProfileProgress progress, AchievementRegistry registry) {
        int done = 0;
        int total = 0;
        for (AchievementDef def : registry.all()) {
            done += Math.min(def.tiers(), progress.unlocked(def.id()));
            total += def.tiers();
        }
        return new int[]{done, total};
    }
}
