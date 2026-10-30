package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Book drops from activities (ENCHANTS §3.2–§3.4, EN5/EN7/EN8), pure and unit-tested. An action (a Depths block, log or
 * combat kill; area blocks count at their yield) first checks the account's <b>first finds</b> (the 100th action gives a
 * guaranteed Common, the 1,000th an Uncommon, once per account and season), then the profile's <b>pity</b> counter (2.5×
 * the tier band's expected gap without a book guarantees one), then the plain <b>chance</b> (1 in N by tier band).
 */
final class DropMath {

    private DropMath() {
    }

    /** Why a book drops. */
    enum Kind {
        NONE, CHANCE, PITY, FIRST
    }

    /** @param milestone index of the first-find milestone for {@link Kind#FIRST}, else -1 */
    record Decision(Kind kind, int milestone) {
        static final Decision NONE = new Decision(Kind.NONE, -1);

        boolean drops() {
            return kind != Kind.NONE;
        }
    }

    /** A first-find milestone: the {@code actions}-th action of an activity gives a book of {@code rarity}. */
    record Milestone(int actions, Rarity rarity) {
    }

    /**
     * Drop settings from {@code enchanter.yml → drops.*}.
     *
     * @param bandStarts     first tier of each band (A, B, C, D …)
     * @param oneIn          per pool: 1 in N per action, by band
     * @param pity           per pool: actions without a book that guarantee the next one, by band (0 = no pity)
     * @param bandOdds       rarity odds by band
     * @param levelOdds      book level odds ({@code level:weight}) by band
     * @param firstFinds     first-find milestones, ascending
     * @param miniBossChance percent chance of a Combat book per rewarded player of a mini-boss
     * @param miniBossBands  bands better than the tier for mini-boss books
     */
    record Rules(boolean enabled, List<Integer> bandStarts, Map<Pool, List<Integer>> oneIn, Map<Pool, List<Integer>> pity,
                 List<Map<Rarity, Double>> bandOdds, List<List<String>> levelOdds, List<Milestone> firstFinds,
                 double miniBossChance, int miniBossBands) {

        Rules {
            bandStarts = List.copyOf(bandStarts);
            oneIn = Map.copyOf(oneIn);
            pity = Map.copyOf(pity);
            bandOdds = List.copyOf(bandOdds);
            levelOdds = List.copyOf(levelOdds);
            firstFinds = List.copyOf(firstFinds);
        }

        int band(int tier) {
            return DropMath.band(bandStarts, tier);
        }

        int oneIn(Pool pool, int band) {
            return byBand(oneIn.getOrDefault(pool, List.of()), band, 0);
        }

        int pityGap(Pool pool, int band) {
            return byBand(pity.getOrDefault(pool, List.of()), band, 0);
        }

        Map<Rarity, Double> odds(int band) {
            return byBand(bandOdds, band, Map.of());
        }

        List<String> levelOdds(int band) {
            return byBand(levelOdds, band, List.of());
        }

        boolean drops(Pool pool) {
            return enabled && oneIn.containsKey(pool);
        }
    }

    // ------------------------------------------------------------------ bands

    /** Band index (0 = A) of a tier: how many band starts are at or below it, minus one (at least 0). */
    static int band(List<Integer> bandStarts, int tier) {
        int count = 0;
        for (int start : bandStarts) {
            if (tier >= start) {
                count++;
            }
        }
        return Math.max(0, count - 1);
    }

    /** The value for a band; the last value for higher bands, {@code fallback} when the list is empty. */
    static <T> T byBand(List<T> values, int band, T fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(Math.max(0, Math.min(band, values.size() - 1)));
    }

    // ------------------------------------------------------------------ chance, pity, first finds

    /** Chance (0..1) of at least one hit at 1 in {@code oneIn} over {@code actions} actions (fractional actions allowed). */
    static double chance(int oneIn, double actions) {
        if (oneIn <= 0 || !(actions > 0)) {
            return 0;
        }
        if (oneIn == 1) {
            return 1;
        }
        return 1 - Math.pow(1 - 1.0 / oneIn, actions);
    }

    /**
     * The milestone index that is due now, or -1: the next milestone ({@code granted}) whose action count is reached.
     * Only one milestone per call; a skipped one (e.g. 1,000 reached while 100 was still pending) comes on the next action.
     */
    static int firstFindDue(double actions, int granted, List<Milestone> milestones) {
        if (granted < 0 || granted >= milestones.size()) {
            return -1;
        }
        return actions >= milestones.get(granted).actions() ? granted : -1;
    }

    /**
     * What one action gives.
     *
     * @param firstReady true when the account's first finds are loaded and no grant of this activity is in flight
     * @param pityReady  true when the profile's pity counter is loaded
     * @param since      actions since the last book of this activity (after this action)
     * @param roll       uniform random 0..1
     */
    static Decision decide(boolean firstReady, double accountActions, int granted, List<Milestone> milestones,
                           boolean pityReady, double since, int pityGap, double chance, double roll) {
        if (firstReady) {
            int due = firstFindDue(accountActions, granted, milestones);
            if (due >= 0) {
                return new Decision(Kind.FIRST, due);
            }
        }
        if (pityReady && pityGap > 0 && since >= pityGap) {
            return new Decision(Kind.PITY, -1);
        }
        return roll < chance ? new Decision(Kind.CHANCE, -1) : Decision.NONE;
    }

    /**
     * Actions until the next guaranteed book (the menu's "next guaranteed book"): the nearer of the next first find and the
     * pity threshold, at least 1. {@code Long.MAX_VALUE} when nothing is guaranteed.
     */
    static long nextGuaranteed(double since, int pityGap, double accountActions, int granted, List<Milestone> milestones) {
        double best = Double.POSITIVE_INFINITY;
        if (pityGap > 0) {
            best = pityGap - since;
        }
        if (granted >= 0 && granted < milestones.size()) {
            best = Math.min(best, milestones.get(granted).actions() - accountActions);
        }
        if (Double.isInfinite(best)) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, (long) Math.ceil(best));
    }

    // ------------------------------------------------------------------ parsing

    /** {@code "100:common"} entries, sorted by actions; broken ones skipped. */
    static List<Milestone> parseMilestones(List<String> raw) {
        List<Milestone> out = new ArrayList<>();
        for (String s : raw) {
            Milestone m = parseMilestone(s);
            if (m != null) {
                out.add(m);
            }
        }
        out.sort((a, b) -> Integer.compare(a.actions(), b.actions()));
        return out;
    }

    static Milestone parseMilestone(String raw) {
        if (raw == null) {
            return null;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        try {
            int actions = Integer.parseInt(raw.substring(0, colon).trim());
            Rarity rarity = Rarity.valueOf(raw.substring(colon + 1).trim().toUpperCase(Locale.ROOT));
            if (actions < 1 || rarity == Rarity.MYTHIC || rarity == Rarity.SPECIAL) {
                return null;
            }
            return new Milestone(actions, rarity);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whole numbers ≥ 0 from strings; broken entries skipped. */
    static List<Integer> parseInts(List<String> raw) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 0) {
                    out.add(v);
                }
            } catch (NumberFormatException | NullPointerException ignored) {
                // skipped; EnchanterConfig.validate reports it
            }
        }
        return out;
    }

    /** Rarity odds of a band shifted {@code bonus} bands up (mini-bosses), clamped to the last band. */
    static Map<Rarity, Double> shifted(Rules rules, int band, int bonus) {
        Map<Rarity, Double> odds = rules.odds(Math.max(0, band + Math.max(0, bonus)));
        return odds.isEmpty() ? Map.of() : new EnumMap<>(odds);
    }
}
