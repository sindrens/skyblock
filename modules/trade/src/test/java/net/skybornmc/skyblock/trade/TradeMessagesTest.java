package net.skybornmc.skyblock.trade;

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

/** Every message key the code uses exists in messages/trade.yml (owner rule: all player-facing text in messages). */
class TradeMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Keys of other files (menus.yml frame) and strings that are not message keys. */
    private static final Set<String> FOREIGN = Set.of("frame.back");

    private static YamlConfiguration messages() throws Exception {
        try (var in = TradeMessagesTest.class.getClassLoader().getResourceAsStream("messages/trade.yml")) {
            assertNotNull(in, "messages/trade.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/trade");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("TradeConfig.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.startsWith("trade:") || key.endsWith(".yml") || FOREIGN.contains(key)) {
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
        List<String> keys = new ArrayList<>();
        for (TradeRules.Problem p : TradeRules.Problem.values()) {
            keys.add("rules." + p.key());
        }
        for (TradeRules.MoneyProblem p : TradeRules.MoneyProblem.values()) {
            keys.add("money." + p.key());
        }
        for (String item : List.of("no-item", "dupe-flagged", "starter", "blocked", "filled-container")) {
            keys.add("items." + item);
        }
        for (net.skybornmc.skyblock.common.economy.Currency c : net.skybornmc.skyblock.common.economy.Currency.values()) {
            keys.add("currency." + c.key());
        }
        for (String reason : List.of("cancelled.you", "cancelled.other", "closed.you", "closed.other", "quit", "damage", "teleport",
                "profile", "frozen", "staff-mode", "distance", "timeout", "changed", "error", "own-profile", "generic")) {
            keys.add("cancel." + reason);
        }
        for (String s : List.of("open", "countdown", "completing", "ended")) {
            keys.add("menu.status." + s + ".name");
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }
}
