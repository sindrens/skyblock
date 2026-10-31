package net.skybornmc.skyblock.quests;

/**
 * Numbers of the quest engine (targets, rewards, tiers, progress steps). Pure, unit-tested.
 * <ul>
 *   <li>Targets scale with the player so a daily takes ~10-15 minutes and a weekly ~45 (PHASE-10 M10.7): blocks, logs and
 *       kills = the regular's rate per played minute at that Depths tier (ECONOMY_BALANCE §3) x minutes; sell = REF(t) per
 *       minute x minutes x the sell share.</li>
 *   <li>Money = minutes of REF(t) at the player's stage tier (same table as crates and the daily calendar); crystals =
 *       base x growth^(tier-1) (crystal pouches, ECONOMY_BALANCE §9.1).</li>
 * </ul>
 */
final class QuestMath {

    /** Region ids of the Depths zones: {@code mine_t04_<field>} (ZoneLayout, Zone#regionId). */
    private static final java.util.regex.Pattern REGION = java.util.regex.Pattern.compile("^(mine|forest|combat)_t(\\d{1,3})(?:_.*)?$");

    private QuestMath() {
    }

    /** The tier of a Depths zone region id, or 0 when it is not a tier zone (config regions, events). */
    static int tierOf(String regionId) {
        if (regionId == null) {
            return 0;
        }
        java.util.regex.Matcher matcher = REGION.matcher(regionId);
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : 0;
    }

    /** The value at a 1-based tier, using the last entry for tiers past the table and the first below 1. */
    static double at(double[] table, int tier) {
        if (table.length == 0) {
            return 0;
        }
        return table[Math.max(0, Math.min(table.length - 1, tier - 1))];
    }

    static long at(long[] table, int tier) {
        if (table.length == 0) {
            return 0;
        }
        return table[Math.max(0, Math.min(table.length - 1, tier - 1))];
    }

    /**
     * Target of a pool entry.
     *
     * @param ratePerMinute blocks/logs/kills per played minute by Depths tier of that wing (mine, chop, kill)
     * @param refPerMinute  REF(t) per minute by stage tier (sell)
     * @param wingTier      the quest's Depths tier (mine, chop, kill)
     * @param stageTier     the player's stage tier (sell)
     */
    static long target(QuestPool.Entry entry, double[] ratePerMinute, long[] refPerMinute, int wingTier, int stageTier,
                       int minutes, double sellShare) {
        if (entry.fixed() > 0) {
            return entry.fixed();
        }
        double raw = switch (entry.type()) {
            case MINE, CHOP, KILL -> at(ratePerMinute, wingTier) * minutes;
            case SELL -> at(refPerMinute, stageTier) * (double) minutes * sellShare;
            default -> 1;
        };
        return Math.max(1, nice(Math.round(raw)));
    }

    /**
     * A round number close to {@code value}: up to 20 as is, then steps of 5 below 100 and two significant digits above
     * (1,523 → 1,500; 237 → 240; 87 → 85). Never below 1.
     */
    static long nice(long value) {
        if (value <= 20) {
            return Math.max(1, value);
        }
        if (value < 100) {
            return Math.round(value / 5.0) * 5;
        }
        long step = 1;
        long v = value;
        while (v >= 100) {
            v /= 10;
            step *= 10;
        }
        return Math.round((double) value / step) * step;
    }

    /** Money reward: {@code minutes} of REF(t) per minute at the stage tier. */
    static long money(long[] refPerMinute, int stageTier, double minutes) {
        return Math.max(0, Math.round(at(refPerMinute, stageTier) * Math.max(0, minutes)));
    }

    /** Crystal reward: base x growth^(tier-1), rounded. */
    static long crystals(long base, int tier, double growth) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * Math.pow(Math.max(1, growth), Math.max(0, tier - 1))));
    }

    /** Tier (1-based) for a skill level: the number of unlock levels at or below it (levels sorted ascending). */
    static int tierFor(int level, int[] tierLevels) {
        int tier = 0;
        for (int need : tierLevels) {
            if (level >= need) {
                tier++;
            }
        }
        return Math.max(1, tier);
    }

    /** The quest tier of a Depths wing: the best open tier minus {@code offset}, at least 1. */
    static int wingTier(int unlockedTiers, int offset) {
        return Math.max(1, unlockedTiers - Math.max(0, offset));
    }

    /**
     * The progress step (25, 50 or 75 with {@code step} 25) crossed by going from {@code before} to {@code after}, or 0
     * when none was crossed. 100% is the completion, never a step. With several steps crossed at once the highest wins.
     */
    static int stepCrossed(long before, long after, long target, int step) {
        if (target <= 0 || step <= 0 || step >= 100 || after <= before || after >= target) {
            return 0;
        }
        int was = percent(before, target) / step;
        int now = percent(after, target) / step;
        return now > was ? now * step : 0;
    }

    /** Whole percent 0-100. */
    static int percent(long value, long target) {
        if (target <= 0) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, Math.floor(value * 100.0 / target)));
    }

    /** True when {@code part} is at least {@code minPercent} of {@code total} (a helper of an island mission). */
    static boolean helped(long part, long total, double minPercent) {
        if (part <= 0) {
            return false;
        }
        if (minPercent <= 0 || total <= 0) {
            return true;
        }
        return part * 100.0 >= total * minPercent;
    }
}
