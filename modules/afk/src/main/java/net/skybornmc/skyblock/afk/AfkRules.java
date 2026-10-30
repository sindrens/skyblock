package net.skybornmc.skyblock.afk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The AFK pool numbers parsed from {@link AfkConfig} (pure, no server): reward length, daily cap, money per stage tier,
 * session steps and key milestones. Bad entries are skipped and reported in {@link #problems()}.
 */
public record AfkRules(long rewardMillis, long dailyCapMillis, long ipDailyCapMillis, long[] moneyByTier, int[] tierLevels,
                       List<Step> steps, List<KeyMilestone> keys, List<String> problems) {

    /** From {@code minutes} in one session on, rewards are {@code percent}% of the tier's money. */
    public record Step(int minutes, int percent) {
    }

    /** At {@code minutes} credited today the account gets {@code amount} keys of {@code crate}. */
    public record KeyMilestone(int minutes, String crate, int amount) {

        public long millis() {
            return minutes * 60_000L;
        }
    }

    private static final Pattern CRATE_ID = Pattern.compile("[a-z0-9_]{1,32}");
    static final long MINUTE = 60_000L;

    public static AfkRules from(AfkConfig c) {
        List<String> problems = new ArrayList<>();
        long[] money = parseMoney(c.moneyByTier, problems);
        int[] levels = parseLevels(c.tierLevels, problems);
        List<Step> steps = parseSteps(c.sessionSteps, problems);
        List<KeyMilestone> keys = parseKeys(c.keyMilestones, c.dailyCapMinutes, problems);
        return new AfkRules(c.rewardMinutes * MINUTE, c.dailyCapMinutes * MINUTE, c.ipDailyCapMinutes * MINUTE, money, levels,
                steps, keys, List.copyOf(problems));
    }

    // ------------------------------------------------------------------ queries

    /** Reward size in percent after {@code sessionMillis} in the pool without leaving. */
    public int stepPercent(long sessionMillis) {
        int percent = 100;
        for (Step s : steps) {
            if (sessionMillis >= s.minutes() * MINUTE) {
                percent = s.percent();
            }
        }
        return percent;
    }

    /** Stage tier (1..n) for the highest stage skill level. */
    public int tierFor(int level) {
        int tier = 1;
        for (int i = 0; i < tierLevels.length; i++) {
            if (level >= tierLevels[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** Money of one reward at this tier and session length (rounded down, never negative). */
    public long money(int tier, long sessionMillis) {
        if (moneyByTier.length == 0) {
            return 0;
        }
        long base = moneyByTier[Math.max(0, Math.min(moneyByTier.length - 1, tier - 1))];
        try {
            return Math.multiplyExact(base, stepPercent(sessionMillis)) / 100;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE / 100;
        }
    }

    /** The highest percent a session can reach. */
    public int maxPercent() {
        return steps.stream().mapToInt(Step::percent).max().orElse(100);
    }

    /** Milestones passed when today's credited time went from {@code before} (exclusive) to {@code after} (inclusive). */
    public List<KeyMilestone> crossed(long before, long after) {
        List<KeyMilestone> out = new ArrayList<>();
        for (KeyMilestone k : keys) {
            if (before < k.millis() && k.millis() <= after) {
                out.add(k);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    static long[] parseMoney(List<String> raw, List<String> problems) {
        List<Long> values = new ArrayList<>();
        for (String s : raw) {
            try {
                long v = Long.parseLong(s.trim().replace("_", ""));
                if (v < 0) {
                    throw new NumberFormatException();
                }
                values.add(v);
            } catch (NumberFormatException e) {
                problems.add("afk.yml → money.per-reward-by-tier: '" + s + "' is not a whole number >= 0 (skipped)");
            }
        }
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    static int[] parseLevels(List<String> raw, List<String> problems) {
        List<Integer> values = new ArrayList<>();
        int last = Integer.MIN_VALUE;
        for (String s : raw) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v < 0 || v < last) {
                    problems.add("afk.yml → money.tier-levels: '" + s + "' must be >= 0 and not lower than the one before (skipped)");
                    continue;
                }
                values.add(v);
                last = v;
            } catch (NumberFormatException e) {
                problems.add("afk.yml → money.tier-levels: '" + s + "' is not a whole number (skipped)");
            }
        }
        if (values.isEmpty()) {
            values.add(0);
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    static List<Step> parseSteps(List<String> raw, List<String> problems) {
        List<Step> steps = new ArrayList<>();
        for (String s : raw) {
            String[] parts = s.trim().split(":");
            try {
                if (parts.length != 2) {
                    throw new NumberFormatException();
                }
                int minutes = Integer.parseInt(parts[0].trim());
                int percent = Integer.parseInt(parts[1].trim());
                if (minutes < 0 || percent < 0 || percent > 1000) {
                    throw new NumberFormatException();
                }
                steps.add(new Step(minutes, percent));
            } catch (NumberFormatException e) {
                problems.add("afk.yml → money.session-steps: '" + s + "' must be 'minutes:percent' (percent 0-1000) (skipped)");
            }
        }
        steps.sort(Comparator.comparingInt(Step::minutes));
        return List.copyOf(steps);
    }

    static List<KeyMilestone> parseKeys(List<String> raw, int capMinutes, List<String> problems) {
        List<KeyMilestone> keys = new ArrayList<>();
        for (String s : raw) {
            String[] parts = s.trim().split(":");
            try {
                if (parts.length != 3) {
                    throw new IllegalArgumentException();
                }
                int minutes = Integer.parseInt(parts[0].trim());
                String crate = parts[1].trim().toLowerCase(Locale.ROOT);
                int amount = Integer.parseInt(parts[2].trim());
                if (minutes <= 0 || amount <= 0 || amount > 64 || !CRATE_ID.matcher(crate).matches()) {
                    throw new IllegalArgumentException();
                }
                if (minutes > capMinutes) {
                    problems.add("afk.yml → keys.milestones: '" + s + "' is above the daily cap (" + capMinutes + " min) and can never be reached");
                }
                if (keys.stream().anyMatch(k -> k.minutes() == minutes)) {
                    problems.add("afk.yml → keys.milestones: two milestones at " + minutes + " min (the second is skipped)");
                    continue;
                }
                keys.add(new KeyMilestone(minutes, crate, amount));
            } catch (IllegalArgumentException e) {
                problems.add("afk.yml → keys.milestones: '" + s + "' must be 'minutes:crate:amount' (crate id a-z 0-9 _, amount 1-64) (skipped)");
            }
        }
        keys.sort(Comparator.comparingInt(KeyMilestone::minutes));
        return List.copyOf(keys);
    }
}
