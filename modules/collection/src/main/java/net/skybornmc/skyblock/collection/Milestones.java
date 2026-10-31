package net.skybornmc.skyblock.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Page milestones and what they pay (pure, unit tested; PHASE-10 M10.10, P10-33).
 * <ul>
 *   <li>{@code <page>:<percent>} for every configured percent (25/50/75/100): one island level point each.</li>
 *   <li>{@code <page>:100} also pays the page title and a Blaze key.</li>
 *   <li>{@code all} (every configured page at 100%, none of them empty) pays Keeper of Ashes, a Phoenix key and the secret
 *       set token once one exists.</li>
 * </ul>
 * Milestones never go back: a milestone stays reached (and paid) when the catalog changes later.
 */
final class Milestones {

    static final String ALL = "all";
    static final int FULL = 100;

    /** Grant kinds ({@code collection_grants.grant_kind}). */
    static final String KEY = "key";
    static final String TITLE = "title";
    static final String SET = "set";

    /** One hand-out of a milestone. {@code ref}: crate id, title id or set id. */
    record Grant(String kind, String ref, long amount) {
    }

    /** Reward settings snapshot (from {@code collection.yml}). */
    record Rewards(boolean pageTitles, String pageKeyCrate, int pageKeys, String logTitle, String logKeyCrate, int logKeys,
                   String logSet) {

        static Rewards of(CollectionSettings s) {
            return new Rewards(s.pageTitles, clean(s.pageKeyCrate), s.pageKeys, clean(s.logTitle), clean(s.logKeyCrate), s.logKeys,
                    clean(s.logSet));
        }

        private static String clean(String s) {
            return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        }
    }

    private Milestones() {
    }

    /** The configured percents: 1-100, sorted, no duplicates; 100 is always there (the full page pays its rewards). */
    static List<Integer> percents(List<String> raw, Consumer<String> problem) {
        Set<Integer> out = new TreeSet<>();
        for (String s : raw) {
            try {
                int p = Integer.parseInt(s.trim().replace("%", ""));
                if (p < 1 || p > FULL) {
                    problem.accept("collection.yml -> milestones.percents: " + s + " is not 1-100, skipped");
                } else {
                    out.add(p);
                }
            } catch (NumberFormatException e) {
                problem.accept("collection.yml -> milestones.percents: '" + s + "' is not a number, skipped");
            }
        }
        out.add(FULL);
        return List.copyOf(out);
    }

    static String id(String page, int percent) {
        return page + ":" + percent;
    }

    /** Percent of a milestone id ({@code mine:50} → 50), or -1 for {@code all} or a broken id. */
    static int percentOf(String id) {
        int colon = id == null ? -1 : id.lastIndexOf(':');
        if (colon < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Page milestones count for the island level; {@code all} does not. */
    static boolean isPageMilestone(String id) {
        return percentOf(id) > 0;
    }

    /** True when {@code found} of {@code total} reaches {@code percent} (an empty page reaches nothing). */
    static boolean reached(int found, int total, int percent) {
        return total > 0 && found * 100L >= (long) percent * total;
    }

    /** Every milestone {@code found} reaches now, in page order then percent, {@code all} last. */
    static List<String> reachedNow(Catalog catalog, Set<String> found, List<Integer> percents) {
        List<String> out = new ArrayList<>();
        boolean all = !catalog.pages().isEmpty();
        for (Catalog.Page page : catalog.pages()) {
            int total = page.entries().size();
            int n = Catalog.found(page, found);
            for (int p : percents) {
                if (reached(n, total, p)) {
                    out.add(id(page.id(), p));
                }
            }
            all &= reached(n, total, FULL);
        }
        if (all) {
            out.add(ALL);
        }
        return out;
    }

    /** Milestones reached now that are not in {@code already}. */
    static List<String> newlyReached(Catalog catalog, Set<String> found, Set<String> already, List<Integer> percents) {
        List<String> out = new ArrayList<>();
        for (String id : reachedNow(catalog, found, percents)) {
            if (!already.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Title id of a page ({@code collection_mine}). */
    static String pageTitle(String page) {
        return "collection_" + page;
    }

    /** What one milestone hands out (island points are not a hand-out; they follow the milestone rows). */
    static List<Grant> grants(String milestone, Rewards r) {
        List<Grant> out = new ArrayList<>();
        if (ALL.equals(milestone)) {
            if (!r.logTitle().isEmpty()) {
                out.add(new Grant(TITLE, r.logTitle(), 1));
            }
            if (!r.logKeyCrate().isEmpty() && r.logKeys() > 0) {
                out.add(new Grant(KEY, r.logKeyCrate(), r.logKeys()));
            }
            if (!r.logSet().isEmpty()) {
                out.add(new Grant(SET, r.logSet(), 1));
            }
            return out;
        }
        if (percentOf(milestone) != FULL) {
            return out;
        }
        String page = milestone.substring(0, milestone.lastIndexOf(':'));
        if (r.pageTitles()) {
            out.add(new Grant(TITLE, pageTitle(page), 1));
        }
        if (!r.pageKeyCrate().isEmpty() && r.pageKeys() > 0) {
            out.add(new Grant(KEY, r.pageKeyCrate(), r.pageKeys()));
        }
        return out;
    }
}
