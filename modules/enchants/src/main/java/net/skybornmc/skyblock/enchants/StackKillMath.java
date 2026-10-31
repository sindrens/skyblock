package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The numbers behind the stacked-mob enchants (ENCHANTS §6.6, §7), free of Bukkit so they are unit-tested: Slaughter
 * kill counts with Bloodlust streaks and the per-hit clamp, Reaper's Sweep counts and targets, the Phoenix Wrath hit
 * counter, Plunder and Soul Reaper inside their bonus family caps, and island stack book drops with the daily cap.
 */
final class StackKillMath {

    private StackKillMath() {
    }

    // ------------------------------------------------------------------ Slaughter + Bloodlust

    /**
     * Mobs one killing hit kills: the Slaughter count ({@code base}, 1 without Slaughter) plus the Bloodlust bonus
     * ({@code bonusPercent} of it, at least one mob when there is a bonus), clamped to {@code maxPerHit}. The stacking
     * module clamps it to the stack size afterwards.
     */
    static int kills(int base, double bonusPercent, int maxPerHit) {
        int b = Math.max(1, base);
        long total = b;
        if (Double.isFinite(bonusPercent) && bonusPercent > 0) {
            total += Math.max(1, (long) Math.floor(b * bonusPercent / 100.0));
        }
        return (int) Math.max(1, Math.min(Math.max(1, maxPerHit), total));
    }

    /** Bloodlust bonus in percent: {@code step} per earlier hit of the streak, at most {@code max}. */
    static double bloodlustPercent(int priorHits, double step, double max) {
        if (priorHits <= 0 || !(step > 0) || !(max > 0)) {
            return 0;
        }
        return Math.min(max, priorHits * step);
    }

    /**
     * A Bloodlust streak: the time of the last killing stack hit and how many hits in a row it had.
     *
     * @param lastMillis time of the last killing hit
     * @param hits       killing hits in the streak so far, including the last one
     */
    record Streak(long lastMillis, int hits) {

        static final Streak NONE = new Streak(Long.MIN_VALUE, 0);

        /** Earlier hits that still count for a hit at {@code now} (0 when the window passed). */
        int prior(long now, long windowMillis) {
            if (hits <= 0 || lastMillis == Long.MIN_VALUE || now - lastMillis > windowMillis || now < lastMillis) {
                return 0;
            }
            return hits;
        }

        /** The streak after a killing hit at {@code now}. */
        Streak hit(long now, long windowMillis) {
            int prior = prior(now, windowMillis);
            return new Streak(now, (int) Math.min(Integer.MAX_VALUE, (long) prior + 1));
        }
    }

    // ------------------------------------------------------------------ Reaper's Sweep and Phoenix Wrath

    /** Mobs Reaper's Sweep kills per other stack: {@code percent} of the hit's kill count, at least 1. */
    static int sweepKills(int kills, double percent, int maxPerHit) {
        if (kills <= 0 || !(percent > 0)) {
            return 0;
        }
        long n = Math.max(1, (long) Math.floor(kills * percent / 100.0));
        return (int) Math.min(Math.max(1, maxPerHit), n);
    }

    /**
     * Targets of an area effect: from candidates sorted nearest first, those that pass {@code allowed} and are not
     * {@code exclude}, at most {@code max}.
     */
    static <T> List<T> targets(List<T> nearestFirst, T exclude, Predicate<T> allowed, int max) {
        List<T> out = new ArrayList<>();
        if (max <= 0) {
            return out;
        }
        for (T t : nearestFirst) {
            if (t == null || t == exclude || (exclude != null && t.equals(exclude)) || !allowed.test(t)) {
                continue;
            }
            out.add(t);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /**
     * The Phoenix Wrath counter after one more hit; {@code fires} on every {@code every}-th hit (the counter starts over).
     */
    record Counter(int value, boolean fires) {
    }

    static Counter wrathHit(int counter, int every) {
        if (every <= 0) {
            return new Counter(0, false);
        }
        int next = Math.max(0, counter) + 1;
        return next >= every ? new Counter(0, true) : new Counter(next, false);
    }

    // ------------------------------------------------------------------ bonus families

    /**
     * Plunder on stacks: chance in percent per killed mob for one extra loot roll. Plunder adds to the extra drops family
     * total of all other sources, the family cap applies once, and a mob never gets more than one extra roll (100%).
     */
    static double extraLootPercent(double familyTotal, double plunder, double cap) {
        double p = Double.isFinite(plunder) ? Math.max(0, plunder) : 0;
        double capped = BonusMath.capped(familyTotal + p, cap);
        return Math.max(0, Math.min(100, capped));
    }

    // ------------------------------------------------------------------ island stack books (EN15)

    /**
     * Books found among {@code killed} mobs at 1 in {@code oneIn} each, never more than {@code remaining} (what is left of
     * the daily cap). Exact for any count; stacks are at most 1,024 mobs, so this stays cheap.
     */
    static int books(long killed, int oneIn, int remaining, RandomGenerator random) {
        if (killed <= 0 || oneIn <= 0 || remaining <= 0) {
            return 0;
        }
        double p = 1.0 / oneIn;
        long n = Math.min(killed, 1_000_000);
        int hits = 0;
        for (long i = 0; i < n && hits < remaining; i++) {
            if (random.nextDouble() < p) {
                hits++;
            }
        }
        return hits;
    }

    /** {@code 2:12} → (2, 12); null when broken. */
    static Map.Entry<Integer, Double> parseLevelOdd(String raw) {
        if (raw == null) {
            return null;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            int level = Integer.parseInt(raw.substring(0, colon).trim());
            double weight = Double.parseDouble(raw.substring(colon + 1).trim());
            if (level < 1 || level > 10 || !Double.isFinite(weight) || weight <= 0) {
                return null;
            }
            return Map.entry(level, weight);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Level by weight from {@code level:weight} entries, never above {@code maxLevel}; 1 when nothing is valid. */
    static int bookLevel(List<String> odds, int maxLevel, RandomGenerator random) {
        Map<Integer, Double> weights = new TreeMap<>();
        for (String s : odds) {
            Map.Entry<Integer, Double> e = parseLevelOdd(s);
            if (e != null) {
                weights.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        int level = 1;
        if (total > 0) {
            double roll = random.nextDouble() * total;
            for (Map.Entry<Integer, Double> e : weights.entrySet()) {
                level = e.getKey();
                roll -= e.getValue();
                if (roll < 0) {
                    break;
                }
            }
        }
        return Math.max(1, Math.min(Math.max(1, maxLevel), level));
    }
}
