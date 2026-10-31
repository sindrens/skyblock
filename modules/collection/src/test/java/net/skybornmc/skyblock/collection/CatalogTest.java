package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.crate.CrateKeys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {

    private static final Set<String> ITEMS = Set.of("depths_coal", "depths_copper", "depths_oak_log", "depths_bone");

    private static List<CrateKeys.RewardInfo> rewards(String crate) {
        return switch (crate) {
            case "ember" -> List.of(
                    new CrateKeys.RewardInfo("ember", "money", false, true, "gold_nugget", "Money"),
                    new CrateKeys.RewardInfo("ember", "cinder_key", true, true, "tripwire_hook", "Cinder Key"),
                    new CrateKeys.RewardInfo("ember", "tome", true, false, "book", "Tome"));
            case "cinder" -> List.of(new CrateKeys.RewardInfo("cinder", "spawner_t3", true, true, "spawner", "Spawner"));
            default -> List.of();
        };
    }

    private static Catalog build(List<String> problems, boolean topOnly) {
        return Catalog.build(List.of("mine", "forest", "combat", "crates", "fishing"),
                Map.of("mine", List.of("1: depths_coal", "2: depths_copper", "3: depths_unknown", "x: depths_coal", "4 depths_iron"),
                        "forest", List.of("1: depths_oak_log", "2: depths_coal"),
                        "combat", List.of("2: depths_bone")),
                List.of("ember", "cinder", "nope"), topOnly, CatalogTest::rewards, ITEMS::contains, problems::add);
    }

    @Test
    void buildsPagesInOrderAndSkipsBrokenLines() {
        List<String> problems = new ArrayList<>();
        Catalog c = build(problems, true);
        assertEquals(List.of("mine", "forest", "combat", "crates"), c.pages().stream().map(Catalog.Page::id).toList());
        assertEquals(List.of("depths_coal", "depths_copper"), c.page("mine").entries().stream().map(Catalog.Entry::id).toList());
        // depths_coal is already on the Mine page: one entry per item in the whole log.
        assertEquals(List.of("depths_oak_log"), c.page("forest").entries().stream().map(Catalog.Entry::id).toList());
        assertEquals(2, c.page("mine").entries().get(1).tier());
        // unknown item, bad tier, missing colon, duplicate, unknown page, unknown crate
        assertEquals(6, problems.size(), problems.toString());
    }

    @Test
    void cratesPageHasLiveStarRewardsOnly() {
        Catalog c = build(new ArrayList<>(), true);
        assertEquals(List.of("crate.ember.cinder_key", "crate.cinder.spawner_t3"),
                c.page("crates").entries().stream().map(Catalog.Entry::id).toList());
        Catalog.Entry key = c.entry("crate.ember.cinder_key");
        assertNotNull(key);
        assertEquals("ember", key.crate());
        assertEquals("tripwire_hook", key.icon());
        assertNull(c.entry("crate.ember.tome"), "coming soon rewards are never in the log");
        Catalog all = build(new ArrayList<>(), false);
        assertTrue(all.page("crates").entries().stream().anyMatch(e -> e.id().equals("crate.ember.money")));
    }

    @Test
    void countsFoundPerPageAndIgnoresStaleIds() {
        Catalog c = build(new ArrayList<>(), true);
        Set<String> found = Set.of("depths_coal", "crate.ember.cinder_key", "removed_entry");
        assertEquals(1, Catalog.found(c.page("mine"), found));
        assertEquals(2, c.found(found));
        assertEquals(6, c.total());
    }

    @Test
    void emptyPagesStayInTheCatalogButAreNotVisible() {
        Catalog c = Catalog.build(List.of("mine", "crates"), Map.of("mine", List.of("1: depths_coal")), List.of(), true,
                x -> List.of(), ITEMS::contains, p -> { });
        assertEquals(2, c.pages().size());
        assertEquals(1, c.visible().size());
    }
}
