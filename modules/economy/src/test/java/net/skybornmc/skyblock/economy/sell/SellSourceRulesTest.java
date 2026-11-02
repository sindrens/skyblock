package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECON-SELL-SHOP-AH A5, source scans over the sell package: items going back never land on the floor or in the ender chest
 * (inventory, then /box), selling only ever pays money (never shards or credits), and a balance overflow hands the
 * items back with a message.
 */
class SellSourceRulesTest {

    private static final Path SRC = Path.of("src/main/java/net/skybornmc/skyblock/economy/sell");

    private static List<String> offenders(Pattern pattern) throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(SRC)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("*") && !line.startsWith("//") && pattern.matcher(line).find()) {
                        out.add(f.getFileName() + ":" + (i + 1) + " " + line);
                    }
                }
            }
        }
        return out;
    }

    @Test
    void noDropInSell() throws IOException {
        List<String> bad = offenders(Pattern.compile("dropItem(Naturally)?\\s*\\(|getEnderChest\\s*\\("));
        assertTrue(bad.isEmpty(), "sell code must give items back through giveBack (inventory, then /box): " + bad);
    }

    @Test
    void noSellForShards() throws IOException {
        List<String> bad = offenders(Pattern.compile("Currency\\.(SHARDS|CREDITS|CRYSTALS)"));
        assertTrue(bad.isEmpty(), "selling pays money only: " + bad);
    }

    @Test
    void overflowGivesItemsBackWithAMessage() throws IOException {
        String feature = Files.readString(SRC.resolve("SellFeature.java"));
        assertTrue(feature.contains("case OVERFLOW ->") && feature.contains("\"sell.overflow\""),
                "a sale the balance can't hold must tell the player");
        String impl = Files.readString(SRC.resolve("SellServiceImpl.java"));
        assertTrue(impl.contains("OVERFLOW"), "the service must report a balance overflow instead of paying part of it");
    }
}
