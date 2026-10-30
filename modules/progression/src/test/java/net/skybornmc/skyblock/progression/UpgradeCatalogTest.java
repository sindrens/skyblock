package net.skybornmc.skyblock.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled upgrades.yml (plan catalog and prices), its validation, its tasks and its message keys (M5.4). */
class UpgradeCatalogTest {

    private static YamlConfiguration resource(String name) throws Exception {
        try (var in = UpgradeCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bundledCatalogFollowsThePlan() throws Exception {
        UpgradeCatalog c = UpgradeStoreTest.bundled();
        assertEquals(18, c.size());
        UpgradeCatalog.Def size = c.get("island_size").orElseThrow();
        assertEquals(List.of(5.0, 7.0, 9.0), size.levels().stream().map(UpgradeCatalog.Level::value).toList(), "3x3 -> 9x9 (owner 157)");
        assertEquals(List.of(3_350L, 22_900L, 157_000L), size.levels().stream().map(UpgradeCatalog.Level::cost).toList());
        assertEquals(List.of(1, 3, 5), size.levels().stream().map(UpgradeCatalog.Level::tier).toList());
        UpgradeCatalog.Def slots = c.get("member_slots").orElseThrow();
        assertEquals(4, slots.maxLevel());
        assertEquals(9.0, slots.valueAt(4), "8 members + leader (owner 242)");
        assertEquals(7_010L, slots.level(1).cost());
        assertEquals(12, c.get("sell_multiplier").orElseThrow().maxLevel());
        assertEquals(60.0, c.get("sell_multiplier").orElseThrow().valueAt(12));
        assertEquals(157_000L, c.get("entity_limits").orElseThrow().level(1).cost());
        assertEquals(2_500L, c.get("spawner_rate").orElseThrow().level(1).cost(), "production: C_p(1)");
        // Features that don't exist yet: shown as coming soon, not buyable.
        for (String id : List.of("island_vault", "mob_loot", "crop_growth", "harvest_yield", "generator", "fuel_tank",
                "mine_fortune", "lumberjack", "xp_boost")) {
            assertFalse(c.get(id).orElseThrow().buyable(), id);
        }
        for (String id : List.of("island_size", "member_slots", "warp_slots", "entity_limits", "bank_capacity", "bank_interest",
                "sell_multiplier", "spawner_stack_limit", "spawner_rate")) {
            assertTrue(c.get(id).orElseThrow().buyable(), id);
        }
        for (UpgradeTab tab : UpgradeTab.values()) {
            assertTrue(c.tab(tab).size() <= UpgradeMenu.UPGRADE_SLOTS.length, tab + " fits the menu");
            assertFalse(c.tab(tab).isEmpty(), tab + " has upgrades");
        }
        assertTrue(c.tab(UpgradeTab.DEPTHS).stream().noneMatch(UpgradeCatalog.Def::buyable),
                "Depths upgrades wait until the drop roll and skill XP read the player's bonus families");
        for (UpgradeCatalog.Def d : c.all()) {
            assertTrue(d.maxLevel() <= UpgradeDetailMenu.LEVEL_SLOTS.length, d.id() + " levels fit the detail menu");
        }
    }

    @Test
    void everyBuyableLevelHasTasks() throws Exception {
        UpgradeCatalog c = UpgradeStoreTest.bundled();
        ConfigurationSection tasks = resource("tasks.yml").getConfigurationSection("tasks");
        assertNotNull(tasks);
        Set<String> keys = new HashSet<>();
        for (String id : tasks.getKeys(false)) {
            keys.addAll(tasks.getStringList(id + ".unlocks"));
        }
        Set<String> missing = new TreeSet<>();
        for (UpgradeCatalog.Def d : c.all()) {
            for (int n = 1; n <= d.maxLevel(); n++) {
                if (!keys.contains(d.unlockKey(n))) {
                    missing.add(d.unlockKey(n));
                }
            }
        }
        assertEquals(Set.of(), missing, "every level is gated by challenge book tasks");
    }

    @Test
    void brokenEntriesAreSkippedWithAReason() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("upgrades.good.tab", "island");
        yaml.set("upgrades.good.effect", "warp-slots");
        yaml.set("upgrades.good.levels", List.of(java.util.Map.of("tier", 1, "cost", 10, "value", 4)));
        yaml.set("upgrades.bad_tab.tab", "nowhere");
        yaml.set("upgrades.bad_tab.effect", "warp-slots");
        yaml.set("upgrades.bad_tab.levels", List.of(java.util.Map.of("tier", 1, "cost", 10, "value", 4)));
        yaml.set("upgrades.even_size.tab", "island");
        yaml.set("upgrades.even_size.effect", "island-size");
        yaml.set("upgrades.even_size.levels", List.of(java.util.Map.of("tier", 1, "cost", 10, "value", 6)));
        yaml.set("upgrades.going_down.tab", "economy");
        yaml.set("upgrades.going_down.effect", "sell-bonus");
        yaml.set("upgrades.going_down.levels", List.of(java.util.Map.of("tier", 1, "cost", 10, "value", 10),
                java.util.Map.of("tier", 2, "cost", 20, "value", 5)));
        yaml.set("upgrades.negative.tab", "economy");
        yaml.set("upgrades.negative.effect", "sell-bonus");
        yaml.set("upgrades.negative.levels", List.of(java.util.Map.of("tier", 1, "cost", -1, "value", 5)));
        yaml.set("upgrades.no_levels.tab", "economy");
        yaml.set("upgrades.no_levels.effect", "sell-bonus");
        yaml.set("upgrades.Bad-Id.tab", "economy");
        List<String> problems = new ArrayList<>();
        UpgradeCatalog c = UpgradeCatalog.load(yaml, id -> true, problems::add);
        assertEquals(1, c.size());
        assertTrue(c.get("good").isPresent());
        assertEquals(6, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.contains("even_size") && p.contains("odd")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("going_down") && p.contains("lower")));
        List<String> none = new ArrayList<>();
        assertEquals(0, UpgradeCatalog.load(new YamlConfiguration(), id -> true, none::add).size());
        assertEquals(1, none.size());
        List<String> errors = new ArrayList<>();
        assertNull(UpgradeCatalog.parse("icon_bad", yaml.getConfigurationSection("upgrades.good"), id -> false, errors));
        assertTrue(errors.get(0).contains("icon"));
    }

    // ------------------------------------------------------------------ messages

    private static final Pattern LITERAL = Pattern.compile("\"((?:upgrades|level-menu\\.source\\.upgrades)(?:\\.[a-z0-9][a-z0-9-]*)+)\"");
    private static final List<String> FILES = List.of("UpgradeManager.java", "UpgradeMenus.java", "UpgradeMenu.java",
            "UpgradeDetailMenu.java", "LevelMenu.java");

    @Test
    void messageKeysExist() throws Exception {
        YamlConfiguration yml = resource("messages/progression.yml");
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/progression");
        Set<String> missing = new TreeSet<>();
        for (String name : FILES) {
            Matcher m = LITERAL.matcher(Files.readString(src.resolve(name)));
            while (m.find()) {
                String key = m.group(1);
                // prefixes completed in code ("upgrades.state." + key, "upgrades.effect." + id ...)
                if (!key.endsWith(".") && !key.endsWith("-") && !yml.contains(key) && !key.equals("upgrades.state") && !key.equals("upgrades.effect")) {
                    missing.add(key + " (" + name + ")");
                }
            }
        }
        for (UpgradeEffect e : UpgradeEffect.values()) {
            check(yml, "upgrades.effect." + e.id(), missing);
        }
        for (UpgradeRules.Status s : UpgradeRules.Status.values()) {
            check(yml, "upgrades.state." + UpgradeMenus.stateKey(s), missing);
        }
        for (UpgradeTab t : UpgradeTab.values()) {
            check(yml, "upgrades.tab." + t.id() + ".crumb", missing);
        }
        for (UpgradeCatalog.Def d : UpgradeStoreTest.bundled().all()) {
            check(yml, "upgrades.name." + d.id(), missing);
        }
        for (String state : List.of("bought", "next", "later")) {
            check(yml, "upgrades.detail.level.name-" + state, missing);
            check(yml, "upgrades.detail.level." + state, missing);
        }
        assertEquals(Set.of(), missing);
    }

    private static void check(YamlConfiguration yml, String key, Set<String> missing) {
        if (!yml.contains(key)) {
            missing.add(key);
        }
    }

    @Test
    void menuSlotsAreInside() {
        for (int slot : UpgradeMenu.UPGRADE_SLOTS) {
            assertInside(slot);
        }
        for (int slot : new int[]{UpgradeMenu.SLOT_BANK, UpgradeMenu.SLOT_LEVEL, UpgradeMenu.SLOT_BOOK, UpgradeDetailMenu.SLOT_BUY}) {
            assertInside(slot);
        }
        for (int slot : UpgradeDetailMenu.TASK_SLOTS) {
            assertInside(slot);
        }
        for (int slot : UpgradeDetailMenu.LEVEL_SLOTS) {
            assertInside(slot);
        }
    }

    private static void assertInside(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        assertTrue(row >= 1 && row <= 4 && col >= 1 && col <= 7, "slot " + slot);
    }
}
