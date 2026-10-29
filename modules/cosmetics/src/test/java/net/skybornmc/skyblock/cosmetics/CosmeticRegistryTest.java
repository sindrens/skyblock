package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticRegistryTest {

    @Test
    void parsesEntriesAndReportsProblems() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                titles:
                  founder:
                    display: "<grad:phoenix>Founder</grad>"
                    rarity: legendary
                    source: "Pre-season"
                  night_owl:
                    display: "Night Owl"
                    rarity: shiny
                    secret: true
                  Bad-Id:
                    display: "x"
                """);
        List<String> problems = new ArrayList<>();
        Map<String, Cosmetic> titles = CosmeticRegistry.parse(yaml.getConfigurationSection("titles"), Cosmetics.Kind.TITLE, problems::add);
        assertEquals(2, titles.size());
        assertEquals(2, problems.size(), "unknown rarity + invalid id");
        assertEquals(Rarity.LEGENDARY, titles.get("founder").rarity());
        assertEquals(Rarity.COMMON, titles.get("night_owl").rarity());
        assertTrue(titles.get("night_owl").secret());
        assertTrue(titles.get("founder").seasonPersistent());
        assertEquals(List.of("founder", "night_owl"), List.copyOf(titles.keySet()));
    }

    @Test
    void ids() {
        assertTrue(Cosmetic.validId("og"));
        assertFalse(Cosmetic.validId("OG"));
        assertFalse(Cosmetic.validId("with space"));
        assertThrows(IllegalArgumentException.class, () -> new Cosmetic(Cosmetics.Kind.TAG, "x y", "d", Rarity.COMMON, "", false, true));
    }

    @Test
    void withersKeepEverythingElse() {
        Cosmetic c = new Cosmetic(Cosmetics.Kind.TAG, "og", "[OG]", Rarity.EPIC, "Store", false, true);
        Cosmetic d = c.withRarity(Rarity.MYTHIC).withDisplay("&c[OG]");
        assertEquals("og", d.id());
        assertEquals("Store", d.source());
        assertEquals(Rarity.MYTHIC, d.rarity());
        assertEquals("&c[OG]", d.display());
    }
}
