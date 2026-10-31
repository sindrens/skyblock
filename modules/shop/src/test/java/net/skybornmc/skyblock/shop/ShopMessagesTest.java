package net.skybornmc.skyblock.shop;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key the code uses exists in messages/shop.yml (owner rule: all player-facing text in messages). */
class ShopMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    static YamlConfiguration messages() throws Exception {
        try (var in = ShopMessagesTest.class.getClassLoader().getResourceAsStream("messages/shop.yml")) {
            assertNotNull(in, "messages/shop.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/shop");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ShopSettings.java")).toList()) {
                String text = Files.readString(file);
                Matcher m = LITERAL.matcher(text);
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("shop:")) {
                        continue;
                    }
                    if (!has(yml, key) && !yml.isConfigurationSection(key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        List<String> keys = new ArrayList<>(List.of("error", "loading", "frozen", "staff-mode", "switching", "unknown-category"));
        for (ShopCatalog.Category c : ShopCatalogTest.catalog().categories()) {
            keys.add("categories." + c.id() + ".name");
            keys.add("categories." + c.id() + ".lore");
        }
        for (String state : List.of("locked", "unavailable", "requires")) {
            keys.add("entry." + state);
        }
        for (String state : List.of("ok", "partial", "no-room", "too-expensive", "armed")) {
            keys.add("buy.button." + state);
        }
        for (String why : List.of("no-room", "too-expensive")) {
            keys.add("buy.fill-none." + why + ".name");
        }
        for (String setting : List.of(ShopModule.SETTING_UNDO, ShopModule.SETTING_BIG_BUY)) {
            keys.add("settings." + setting + ".name");
            keys.add("settings." + setting + ".lore");
        }
        for (String key : List.of("buy.quick.name", "buy.fill.name", "buy.typed.name", "undo.idle.name", "undo.ready.name",
                "menu.spawners-off.name", "menu.empty.name")) {
            keys.add(key);
        }
        for (String skill : List.of("mining", "foraging", "combat", "farming", "fishing")) {
            keys.add("skills." + skill);
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }
}
