package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanTargetsTest {

    private final List<String> banned = List.of("Pouched", "griefer99", ".BedrockGuy", "Grinder");

    @Test
    void matchesIgnoringCase() {
        assertEquals(Optional.of("Pouched"), BanTargets.match(banned, "pouched"));
        assertEquals(Optional.of(".BedrockGuy"), BanTargets.match(banned, ".bedrockguy"));
        assertEquals(Optional.empty(), BanTargets.match(banned, "nobody"));
    }

    @Test
    void suggestsByPrefixSorted() {
        assertEquals(List.of("griefer99", "Grinder"), BanTargets.suggestions(banned, "gr"));
        assertEquals(4, BanTargets.suggestions(banned, "").size());
    }

    @Test
    void detectsIpAddresses() {
        assertTrue(BanTargets.looksLikeIp("192.168.1.20"));
        assertTrue(BanTargets.looksLikeIp("2001:db8::1"));
        assertFalse(BanTargets.looksLikeIp("Pouched"));
        assertFalse(BanTargets.looksLikeIp(".BedrockGuy"));
    }
}
