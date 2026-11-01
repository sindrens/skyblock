package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREDIT-SHOP 5.2: {@code /credit grant} creates bought credits and runs from the console only (Tebex). No LuckPerms grant
 * file may ever set {@code skyblock.credit.grant} to true, and admin and owner carry an explicit false (the owner has '*').
 */
class CreditGrantNodeNeverGrantedTest {

    @Test
    void nobodyGetsTheStoreGrantNode() throws IOException {
        Path dir = Path.of("..").toAbsolutePath().normalize().resolve("server/permissions");
        List<String> problems = new ArrayList<>();
        boolean adminFalse = false;
        boolean ownerFalse = false;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".txt")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String l = line.trim().toLowerCase(Locale.ROOT);
                    if (l.startsWith("#") || !l.contains("skyblock.credit.grant")) {
                        continue;
                    }
                    if (!l.endsWith(" false")) {
                        problems.add(dir.relativize(file) + ": " + line.trim());
                    }
                    adminFalse |= l.startsWith("lp group admin ") && l.endsWith(" false");
                    ownerFalse |= l.startsWith("lp group owner ") && l.endsWith(" false");
                }
            }
        }
        assertTrue(problems.isEmpty(), "skyblock.credit.grant must never be granted:\n" + String.join("\n", problems));
        assertTrue(adminFalse && ownerFalse, "admin and owner need an explicit false for skyblock.credit.grant");
    }
}
