package net.skybornmc.skyblock.auction;

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

/** Every message key the code uses exists in messages/auction.yml (owner rule: all player-facing text in messages). */
class AuctionMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Keys of other modules' files (menus.yml) and strings that are not message keys. */
    private static final Set<String> FOREIGN = Set.of("confirm.no");

    private static YamlConfiguration messages() throws Exception {
        try (var in = AuctionMessagesTest.class.getClassLoader().getResourceAsStream("messages/auction.yml")) {
            assertNotNull(in, "messages/auction.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/auction");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("AuctionConfig.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || FOREIGN.contains(key) || key.endsWith(".")) {
                        continue;
                    }
                    // Prefixes completed in code ("rules." + problem) are checked below.
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
        List<String> keys = new ArrayList<>();
        for (ListingRules.Problem p : ListingRules.Problem.values()) {
            keys.add("rules." + p.key());
        }
        for (String item : List.of("no-item", "dupe-flagged", "starter", "note", "blocked", "filled-container")) {
            keys.add("items." + item);
        }
        keys.add("categories.all");
        for (Category c : Category.values()) {
            keys.add("categories." + c.key());
        }
        for (AuctionMenu.Sort s : AuctionMenu.Sort.values()) {
            keys.add("sort." + s.key());
        }
        for (CollectionEntry.Source s : CollectionEntry.Source.values()) {
            keys.add("collection.source." + s.key());
        }
        for (String k : List.of("browse.category", "browse.sort")) {
            keys.add(k + ".name");
            keys.add(k + ".crumb");
            keys.add(k + ".lore");
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }
}
