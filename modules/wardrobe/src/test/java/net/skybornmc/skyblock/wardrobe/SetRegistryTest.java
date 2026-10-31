package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled sets.yml follows WARDROBE §2 (16 sets, tiers, sources, prices, gates) and is Bedrock-safe (§10). */
class SetRegistryTest {

    @Test
    void all16SetsLoad() {
        SetRegistry r = Fixtures.registry();
        assertEquals(16, r.size());
        assertEquals(List.of("delver", "woodsman", "tiller", "footman", "netcaster", "banneret", "lorekeeper", "deepforge", "thornward",
                "cropwarden", "dreadguard", "stormkeel", "coinmaster", "voidforged", "conqueror", "phoenix"), r.ids());
    }

    @Test
    void tiersRaritiesAndMerging() {
        for (SetDef d : Fixtures.registry().all()) {
            assertTrue(d.enabled(), d.id());
            assertEquals(switch (d.tier()) {
                case 1 -> Rarity.UNCOMMON;
                case 2 -> Rarity.RARE;
                case 3 -> Rarity.EPIC;
                default -> Rarity.LEGENDARY;
            }, d.rarity(), d.id());
            assertEquals(d.tier() < 4, d.mergeable(), d.id() + ": tier IV never merges");
            if (d.tier() == 4) {
                assertTrue(d.families().size() >= 2 || d.id().equals("conqueror"), d.id() + " covers several families");
            }
        }
    }

    @Test
    void pricesAndSourcesFollowThePlan() {
        SetRegistry r = Fixtures.registry();
        for (String id : List.of("delver", "woodsman", "tiller", "footman", "netcaster")) {
            assertSource(r, id, SetDef.SourceType.MONEY, 20_000);
        }
        assertSource(r, "banneret", SetDef.SourceType.MONEY, 2_500_000);
        assertSource(r, "coinmaster", SetDef.SourceType.MONEY, 40_000_000);
        for (String id : List.of("deepforge", "cropwarden", "dreadguard")) {
            assertSource(r, id, SetDef.SourceType.CRYSTALS, 25_000);
        }
        assertSource(r, "lorekeeper", SetDef.SourceType.CREDITS, 400);
        assertSource(r, "thornward", SetDef.SourceType.CREDITS, 900);
        assertSource(r, "stormkeel", SetDef.SourceType.CREDITS, 900);
        for (String id : List.of("voidforged", "conqueror", "phoenix")) {
            SetDef d = r.get(id).orElseThrow();
            assertEquals(SetDef.SourceType.CRATE, d.source().type());
            assertFalse(d.source().buyable());
            assertFalse(d.source().crates().isEmpty());
        }
        // No power is gem-only: every gem set is also in a crate.
        for (SetDef d : r.all()) {
            if (d.source().type() == SetDef.SourceType.CREDITS) {
                assertFalse(d.source().crates().isEmpty(), d.id() + " must also drop from a crate");
                assertTrue(d.tier() <= 3, "gem sets are never the top tier");
            }
        }
        assertEquals(Currency.CREDITS_EARNED, r.get("lorekeeper").orElseThrow().source().type().currency());
    }

    private static void assertSource(SetRegistry r, String id, SetDef.SourceType type, long price) {
        SetDef d = r.get(id).orElseThrow();
        assertEquals(type, d.source().type(), id);
        assertEquals(price, d.source().price(), id);
        assertTrue(d.source().buyable(), id);
    }

    @Test
    void gatesAndFeatures() {
        SetRegistry r = Fixtures.registry();
        assertTrue(r.get("delver").orElseThrow().gate().open());
        SetDef lore = r.get("lorekeeper").orElseThrow();
        assertEquals(5, lore.gate().tier());
        assertEquals(25, lore.gate().anySkill());
        assertEquals("mining", r.get("deepforge").orElseThrow().gate().wing());
        assertEquals(8, r.get("coinmaster").orElseThrow().gate().tier());
        assertEquals("fishing", r.get("stormkeel").orElseThrow().gate().skill());
        assertEquals(40, r.get("stormkeel").orElseThrow().gate().skillLevel());
        assertEquals("conquest", r.get("banneret").orElseThrow().enabledFrom());
        assertEquals("conquest", r.get("conqueror").orElseThrow().enabledFrom());
        assertEquals("fishing", r.get("netcaster").orElseThrow().enabledFrom());
        assertEquals("", r.get("phoenix").orElseThrow().enabledFrom());
    }

    @Test
    void bonusesUseKnownFamiliesAndTheSellSetMaxIsBelowTheCap() {
        SetRegistry r = Fixtures.registry();
        assertEquals(Set.of(BonusFamily.CROP_GROWTH, BonusFamily.EXTRA_DROPS), r.get("cropwarden").orElseThrow().families());
        assertTrue(r.get("banneret").orElseThrow().families().contains(BonusFamily.CONQUEST));
        SetDef coin = r.get("coinmaster").orElseThrow();
        double sellAt1x = coin.two().stream().mapToDouble(SetDef.Bonus::value).sum() + coin.four().stream().mapToDouble(SetDef.Bonus::value).sum();
        assertEquals(25, sellAt1x, 1e-9, "+25% at 1x, +50% at level 50 (#46: 1/6 of the +300% cap)");
    }

    @Test
    void namesFitBedrockAndTextsExist() {
        YamlConfiguration messages = Fixtures.resource("messages/wardrobe.yml");
        Set<String> missing = new TreeSet<>();
        for (SetDef d : Fixtures.registry().all()) {
            assertTrue(d.name().length() <= 16, d.id() + " name ≤ 16");
            for (ArmorPiece p : ArmorPiece.values()) {
                String piece = d.shortName() + " " + messages.getString("piece-name." + p.key(), "");
                assertTrue(piece.length() <= 24, piece + " ≤ 24");
            }
            List<SetDef.Bonus> all = new ArrayList<>(d.two());
            all.addAll(d.four());
            for (SetDef.Bonus b : all) {
                if (!messages.contains("bonus-text." + b.text())) {
                    missing.add("bonus-text." + b.text());
                }
                String line = " ▸ " + messages.getString("bonus-text." + b.text(), "").replace("{value}", "99.99");
                assertTrue(line.length() <= 36, line);
            }
            for (String c : d.source().crates()) {
                if (!messages.contains("crate-name." + c)) {
                    missing.add("crate-name." + c);
                }
            }
            for (String key : List.of("activity-name." + d.activity(), "filter." + d.group())) {
                if (!messages.contains(key)) {
                    missing.add(key);
                }
            }
            if (!d.enabledFrom().isEmpty() && !messages.contains("feature-name." + d.enabledFrom())) {
                missing.add("feature-name." + d.enabledFrom());
            }
            if (!d.gate().open() && !messages.contains("wing-name." + d.gate().wing())) {
                missing.add("wing-name." + d.gate().wing());
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void brokenSetsAreSkippedWithAProblem() {
        YamlConfiguration yaml = new YamlConfiguration();
        copy(yaml, "sets.good", "delver");
        yaml.set("sets.bad.name", "A name that is far too long for Bedrock");
        yaml.set("sets.bad.short-name", "Bad");
        yaml.set("sets.bad.activity", "dancing");
        yaml.set("sets.bad.tier", 9);
        copy(yaml, "sets.nofamily", "delver");
        yaml.set("sets.nofamily.bonuses.two", List.of(Map.of("family", "luck", "value", 5, "text", "x")));
        List<String> problems = new ArrayList<>();
        SetRegistry r = SetRegistry.load(yaml, problems::add);
        assertEquals(List.of("good"), r.ids());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad") && p.contains("name")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("nofamily") && p.contains("unknown bonus family")));
    }

    @Test
    void crateSetsNeedCratesAndPricesMustBePositive() {
        YamlConfiguration yaml = new YamlConfiguration();
        copy(yaml, "sets.free", "delver");
        yaml.set("sets.free.source.price", 0);
        copy(yaml, "sets.crate", "phoenix");
        yaml.set("sets.crate.source.crates", List.of());
        List<String> problems = new ArrayList<>();
        assertEquals(0, SetRegistry.load(yaml, problems::add).size());
        assertEquals(2, problems.size(), problems.toString());
    }

    private static void copy(YamlConfiguration into, String path, String set) {
        var src = Fixtures.resource("sets.yml").getConfigurationSection("sets." + set);
        for (String key : src.getKeys(true)) {
            if (!src.isConfigurationSection(key)) {
                into.set(path + "." + key, src.get(key));
            }
        }
    }
}
