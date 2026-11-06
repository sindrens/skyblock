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

    /**
     * Pages with a live source today. Their id is the lowercase name (config, messages, milestone ids). Batch 9 (7 Nov
     * 2026): the four grind areas; MINE keeps its id so old mine milestones and the title stay.
     */
    enum PageKind {
        FARM, MINE, FISH, MOBS, CRATES;

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
     * @param tier     place on the page (crop, ore, ring number), 0 for crates
     * @param crate    crate id (Crates page), else null
     * @param icon     vanilla item id of the entry's look (area pages: the item; mobs: the spawn egg)
     * @param nameMini display name for crate rewards (MiniMessage); null = the vanilla name of the item or mob
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

    /** Stored id of an area entry: {@code farm.wheat}, {@code mine.diamond}, {@code fish.cod}, {@code mob.zombie}, {@code boss.3}. */
    static String areaEntryId(String prefix, String id) {
        return prefix + "." + id.toLowerCase(Locale.ROOT);
    }

    /** Stored id of a crate reward entry. */
    static String crateEntryId(String crate, String reward) {
        return "crate." + crate + "." + reward;
    }

    /**
     * Builds the catalog.
     *
     * @param order      page ids in order ({@code pages.order})
     * @param tierLines  page id → vanilla ids in order (farm, mine, fish: items; mobs: mob types, each with its boss)
     * @param crates     crates on the Crates page, in order
     * @param topOnly    only star rewards on the Crates page
     * @param rewards    crate id → its rewards (empty when the crates module is missing)
     * @param knownItem  whether a vanilla item id exists (unknown ones are skipped with a problem)
     */
    static Catalog build(List<String> order, Map<String, List<String>> tierLines, List<String> crates, boolean topOnly,
                         Function<String, List<CrateKeys.RewardInfo>> rewards, Predicate<String> knownItem, Consumer<String> problem) {
        Map<PageKind, Page> pages = new LinkedHashMap<>();
        Set<String> used = new java.util.HashSet<>();
        for (String raw : order) {
            PageKind kind = PageKind.byId(raw);
            if (kind == null) {
                problem.accept("collection.yml -> pages.order: '" + raw + "' is not a page (farm, mine, fish, mobs, crates), skipped");
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
        List<Entry> bosses = new ArrayList<>();
        int tier = 0;
        for (String line : lines) {
            String id = line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
            String where = "collection.yml -> pages." + kind.id() + " '" + line + "'";
            if (id.isEmpty() || id.length() > 48 || !id.matches("[a-z0-9_]+")) {
                problem.accept(where + ": write a vanilla " + (kind == PageKind.MOBS ? "mob" : "item") + " id, skipped");
                continue;
            }
            String icon = kind == PageKind.MOBS ? id + "_spawn_egg" : id;
            if (!knownItem.test(icon)) {
                problem.accept(where + ": '" + icon + "' is not a vanilla item, skipped");
                continue;
            }
            String entry = areaEntryId(kind == PageKind.MOBS ? "mob" : kind.id(), id);
            if (!used.add(entry)) {
                problem.accept(where + ": '" + id + "' is already on the page, skipped");
                continue;
            }
            tier++;
            out.add(new Entry(entry, kind.id(), tier, null, icon, null));
            if (kind == PageKind.MOBS) { // every ring mob has its boss (/pve boss), after the mobs
                bosses.add(new Entry(areaEntryId("boss", String.valueOf(tier)), kind.id(), tier, null, icon, null));
            }
        }
        out.addAll(bosses);
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
