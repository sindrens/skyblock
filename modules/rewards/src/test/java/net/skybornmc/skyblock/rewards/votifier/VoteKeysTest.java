package net.skybornmc.skyblock.rewards.votifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Key files: made once, read back the same, tokens per site kept across restarts; limiter and throttled log. */
class VoteKeysTest {

    @TempDir
    Path dir;

    @Test
    void rsaPairIsMadeOnceAndReadBack() throws Exception {
        KeyPair first = VoteKeys.loadOrCreateRsa(dir);
        assertTrue(Files.isRegularFile(dir.resolve("rsa/public.key")));
        assertTrue(Files.isRegularFile(dir.resolve("rsa/private.key")));
        KeyPair again = VoteKeys.loadOrCreateRsa(dir);
        assertArrayEquals(first.getPublic().getEncoded(), again.getPublic().getEncoded());
        assertArrayEquals(first.getPrivate().getEncoded(), again.getPrivate().getEncoded());
        assertEquals(VoteKeys.publicKeyText(first.getPublic()), Files.readString(dir.resolve("rsa/public.key")));
    }

    @Test
    void brokenKeyFilesAreReported() throws Exception {
        Files.createDirectories(dir.resolve("rsa"));
        Files.writeString(dir.resolve("rsa/public.key"), "bm90IGEga2V5");
        Files.writeString(dir.resolve("rsa/private.key"), "bm90IGEga2V5");
        assertThrows(java.io.IOException.class, () -> VoteKeys.loadOrCreateRsa(dir));
    }

    @Test
    void tokensPerSiteAreKept() throws Exception {
        SecureRandom r = new SecureRandom();
        Map<String, String> first = VoteKeys.loadOrCreateTokens(dir, List.of("planetminecraft", "topg"), r);
        assertEquals(2, first.size());
        assertNotEquals(first.get("planetminecraft"), first.get("topg"));
        Map<String, String> again = VoteKeys.loadOrCreateTokens(dir, List.of("planetminecraft", "topg", "minecraft-mp"), r);
        assertEquals(first.get("planetminecraft"), again.get("planetminecraft"));
        assertEquals(first.get("topg"), again.get("topg"));
        assertEquals(26, again.get("minecraft-mp").length());
        // An owner-typed token is used as it is.
        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(VoteKeys.TOKENS)));
        lines.replaceAll(l -> l.startsWith("topg:") ? "topg: MyOwnToken_123" : l);
        Files.write(dir.resolve(VoteKeys.TOKENS), lines);
        assertEquals("MyOwnToken_123", VoteKeys.loadOrCreateTokens(dir, List.of("topg"), r).get("topg"));
    }

    @Test
    void tokenLinesParse() {
        Map<String, String> t = VoteKeys.parseTokens(List.of("# comment", "", "topg: abcdefgh12", "bad line", "x: short",
                "Planet: ABCDEFGHIJKL"));
        assertEquals(Map.of("topg", "abcdefgh12", "planet", "ABCDEFGHIJKL"), t);
    }

    @Test
    void rateLimiterCountsPerAddressAndWindow() {
        IpRateLimiter l = new IpRateLimiter(2, 60_000);
        assertTrue(l.allow("a", 0));
        assertTrue(l.allow("a", 10));
        assertFalse(l.allow("a", 20));
        assertTrue(l.allow("b", 20));
        assertTrue(l.allow("a", 60_000));
        for (int i = 0; i < 5000; i++) {
            l.allow("ip" + i, 200_000);
        }
        assertTrue(l.tracked() <= 4097);
    }

    @Test
    void rejectsAreLoggedAtMostOncePerInterval() {
        List<String> lines = new ArrayList<>();
        ThrottledLog log = new ThrottledLog(60_000, lines::add);
        assertTrue(log.reject("1.1.1.1", "bad", 0));
        assertFalse(log.reject("1.1.1.1", "bad", 1_000));
        assertFalse(log.reject("2.2.2.2", "bad", 59_999));
        assertTrue(log.reject("3.3.3.3", "worse", 60_000));
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("+2 more"), lines.get(1));
    }
}
