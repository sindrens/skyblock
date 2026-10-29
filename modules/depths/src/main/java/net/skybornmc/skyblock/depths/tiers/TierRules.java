package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.depths.zones.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Unlock rules of the Depths tiers (M4.6), free of Bukkit so they are unit-tested. A tier is open when the player's
 * level in its wing's skill is at least the zone's {@code required-level} ({@code zones.yml}); see also
 * {@link net.skybornmc.skyblock.depths.zones.ZoneAccess}.
 */
public final class TierRules {

    private TierRules() {
    }

    /** True if {@code level} opens a zone that needs {@code required}. */
    public static boolean unlocked(int level, int required) {
        return level >= required;
    }

    /**
     * Zones of {@code skill}'s wing opened by a level-up from {@code oldLevel} to {@code newLevel}
     * ({@code oldLevel < required <= newLevel}), in the given order. Empty for a level that went down or stayed.
     */
    public static List<Zone> newlyUnlocked(List<Zone> zones, SkillType skill, int oldLevel, int newLevel) {
        List<Zone> out = new ArrayList<>();
        if (newLevel <= oldLevel) {
            return out;
        }
        for (Zone zone : zones) {
            if (zone.aspect().skill() == skill && zone.requiredLevel() > oldLevel && zone.requiredLevel() <= newLevel) {
                out.add(zone);
            }
        }
        return out;
    }

    /** Levels still missing (0 when open). */
    public static int missing(int level, int required) {
        return Math.max(0, required - level);
    }

    /**
     * Filled segments of a {@code length}-segment progress bar toward {@code required} (level 9 of 15 with 10 segments
     * → 6). Full when open, empty for negative levels; never rounds a locked tier up to a full bar.
     */
    public static int filled(int level, int required, int length) {
        if (length <= 0) {
            return 0;
        }
        if (required <= 0 || level >= required) {
            return length;
        }
        if (level <= 0) {
            return 0;
        }
        int segments = (int) Math.floor((double) level * length / required);
        return Math.min(length - 1, Math.max(0, segments));
    }

    /** The highest open tier of a wing (zones ordered by tier), or {@code null} if none is open. */
    public static Zone highestUnlocked(List<Zone> wing, int level) {
        Zone best = null;
        for (Zone zone : wing) {
            if (unlocked(level, zone.requiredLevel()) && (best == null || zone.tier() > best.tier())) {
                best = zone;
            }
        }
        return best;
    }

    /** The lowest locked tier of a wing (the next goal), or {@code null} if all are open. */
    public static Zone nextLocked(List<Zone> wing, int level) {
        Zone next = null;
        for (Zone zone : wing) {
            if (!unlocked(level, zone.requiredLevel()) && (next == null || zone.tier() < next.tier())) {
                next = zone;
            }
        }
        return next;
    }
}
