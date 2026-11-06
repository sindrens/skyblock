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

    private static final Set<String> ITEMS = Set.of("wheat", "carrot", "coal", "raw_iron", "cod", "zombie_spawn_egg", "husk_spawn_egg");

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
        return Catalog.build(List.of("farm", "mine", "fish", "mobs", "crates", "forest"),
                Map.of("farm", List.of("wheat", "carrot", "pumpkin", "Wheat", "bad id!"),
                        "mine", List.of("coal", "raw_iron"),
                        "fish", List.of("cod"),
                        "mobs", List.of("zombie", "husk")),
                List.of("ember", "cinder", "nope"), topOnly, CatalogTest::rewards, ITEMS::contains, problems::add);
    }

    @Test
    void buildsPagesInOrderAndSkipsBrokenLines() {
        List<String> problems = new ArrayList<>();
        Catalog c = build(problems, true);
        assertEquals(List.of("farm", "mine", "fish", "mobs", "crates"), c.pages().stream().map(Catalog.Page::id).toList());
        assertEquals(List.of("farm.wheat", "farm.carrot"), c.page("farm").entries().stream().map(Catalog.Entry::id).toList());
        assertEquals(List.of("mine.coal", "mine.raw_iron"), c.page("mine").entries().stream().map(Catalog.Entry::id).toList());
        assertEquals(2, c.page("mine").entries().get(1).tier());
        // every ring mob brings its boss, after the mobs
        assertEquals(List.of("mob.zombie", "mob.husk", "boss.1", "boss.2"), c.page("mobs").entries().stream().map(Catalog.Entry::id).toList());
        assertEquals("husk_spawn_egg", c.entry("boss.2").icon());
        // unknown item, duplicate, bad id, unknown page, unknown crate
        assertEquals(5, problems.size(), problems.toString());
    }

    @Test
    void theBundledSettingsBuildWithoutProblems() {
        CollectionSettings s = new CollectionSettings();
        List<String> problems = new ArrayList<>();
        Catalog c = Catalog.build(s.pageOrder, Map.of("farm", s.farm, "mine", s.mine, "fish", s.fish, "mobs", s.mobs), List.of(),
                true, x -> List.of(), id -> org.bukkit.Material.getMaterial(id.toUpperCase(java.util.Locale.ROOT)) != null, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(5, c.page("farm").entries().size());
        assertEquals(7, c.page("mine").entries().size());
        assertEquals(9, c.page("fish").entries().size());
        assertEquals(20, c.page("mobs").entries().size(), "10 ring mobs and their 10 bosses");
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
        Set<String> found = Set.of("mine.coal", "crate.ember.cinder_key", "removed_entry");
        assertEquals(1, Catalog.found(c.page("mine"), found));
        assertEquals(2, c.found(found));
        assertEquals(11, c.total());
    }

    @Test
    void emptyPagesStayInTheCatalogButAreNotVisible() {
        Catalog c = Catalog.build(List.of("mine", "crates"), Map.of("mine", List.of("coal")), List.of(), true,
                x -> List.of(), ITEMS::contains, p -> { });
        assertEquals(2, c.pages().size());
        assertEquals(1, c.visible().size());
    }
}
