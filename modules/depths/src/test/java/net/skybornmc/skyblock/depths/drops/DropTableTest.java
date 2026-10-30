package net.skybornmc.skyblock.depths.drops;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropTableTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void parsesTiersAndReportsBrokenEntries() throws Exception {
        YamlConfiguration y = yaml("""
                drops:
                  mine:
                    1: {item: depths_coal, crystal-chance: 0.05}
                    2: {item: depths_copper, amount: 2, crystal-chance: 0.065, crystal-amount: 3, rare: {depths_geode: 0.01, nope: 0.1, depths_key: 0.0}}
                    x: {item: depths_coal}
                    3: {item: unknown}
                    4: {item: depths_coal, crystal-chance: -1, amount: 99}
                  forest:
                    1: {item: depths_oak_log}
                """);
        List<String> problems = new ArrayList<>();
        DropTable t = DropTable.parse(y.getConfigurationSection("drops"),
                Set.of("depths_coal", "depths_copper", "depths_geode", "depths_oak_log", "depths_key"), problems::add);
        assertEquals(new DropTable.TierDrops("depths_coal", 1, 0.05, 1, List.of()), t.get("mine", 1));
        DropTable.TierDrops two = t.get("mine", 2);
        assertEquals(2, two.amount());
        assertEquals(3, two.crystalAmount());
        assertEquals(0.065 * 3, two.crystalsPerBlock(), 1e-9);
        assertEquals(List.of(new DropTable.RareDrop("depths_geode", 0.01)), two.rare(), "unknown and 0-chance rare finds are left out");
        assertNull(t.get("mine", 3));
        assertEquals(new DropTable.TierDrops("depths_coal", 1, 0, 1, List.of()), t.get("mine", 4));
        assertNull(t.get("combat", 1));
        assertNotNull(t.get("forest", 1));
        assertEquals(5, problems.size(), problems.toString());
    }

    @Test
    void defaultsMatchTheBalanceSheetAndSellIds() {
        List<String> problems = new ArrayList<>();
        YamlConfiguration d = DropSections.defaults(problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        Set<String> items = d.getConfigurationSection(DropSections.ITEMS).getKeys(false);
        DropTable t = DropTable.parse(d.getConfigurationSection(DropSections.DROPS), items, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(12, t.tiers("mine").size());
        assertEquals(11, t.tiers("forest").size());
        // ECONOMY_BALANCE §9.1: crystals per block 0.05 x 1.3^(t-1), three decimals.
        for (int tier = 1; tier <= 12; tier++) {
            assertEquals(0.05 * Math.pow(1.3, tier - 1), t.get("mine", tier).crystalsPerBlock(), 0.0006, "mine tier " + tier);
        }
        // sell.yml (M3.4) proposed exactly these ids.
        String[] mine = {"coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald", "amethyst", "quartz", "netherite", "voidstone"};
        for (int i = 0; i < mine.length; i++) {
            assertEquals("depths_" + mine[i], t.get("mine", i + 1).item());
        }
        String[] forest = {"oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log",
                "pale_oak_log", "crimson_stem"};
        for (int i = 0; i < forest.length; i++) {
            assertEquals("depths_" + forest[i], t.get("forest", i + 1).item());
        }
        assertEquals("depths_warped_stem", t.get("forest", 11).item());
        assertTrue(t.get("mine", 12).rare().isEmpty(), "rare finds are placeholders with chance 0 until their phase");
    }
}
