package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECON-SHARDS H11 (owner 1 Nov 2026: "shards only from blocks, no other rewards"): only the shard block pays shards. Owner 3 Nov 2026: island tools too (shards from grinding, upgrades cost shards). Code
 * that names {@code Currency.SHARDS} may live only in the shard block module (payout, upgrade prices), the Black Market
 * (prices and refunds of its own shard purchases) and the economy module (balances, /eco, transfers). Owner 4 Nov 2026:
 * the same for {@code Currency.MINING_SHARDS} (only the island pickaxe pays them). A new reward, crate
 * or quest that pays shards fails the build here.
 */
class NoShardPayoutTest {

    /** Owner 3 Nov 2026: island tools find shards and their upgrades cost shards (islandtools). */
    static final Set<String> ALLOWED = Set.of("shardblock", "blackmarket", "economy", "islandtools");

    @Test
    void onlyShardBlocksPayShards() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> bad = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root.resolve("modules"))) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                if (ALLOWED.contains(module.getFileName().toString())) {
                    continue;
                }
                scan(module.resolve("src/main/java"), bad);
            }
        }
        scan(root.resolve("plugin/src/main/java"), bad);
        assertTrue(bad.isEmpty(), "shards come only from shard blocks (ECON-SHARDS H11):\n" + String.join("\n", bad));
    }

    private static void scan(Path dir, List<String> bad) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if ((line.contains("Currency.SHARDS") || line.contains("Currency.MINING_SHARDS") || line.contains("Currency.FISHING_SHARDS") || line.contains("Currency.COMBAT_SHARDS"))
                            && !line.contains(".balance(")) { // reading a balance (wipe summary) pays nothing
                        bad.add(f + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
    }
}
