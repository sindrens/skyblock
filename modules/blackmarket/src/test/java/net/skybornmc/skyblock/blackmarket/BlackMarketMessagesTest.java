package net.skybornmc.skyblock.blackmarket;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key the code uses exists in messages/blackmarket.yml (owner rule: all player-facing text in messages). */
class BlackMarketMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = BlackMarketMessagesTest.class.getClassLoader().getResourceAsStream("messages/blackmarket.yml")) {
            assertNotNull(in, "messages/blackmarket.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name") || yml.isConfigurationSection(key);
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/blackmarket");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("BlackMarketSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("blackmarket.")) {
                        continue;
                    }
                    if (!has(yml, key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() {
        YamlConfiguration yml = yml();
        List<String> keys = new ArrayList<>(List.of("text", "disabled", "menu.title", "menu.info", "menu.clock.name", "menu.wallet.name",
                "menu.disabled.name", "menu.empty.name", "tag.display", "tag.source"));
        for (OfferCatalog.Period p : OfferCatalog.Period.values()) {
            keys.add("periods." + p.id());
        }
        for (String currency : List.of("shards", "gems")) {
            keys.add("currency." + currency);
        }
        // Every problem a menu item can show has a short state line.
        for (String problem : List.of("disabled", "buy.unavailable", "buy.rotated", "buy.sold-out", "buy.owned", "buy.limit",
                "charter.no-island", "charter.needs-normal", "charter.maxed", "charter.no-step", "charter.no-permission")) {
            keys.add(problem);
            keys.add("menu.state." + BlackMarketMenu.stateKey(problem));
        }
        for (OfferCatalog.Offer o : OfferCatalog.parse(new BlackMarketSettings().offers, p -> { }).all()) {
            keys.add("offers." + o.id() + ".name");
        }
        Set<String> missing = new TreeSet<>();
        for (String k : keys) {
            if (!has(yml, k)) {
                missing.add(k);
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void rowSlotsSpreadOutAndStayInsideTheRow() {
        assertArrayEquals(new int[]{13}, BlackMarketMenu.rowSlots(1, 10));
        assertArrayEquals(new int[]{20, 22, 24}, BlackMarketMenu.rowSlots(3, 19));
        assertArrayEquals(new int[]{28, 30, 32, 34}, BlackMarketMenu.rowSlots(4, 28));
        assertArrayEquals(new int[]{38, 39, 40, 41, 42}, BlackMarketMenu.rowSlots(5, 37));
        assertArrayEquals(new int[]{37, 38, 39, 40, 41, 42, 43}, BlackMarketMenu.rowSlots(9, 37));
        assertArrayEquals(new int[0], BlackMarketMenu.rowSlots(0, 37));
    }
}
