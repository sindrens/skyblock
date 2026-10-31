package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled {@code masteries.yml} (PHASE-5 M5.5) loads cleanly, follows the plan's table and hides unbuilt masteries. */
class MasteryCatalogTest {

    static MasteryCatalog bundled() throws Exception {
        try (var in = MasteryCatalogTest.class.getClassLoader().getResourceAsStream(MasteryCatalog.FILE)) {
            assertNotNull(in, "masteries.yml missing");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> problems = new ArrayList<>();
            MasteryCatalog catalog = MasteryCatalog.load(yaml, id -> true, problems::add);
            assertEquals(List.of(), problems);
            return catalog;
        }
    }

    @Test
    void bundledCatalogFollowsThePlan() throws Exception {
        MasteryCatalog catalog = bundled();
        assertEquals(13, catalog.size(), "MA1-MA12 without MA5, plus Treasury and Market Contacts");
        MasteryCatalog.Def wings = catalog.get("wings").orElseThrow();
        assertEquals(1, wings.maxRank());
        assertEquals(20, wings.rank(1).islandLevel(), "P5-20: Wings at island level 20");
        assertEquals(60_000, wings.rank(1).cost(), 1_000, "P5-20: about $60k");
        MasteryCatalog.Def treasury = catalog.get("treasury").orElseThrow();
        assertEquals(List.of(15, 35, 60), treasury.ranks().stream().map(MasteryCatalog.Rank::islandLevel).toList(), "P5-19");
        assertEquals(List.of(1.0, 2.0, 3.0), treasury.ranks().stream().map(MasteryCatalog.Rank::value).toList());
        MasteryCatalog.Def warrior = catalog.get("warrior").orElseThrow();
        assertEquals(Pillar.COMBAT, warrior.pillar());
        assertEquals(List.of(10, 20, 35, 50, 60), warrior.ranks().stream().map(MasteryCatalog.Rank::pillarLevel).toList(),
                "pillar level = 5 x price tier");
        assertEquals(20, warrior.valueAt(5));
        MasteryCatalog.Def homestead = catalog.get("homestead").orElseThrow();
        assertEquals(2, homestead.maxRank());
        assertEquals(List.of(20, 40), homestead.ranks().stream().map(MasteryCatalog.Rank::islandLevel).toList());
        MasteryCatalog.Def market = catalog.get("market_contacts").orElseThrow();
        assertEquals(20, market.maxRank());
        assertEquals(List.of("sell_multiplier"), market.requiresMaxed());
        assertEquals(40, market.valueAt(20), "+2% sell per level, max +40%");
        long previous = 0;
        for (MasteryCatalog.Rank r : market.ranks()) {
            assertTrue(r.cost() > previous, "prices grow on the endless curve");
            previous = r.cost();
        }
    }

    @Test
    void unbuiltMasteriesAreHidden() throws Exception {
        Set<String> shown = new HashSet<>();
        bundled().shown().forEach(d -> shown.add(d.id()));
        assertEquals(Set.of("warrior", "deep_pockets", "crystal_rush", "lucky_strike", "homestead", "wings", "treasury", "market_contacts"), shown,
                "GUI-2: Timber, Swift Hands, Fuel Efficiency, Beast Tamer and Angler stay hidden until their feature reads them");
    }

    @Test
    void everyShownRankHasItsTasks() throws Exception {
        TaskCatalog tasks = TaskCatalog.load(TaskCatalogTest.bundled(), TaskCatalog.Checks.all(), p -> { });
        for (MasteryCatalog.Def def : bundled().shown()) {
            if (def.effect() == MasteryEffect.MARKET_CONTACTS) {
                continue; // gated by Sell Multiplier maxed, no own tasks
            }
            for (int n = 1; n <= def.maxRank(); n++) {
                assertFalse(tasks.byUnlock(def.unlockKey(n)).isEmpty(), "tasks for " + def.unlockKey(n));
            }
        }
    }

    @Test
    void brokenEntriesAreSkippedWithAReason() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("masteries.ascension.effect", "wings");
        yaml.set("masteries.ascension.ranks", List.of(java.util.Map.of("tier", 1, "cost", 1, "value", 1)));
        yaml.set("masteries.double_wings.effect", "wings");
        yaml.set("masteries.double_wings.ranks", List.of(java.util.Map.of("tier", 1, "cost", 1, "value", 1),
                java.util.Map.of("tier", 2, "cost", 2, "value", 1)));
        yaml.set("masteries.bad_pillar.effect", "warrior");
        yaml.set("masteries.bad_pillar.pillar", "cooking");
        yaml.set("masteries.bad_pillar.ranks", List.of(java.util.Map.of("tier", 1, "cost", 1, "value", 1)));
        yaml.set("masteries.down.effect", "warrior");
        yaml.set("masteries.down.ranks", List.of(java.util.Map.of("tier", 1, "cost", 1, "value", 5),
                java.util.Map.of("tier", 2, "cost", 2, "value", 4)));
        yaml.set("masteries.ok.effect", "crystal-rush");
        yaml.set("masteries.ok.ranks", List.of(java.util.Map.of("tier", 1, "cost", 100, "value", 3, "island-level", 10)));
        List<String> problems = new ArrayList<>();
        MasteryCatalog catalog = MasteryCatalog.load(yaml, id -> true, problems::add);
        assertEquals(1, catalog.size());
        assertEquals(10, catalog.get("ok").orElseThrow().rank(1).islandLevel());
        assertNull(catalog.get("ok").orElseThrow().rank(2));
        assertEquals(4, problems.size(), problems.toString());
    }
}
