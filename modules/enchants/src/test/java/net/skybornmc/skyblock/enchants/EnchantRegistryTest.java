package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled enchants.yml follows the plan (ENCHANTS §6) and every enchant has Bedrock-safe texts (§11). */
class EnchantRegistryTest {

    @Test
    void all48EnchantsLoad() {
        EnchantRegistry r = Fixtures.registry();
        assertEquals(48, r.size());
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        r.all().forEach(d -> counts.merge(d.rarity(), 1, Integer::sum));
        assertEquals(7, counts.get(Rarity.COMMON));
        assertEquals(10, counts.get(Rarity.UNCOMMON));
        assertEquals(14, counts.get(Rarity.RARE));
        assertEquals(8, counts.get(Rarity.EPIC));
        assertEquals(3, counts.get(Rarity.LEGENDARY));
        assertEquals(6, counts.get(Rarity.MYTHIC));
    }

    @Test
    void groupsSlotsAndConflictsFollowThePlan() {
        EnchantRegistry r = Fixtures.registry();
        Set<String> twoSlot = r.all().stream().filter(d -> d.slotCost() == 2).map(EnchantDef::id).collect(Collectors.toSet());
        assertEquals(Set.of("dragonfire_blast", "wildfire"), twoSlot);
        for (EnchantDef d : r.all()) {
            if (d.mythic()) {
                assertEquals(1, d.maxLevel(), d.id());
                assertTrue(d.pools().isEmpty(), d.id() + " never drops from pools");
                assertTrue(d.conflicts().contains(EnchantDef.MYTHIC_GROUP), d.id());
            }
            assertTrue(d.enabled(), d.id());
        }
        assertTrue(Fixtures.def("vein_rend").conflictsWith(Fixtures.def("dragonfire_blast")));
        assertTrue(Fixtures.def("treefeller").conflictsWith(Fixtures.def("wildfire")));
        assertTrue(Fixtures.def("ember_momentum").conflictsWith(Fixtures.def("phoenix_haste")));
        assertTrue(Fixtures.def("harvest_moon").conflictsWith(Fixtures.def("sunfire_harvest")));
        assertTrue(Fixtures.def("worldbreaker").conflictsWith(Fixtures.def("phoenix_rebirth")), "one Mythic per item");
        assertFalse(Fixtures.def("worldbreaker").conflictsWith(Fixtures.def("vein_rend")), "Mythic + area is allowed");
        assertFalse(Fixtures.def("swiftness").conflictsWith(Fixtures.def("prosperity")));
        // per tool: 9 shared + 4 pickaxe enchants fit a pickaxe; 12 sword enchants + 4 shared fit a sword
        long pick = r.all().stream().filter(d -> d.fits(ToolKind.PICKAXE)).count();
        assertEquals(13, pick);
        long sword = r.all().stream().filter(d -> d.fits(ToolKind.SWORD)).count();
        assertEquals(12 + 4, sword, "12 sword enchants + Scholar's Insight, Crystal Seeker, Tempering, Treasure Hunter");
        long armor = r.all().stream().filter(EnchantDef::armorOnly).count();
        assertEquals(9, armor);
    }

    @Test
    void numbersMatchThePlan() {
        assertEquals(30, Fixtures.def("swiftness").value("percent", 5));
        assertEquals(48, Fixtures.def("slaughter").value("count", 10));
        assertEquals(12, Fixtures.def("vein_rend").value("chance", 2));
        assertEquals(50, Fixtures.def("vein_rend").value("yield", 3), "constant params work at any level");
        assertTrue(Double.isNaN(Fixtures.def("vein_rend").value("nope", 1)));
        assertEquals(BonusFamily.SPEED, Fixtures.def("swiftness").family());
        assertEquals(30, Fixtures.def("swiftness").passiveValue(5));
        assertEquals(0, Fixtures.def("gilded_touch").passiveValue(5), "a chance effect is not passive");
        assertEquals(40, Fixtures.def("phoenix_edge").pvpPercent());
        assertEquals(5, Fixtures.def("vein_rend").minTier());
    }

    @Test
    void brokenEntriesAreSkippedWithAReason() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enchants.good.rarity", "common");
        yml.set("enchants.good.max-level", 2);
        yml.set("enchants.good.tools", List.of("pickaxe"));
        yml.set("enchants.good.pools", List.of("mining"));
        yml.set("enchants.good.values.percent", List.of(1, 2));
        yml.set("enchants.bad.rarity", "shiny");
        yml.set("enchants.bad.max-level", 3);
        yml.set("enchants.bad.tools", List.of("spoon"));
        yml.set("enchants.bad.values.percent", List.of(1, 2));
        yml.set("enchants.mythic_two.rarity", "mythic");
        yml.set("enchants.mythic_two.max-level", 2);
        yml.set("enchants.mythic_two.tools", List.of("axe"));
        List<String> problems = new ArrayList<>();
        EnchantRegistry r = EnchantRegistry.load(yml, problems::add);
        assertEquals(Set.of("good"), r.ids());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad.rarity")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad.tools")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad.values.percent")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("mythic_two.max-level")), problems.toString());
    }

    // ------------------------------------------------------------------ texts (§11)

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z-]+)}");

    @Test
    void everyEnchantHasAShortNameAndAnEffectThatFitsTwoLines() {
        YamlConfiguration messages = Fixtures.resource("messages/enchants.yml");
        EnchantRegistry r = Fixtures.registry();
        Set<String> problems = new TreeSet<>();
        for (EnchantDef d : r.all()) {
            String name = messages.getString("enchant." + d.id() + ".name");
            String effect = messages.getString("enchant." + d.id() + ".effect");
            if (name == null || effect == null) {
                problems.add(d.id() + ": name or effect missing");
                continue;
            }
            if (name.length() > 17) {
                problems.add(d.id() + ": name longer than 17 characters");
            }
            if ((name + " " + EnchantText.roman(d.maxLevel())).length() + 2 > 36) {
                problems.add(d.id() + ": name + level does not fit a 36-character lore line");
            }
            Matcher m = TOKEN.matcher(effect);
            while (m.find()) {
                if (!d.values().containsKey(m.group(1)) && !d.params().containsKey(m.group(1))) {
                    problems.add(d.id() + ": effect uses {" + m.group(1) + "} which is not in values/params");
                }
            }
            for (int level = 1; level <= d.maxLevel(); level++) {
                String text = EnchantText.fill(effect, d, level);
                List<String> lines = EnchantText.wrap(text, LoreBuilder.DESCRIPTION_WIDTH);
                if (lines.size() > LoreBuilder.DESCRIPTION_MAX_LINES) {
                    problems.add(d.id() + " level " + level + ": effect needs " + lines.size() + " lines: " + text);
                }
                if (lines.stream().anyMatch(l -> l.length() > LoreBuilder.DESCRIPTION_WIDTH)) {
                    problems.add(d.id() + " level " + level + ": a word is too long");
                }
            }
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void numbersInTextsAreReadable() {
        assertEquals("1.5", EnchantText.number(1.5));
        assertEquals("0.05", EnchantText.number(0.05));
        assertEquals("19,836", EnchantText.number(19836));
        assertEquals("3", EnchantText.number(3.0));
        assertEquals("X", EnchantText.roman(10));
        assertEquals("12% chance to break 4 nearby ores of yours at 50% yield.",
                EnchantText.fill(Fixtures.resource("messages/enchants.yml").getString("enchant.vein_rend.effect"), Fixtures.def("vein_rend"), 2));
    }
}
