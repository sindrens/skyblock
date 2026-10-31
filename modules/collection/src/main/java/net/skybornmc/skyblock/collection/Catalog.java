package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.crate.CrateKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The pages and entries of the log (pure, unit tested). Built from {@code collection.yml} and, for the Crates page, from
 * the crates module's catalog ({@link CrateKeys#rewards}), so a crate reward the owner removes simply leaves the page.
 * Immutable; lookups are O(1).
 */
final class Catalog {

    /** Pages with a live source today. Their id is the lowercase name (config, messages, milestone ids). */
    enum PageKind {
        MINE, FOREST, COMBAT, CRATES;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static PageKind byId(String id) {
            for (PageKind k : values()) {
                if (k.id().equalsIgnoreCase(id == null ? "" : id.trim())) {
                    return k;
                }
            }
            return null;
        }
    }

    /**
     * One entry.
     *
     * @param id       stored id ({@code collection_entries.entry_id}); also the key sources report
     * @param page     page id
     * @param tier     Depths tier (mine/forest/combat), 0 for crates
     * @param crate    crate id (Crates page), else null
     * @param icon     vanilla item id for crate rewards; null = the custom item's own look
     * @param nameMini display name for crate rewards (MiniMessage); null = the custom item's own name
     */
    record Entry(String id, String page, int tier, String crate, String icon, String nameMini) {
    }

    record Page(PageKind kind, List<Entry> entries) {

        Page {
            entries = List.copyOf(entries);
        }

        String id() {
            return kind.id();
        }
    }

    static final Catalog EMPTY = new Catalog(List.of());

    private final List<Page> pages;
    private final Map<String, Entry> byId;
    private final int total;

    Catalog(List<Page> pages) {
        this.pages = List.copyOf(pages);
        Map<String, Entry> ids = new HashMap<>();
        int n = 0;
        for (Page p : this.pages) {
            for (Entry e : p.entries()) {
                ids.put(e.id(), e);
                n++;
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.total = n;
    }

    /** Every configured page in order, empty ones included (they block the whole-log milestone). */
    List<Page> pages() {
        return pages;
    }

    /** Pages players see (with at least one entry). */
    List<Page> visible() {
        return pages.stream().filter(p -> !p.entries().isEmpty()).toList();
    }

    Page page(String id) {
        for (Page p : pages) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    Entry entry(String id) {
        return id == null ? null : byId.get(id);
    }

    int total() {
        return total;
    }

    /** Entries of {@code page} in {@code found}. */
    static int found(Page page, Set<String> found) {
        int n = 0;
        for (Entry e : page.entries()) {
            if (found.contains(e.id())) {
                n++;
            }
        }
        return n;
    }

    /** Entries of the whole log in {@code found} (ids no longer in the catalog don't count). */
    int found(Set<String> found) {
        int n = 0;
        for (String id : found) {
            if (byId.containsKey(id)) {
                n++;
            }
        }
        return n;
    }

    /** Stored id of a crate reward entry. */
    static String crateEntryId(String crate, String reward) {
        return "crate." + crate + "." + reward;
    }

    /**
     * Builds the catalog.
     *
     * @param order      page ids in order ({@code pages.order})
     * @param tierLines  page id → lines {@code "<tier>: <item id>"} (mine, forest, combat)
     * @param crates     crates on the Crates page, in order
     * @param topOnly    only star rewards on the Crates page
     * @param rewards    crate id → its rewards (empty when the crates module is missing)
     * @param knownItem  whether a custom item id exists (unknown ones are skipped with a problem)
     */
    static Catalog build(List<String> order, Map<String, List<String>> tierLines, List<String> crates, boolean topOnly,
                         Function<String, List<CrateKeys.RewardInfo>> rewards, Predicate<String> knownItem, Consumer<String> problem) {
        Map<PageKind, Page> pages = new LinkedHashMap<>();
        Set<String> used = new java.util.HashSet<>();
        for (String raw : order) {
            PageKind kind = PageKind.byId(raw);
            if (kind == null) {
                problem.accept("collection.yml -> pages.order: '" + raw + "' is not a page (mine, forest, combat, crates), skipped");
                continue;
            }
            if (pages.containsKey(kind)) {
                continue;
            }
            List<Entry> entries = kind == PageKind.CRATES
                    ? crateEntries(crates, topOnly, rewards, used, problem)
                    : tierEntries(kind, tierLines.getOrDefault(kind.id(), List.of()), knownItem, used, problem);
            pages.put(kind, new Page(kind, entries));
        }
        return new Catalog(new ArrayList<>(pages.values()));
    }

    private static List<Entry> tierEntries(PageKind kind, List<String> lines, Predicate<String> knownItem, Set<String> used,
                                           Consumer<String> problem) {
        List<Entry> out = new ArrayList<>();
        for (String line : lines) {
            String where = "collection.yml -> pages." + kind.id() + " '" + line + "'";
            int colon = line == null ? -1 : line.indexOf(':');
            if (colon < 1) {
                problem.accept(where + ": write '<tier>: <item id>', skipped");
                continue;
            }
            int tier;
            try {
                tier = Integer.parseInt(line.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                problem.accept(where + ": the tier is not a number, skipped");
                continue;
            }
            String item = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            if (tier < 1 || item.isEmpty() || item.length() > 64) {
                problem.accept(where + ": tier must be 1 or more and the item id 1-64 characters, skipped");
                continue;
            }
            if (!knownItem.test(item)) {
                problem.accept(where + ": '" + item + "' is not a custom item (is the depths module on?), skipped");
                continue;
            }
            if (!used.add(item)) {
                problem.accept(where + ": '" + item + "' is already in the log, skipped");
                continue;
            }
            out.add(new Entry(item, kind.id(), tier, null, null, null));
        }
        return out;
    }

    private static List<Entry> crateEntries(List<String> crates, boolean topOnly, Function<String, List<CrateKeys.RewardInfo>> rewards,
                                            Set<String> used, Consumer<String> problem) {
        List<Entry> out = new ArrayList<>();
        for (String raw : crates) {
            String crate = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            List<CrateKeys.RewardInfo> list = crate.isEmpty() ? List.of() : rewards.apply(crate);
            if (list == null || list.isEmpty()) {
                problem.accept("collection.yml -> pages.crates: crate '" + raw + "' has no rewards (unknown crate, or the crates "
                        + "module is off), skipped");
                continue;
            }
            for (CrateKeys.RewardInfo r : list) {
                if (!r.live() || (topOnly && !r.top())) {
                    continue;
                }
                String id = crateEntryId(crate, r.id());
                if (id.length() > 64 || !used.add(id)) {
                    continue;
                }
                out.add(new Entry(id, PageKind.CRATES.id(), 0, crate, r.icon(), r.nameMini()));
            }
        }
        return out;
    }
}
