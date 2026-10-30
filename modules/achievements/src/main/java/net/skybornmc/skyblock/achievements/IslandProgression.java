package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Island achievement rules (AC3, pure, unit-tested): shared progress per island, each tier unlocks once per island,
 * Team Flame style achievements only on team islands, and the title rule (members only, after a minimum time on the
 * island, owner AC7).
 */
final class IslandProgression {

    /**
     * One island tier reached.
     *
     * @param title the reward title for every eligible member, or null (only on the last tier)
     */
    record Unlock(UUID islandId, IslandAchievementDef def, int tier, boolean last, String title) {
    }

    private IslandProgression() {
    }

    /** Adds to a metric and returns the tiers it unlocked (none before the island's data loaded). */
    static List<Unlock> apply(IslandProgress progress, AchievementRegistry registry, IslandMetric metric, long amount, boolean team) {
        if (!progress.apply(metric, amount)) {
            return List.of();
        }
        return evaluate(progress, registry, metric, team);
    }

    static List<Unlock> evaluate(IslandProgress progress, AchievementRegistry registry, IslandMetric metric, boolean team) {
        if (!progress.loaded()) {
            return List.of();
        }
        List<Unlock> out = new ArrayList<>();
        for (IslandAchievementDef def : registry.island()) {
            if (def.metric() == metric) {
                check(progress, def, team, out);
            }
        }
        return out;
    }

    static List<Unlock> evaluateAll(IslandProgress progress, AchievementRegistry registry, boolean team) {
        if (!progress.loaded()) {
            return List.of();
        }
        List<Unlock> out = new ArrayList<>();
        for (IslandAchievementDef def : registry.island()) {
            check(progress, def, team, out);
        }
        return out;
    }

    /** Whether an island achievement applies to this island (team-only ones need a team island). */
    static boolean applies(IslandAchievementDef def, boolean team) {
        return team || !def.teamOnly();
    }

    private static void check(IslandProgress progress, IslandAchievementDef def, boolean team, List<Unlock> out) {
        if (!applies(def, team)) {
            return;
        }
        List<Integer> tiers = ProgressMath.newTiers(def, progress.value(def.metric()), progress.unlocked(def.id()));
        if (tiers.isEmpty()) {
            return;
        }
        for (int tier : tiers) {
            boolean last = tier == def.tiers();
            out.add(new Unlock(progress.islandId(), def, tier, last, last && def.hasTitle() ? def.title().id() : null));
        }
        progress.markUnlocked(def.id(), tiers.getLast());
        // Points are recomputed by the caller (it has the configured points per tier).
    }

    /**
     * True when a member may get an island title now: a stored member (coop players and visitors have no join time) who
     * joined at least {@code minDays} ago.
     */
    static boolean titleEligible(OptionalLong memberSince, long now, double minDays) {
        if (memberSince.isEmpty()) {
            return false;
        }
        return now - memberSince.getAsLong() >= (long) (minDays * 86_400_000L);
    }

    /** Island tiers done (of achievements that still exist and apply) and all of them, for the summary line. */
    static int[] tierCounts(IslandProgress progress, AchievementRegistry registry, boolean team) {
        int done = 0;
        int total = 0;
        for (IslandAchievementDef def : registry.island()) {
            if (!applies(def, team)) {
                continue;
            }
            done += Math.min(def.tiers(), progress.unlocked(def.id()));
            total += def.tiers();
        }
        return new int[]{done, total};
    }
}
