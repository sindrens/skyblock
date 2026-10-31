package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.AbilityId;
import net.skybornmc.skyblock.common.mob.AbilitySpec;
import net.skybornmc.skyblock.common.mob.MobDef;
import net.skybornmc.skyblock.common.mob.MobStats;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobCatalogTest {

    /** Mob types the shipped file uses (the real check asks the server's entity registry). */
    static final Predicate<String> TYPES = Set.of("zombie", "skeleton", "spider", "cave_spider", "slime", "drowned", "witch",
            "pillager", "vindicator", "blaze", "wither_skeleton", "ravager", "evoker", "bat", "silverfish", "husk", "stray",
            "endermite", "zombified_piglin", "magma_cube", "enderman", "rabbit", "fox", "parrot", "frog", "bee", "strider")::contains;

    /** ECONOMY_BALANCE §3.2 drop value per kill and §9.1-derived crystals per kill, tier 1..10. */
    static final double[] VALUE_PER_KILL = {104, 176, 264, 436, 650, 966, 1600, 2300, 5600, 14700};

    static YamlConfiguration shipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = MobCatalogTest.class.getClassLoader().getResourceAsStream(MobSections.FILE)) {
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return y;
    }

    static MobCatalog shippedCatalog(List<String> problems) throws Exception {
        YamlConfiguration y = shipped();
        Set<String> items = y.getConfigurationSection(MobSections.ITEMS).getKeys(false);
        return MobCatalog.parse(y, items, TYPES, problems::add);
    }

    @Test
    void shippedFileLoadsCleanly() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalog c = shippedCatalog(problems);
        assertTrue(problems.isEmpty(), problems.toString());
        for (int tier = 1; tier <= 10; tier++) {
            ZoneSpawns s = c.combat(tier);
            assertNotNull(s, "combat tier " + tier);
            assertTrue(s.hasMiniBoss(), "mini-boss in tier " + tier);
            MobDef boss = c.mob(s.miniBoss());
            assertTrue(boss.glowing() && boss.scale() > 1, "mini-boss looks like one");
            for (String id : s.mobs().keySet()) {
                MobDef m = c.mob(id);
                assertTrue(m.xp() > 0 && m.crystalChance() > 0 && !m.drops().isEmpty(), id + " pays something");
                assertTrue(boss.health() >= 10 * m.health(), "mini-boss much tougher than " + id);
            }
        }
        for (int tier = 1; tier <= 12; tier++) {
            assertNotNull(c.ambient("mine", tier), "mine ambient " + tier);
        }
        for (int tier = 1; tier <= 11; tier++) {
            assertNotNull(c.ambient("forest", tier), "forest ambient " + tier);
        }
        for (Map<Integer, AmbientSpawns> wing : c.ambient().values()) {
            for (AmbientSpawns a : wing.values()) {
                for (String id : a.mobs().keySet()) {
                    MobDef m = c.mob(id);
                    assertTrue(m.drops().isEmpty() && m.xp() == 0 && m.crystalChance() == 0, id + ": ambient mobs give nothing");
                }
            }
        }
    }

    /** Damage grows with the tier; tiers 1-3 stay gentle for new players. */
    @Test
    void damageScalesByTier() throws Exception {
        MobCatalog c = shippedCatalog(new ArrayList<>());
        double last = 0;
        for (int tier = 1; tier <= 10; tier++) {
            double max = 0;
            for (String id : c.combat(tier).mobs().keySet()) {
                max = Math.max(max, c.mob(id).damage());
            }
            assertTrue(max >= last, "tier " + tier + " hits at least as hard as the tier before");
            if (tier <= 3) {
                assertTrue(max <= 3, "tier " + tier + " is safe for new players");
            }
            last = max;
        }
    }

    /** Drop value per kill (sell.yml prices) stays within 10% of the balance sheet's combat table. */
    @Test
    void dropValueMatchesTheBalanceSheet() throws Exception {
        Path sell = Path.of("../economy/src/main/resources/sell.yml");
        if (!Files.exists(sell)) {
            return; // run from another directory: the economy module's own tests cover sell.yml
        }
        YamlConfiguration prices = new YamlConfiguration();
        prices.loadFromString(Files.readString(sell));
        var combatPrices = prices.getConfigurationSection("custom-items.depths-combat");
        assertNotNull(combatPrices, "sell.yml has the combat drops");
        YamlConfiguration y = shipped();
        for (String item : y.getConfigurationSection(MobSections.ITEMS).getKeys(false)) {
            assertTrue(combatPrices.getInt(item, 0) > 0, item + " has a sell price");
        }
        MobCatalog c = shippedCatalog(new ArrayList<>());
        for (int tier = 1; tier <= 10; tier++) {
            ZoneSpawns s = c.combat(tier);
            long total = s.mobs().values().stream().mapToLong(Integer::longValue).sum();
            double value = 0;
            for (Map.Entry<String, Integer> e : s.mobs().entrySet()) {
                double perKill = 0;
                for (MobDef.Drop d : c.mob(e.getKey()).drops()) {
                    perKill += d.average() * combatPrices.getInt(d.item());
                }
                value += perKill * e.getValue() / total;
            }
            double expected = VALUE_PER_KILL[tier - 1];
            assertEquals(expected, value, expected * 0.1, "tier " + tier + " drop value per kill");
        }
    }

    @Test
    void brokenEntriesAreReportedAndSkipped() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                mobs:
                  good: {type: zombie, health: 20, damage: 2, xp: 5, crystal-chance: 0.5, drops: [{item: flesh, min: 1, max: 3, chance: 0.5}, {item: nope}, {item: flesh, min: 5, max: 2}]}
                  wrong_type: {type: creeper_king}
                  Bad-Id!: {type: zombie}
                  weird: {type: zombie, health: -5, speed: 9}
                  boss: {type: zombie, health: 300}
                combat-zones:
                  1: {mobs: {good: 2, ghost: 1}, base: 4, per-player: 1, max: 10, mini-boss: boss, mini-boss-minutes: 5}
                  2: {mobs: {ghost: 1}}
                  3: {mobs: {good: 1}, mini-boss: nobody}
                  zero: {mobs: {good: 1}}
                ambient:
                  mine:
                    1: {mobs: {good: 1}}
                  combat:
                    1: {mobs: {good: 1}}
                """);
        List<String> problems = new ArrayList<>();
        MobCatalog c = MobCatalog.parse(y, Set.of("flesh"), Set.of("zombie")::contains, problems::add);
        MobDef good = c.mob("good");
        assertEquals(List.of(new MobDef.Drop("flesh", 1, 3, 0.5)), good.drops());
        assertEquals(1.0, good.drops().getFirst().average(), 1e-9);
        assertNull(c.mob("wrong_type"));
        assertEquals(0, c.mob("weird").health(), "out of range: vanilla");
        assertEquals(0, c.mob("weird").speed());
        ZoneSpawns one = c.combat(1);
        assertEquals(Map.of("good", 2), one.mobs());
        assertEquals("boss", one.miniBoss());
        assertEquals(5, one.miniBossMinutes());
        assertNull(c.combat(2));
        assertNull(c.combat(3).miniBoss());
        assertNotNull(c.ambient("mine", 1));
        assertNull(c.ambient("combat", 1));
        // nope, flesh min>max, wrong_type, Bad-Id!, weird.health, weird.speed, ghost (1), ghost (2) + empty zone 2,
        // nobody, zero, ambient.combat
        assertEquals(12, problems.size(), String.join("\n", problems));
    }

    /** M8.1: the live mobs use none of the new stats, so the update changes no live balance. */
    @Test
    void shippedLiveMobsHaveNoNewStats() throws Exception {
        MobCatalog c = shippedCatalog(new ArrayList<>());
        for (MobDef m : c.mobs().values()) {
            assertSame(MobStats.NONE, m.stats(), m.id());
        }
        assertTrue(c.dropTables().isEmpty(), "no shared drop table is live");
    }

    /** M8.1: the shipped ability-defaults repeat the code defaults (so an old mobs.yml without them behaves the same). */
    @Test
    void shippedAbilityDefaultsMatchTheCode() throws Exception {
        MobCatalog c = shippedCatalog(new ArrayList<>());
        for (AbilityId id : AbilityId.values()) {
            AbilitySpec file = c.resolve(AbilitySpec.of(id));
            AbilitySpec code = AbilitySpec.of(id);
            assertEquals(code.cooldownTicks(), file.cooldownTicks(), id.id());
            assertEquals(code.rollChance(), file.rollChance(), 1e-9, id.id());
            for (AbilityId.Param p : id.params().values()) {
                if (p.text()) {
                    assertEquals(code.text(p.key()), file.text(p.key()), id.id() + "." + p.key());
                } else {
                    assertEquals(code.num(p.key()), file.num(p.key()), 1e-9, id.id() + "." + p.key());
                    assertTrue(file.numbers().containsKey(p.key()), "the file lists " + id.id() + "." + p.key());
                }
            }
        }
    }

    /** M8.1: the commented examples ("#~ ") are valid once uncommented. */
    @Test
    void commentedExamplesParseCleanly() throws Exception {
        String text;
        try (var in = MobCatalogTest.class.getClassLoader().getResourceAsStream(MobSections.FILE)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("#~ "), "the examples are still in the file");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text.replace("#~ ", ""));
        List<String> problems = new ArrayList<>();
        MobCatalog c = MobCatalog.parse(y, y.getConfigurationSection(MobSections.ITEMS).getKeys(false), TYPES, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        MobDef brute = c.mob("bog_brute");
        assertEquals("bog_common", brute.stats().dropTable());
        assertEquals(20, brute.stats().armor());
        assertEquals(0.02, brute.stats().eliteChance());
        assertEquals(AbilityId.SLAM, brute.stats().abilities().getFirst().id());
        MobDef warden = c.mob("forge_warden");
        assertEquals(3, warden.stats().abilities().size());
        assertEquals("fire", c.resolve(warden.stats().ability(AbilityId.RING)).text("kind"));
        for (ZoneSpawns z : c.combat().values()) {
            assertTrue(!z.mobs().containsKey("bog_brute") && !"forge_warden".equals(z.miniBoss()), "examples are not spawned");
        }
    }
}
