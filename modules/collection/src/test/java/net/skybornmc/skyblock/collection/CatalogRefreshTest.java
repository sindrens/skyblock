package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.crate.CrateKeys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF11: the Crates page refresh is built off the main thread; only a real, still-current change is applied. */
class CatalogRefreshTest {

    private static final Set<String> ITEMS = Set.of("depths_coal");

    private static Catalog catalog(Function<String, List<CrateKeys.RewardInfo>> rewards, List<String> problems) {
        return Catalog.build(List.of("mine", "crates"), Map.of("mine", List.of("1: depths_coal")), List.of("ember"), true,
                rewards, ITEMS::contains, problems::add);
    }

    private static List<CrateKeys.RewardInfo> one(String crate) {
        return List.of(new CrateKeys.RewardInfo(crate, "cinder_key", true, true, "tripwire_hook", "Cinder Key"));
    }

    private static List<CrateKeys.RewardInfo> two(String crate) {
        return List.of(new CrateKeys.RewardInfo(crate, "cinder_key", true, true, "tripwire_hook", "Cinder Key"),
                new CrateKeys.RewardInfo(crate, "spawner", true, true, "spawner", "Spawner"));
    }

    @Test
    void sameCatalogNeedsNoMainThread() {
        CatalogRefresh r = new CatalogRefresh();
        r.shown(catalog(CatalogRefreshTest::one, new ArrayList<>()));
        assertNull(r.compare(catalog(CatalogRefreshTest::one, new ArrayList<>()), List.of(), r.version()));
    }

    @Test
    void changedCatalogIsAppliedOnce() {
        CatalogRefresh r = new CatalogRefresh();
        r.shown(catalog(CatalogRefreshTest::one, new ArrayList<>()));
        List<String> problems = new ArrayList<>();
        Catalog fresh = catalog(CatalogRefreshTest::two, problems);
        CatalogRefresh.Result changed = r.compare(fresh, problems, r.version());
        assertNotNull(changed);
        assertEquals(fresh, changed.catalog());
        assertTrue(r.accept(changed));
        assertFalse(r.accept(changed), "a second hand-over of the same result does nothing");
        assertNull(r.compare(catalog(CatalogRefreshTest::two, new ArrayList<>()), List.of(), r.version()), "now shown");
    }

    @Test
    void reloadMeanwhileDropsTheOlderResult() {
        CatalogRefresh r = new CatalogRefresh();
        r.shown(catalog(CatalogRefreshTest::one, new ArrayList<>()));
        int started = r.version();
        CatalogRefresh.Result changed = r.compare(catalog(CatalogRefreshTest::two, new ArrayList<>()), List.of(), started);
        r.shown(catalog(c -> List.of(), new ArrayList<>())); // /skyblock reload collection on the main thread meanwhile
        assertFalse(r.accept(changed), "built before the reload: dropped, the next refresh sees the current crates");
    }

    @Test
    void disableDropsResultsOnTheirWay() {
        CatalogRefresh r = new CatalogRefresh();
        r.shown(catalog(CatalogRefreshTest::one, new ArrayList<>()));
        CatalogRefresh.Result changed = r.compare(catalog(CatalogRefreshTest::two, new ArrayList<>()), List.of(), r.version());
        r.invalidate();
        assertFalse(r.accept(changed));
        assertFalse(r.accept(null));
    }

    @Test
    void problemsTravelWithTheResultAsACopy() {
        CatalogRefresh r = new CatalogRefresh();
        List<String> problems = new ArrayList<>(List.of("crates: unknown crate 'nope'"));
        CatalogRefresh.Result changed = r.compare(catalog(CatalogRefreshTest::one, new ArrayList<>()), problems, r.version());
        problems.clear();
        assertEquals(List.of("crates: unknown crate 'nope'"), changed.problems());
    }

    @Test
    void signatureSeesEntryAndNameChanges() {
        String a = CatalogRefresh.signature(catalog(CatalogRefreshTest::one, new ArrayList<>()));
        String renamed = CatalogRefresh.signature(catalog(c -> List.of(
                new CrateKeys.RewardInfo(c, "cinder_key", true, true, "tripwire_hook", "Cinder Key II")), new ArrayList<>()));
        assertNotEquals(a, renamed);
        assertEquals(a, CatalogRefresh.signature(catalog(CatalogRefreshTest::one, new ArrayList<>())));
        assertNotEquals(CatalogRefresh.signature(Catalog.EMPTY), a);
    }
}
