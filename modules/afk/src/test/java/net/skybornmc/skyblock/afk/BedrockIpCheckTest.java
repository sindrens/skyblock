package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R3-3 / brief §3.9: Bedrock players sharing Geyser's or a proxy's IP are spotted. */
class BedrockIpCheckTest {

    @Test
    void realIpsAreFine() {
        assertTrue(BedrockIpCheck.check(Map.of(UUID.randomUUID(), "81.1.1.1", UUID.randomUUID(), "81.2.2.2"), 3).isEmpty());
        assertTrue(BedrockIpCheck.check(Map.of(), 3).isEmpty());
    }

    @Test
    void loopbackIsAlwaysAFinding() {
        var f = BedrockIpCheck.check(Map.of(UUID.randomUUID(), "127.0.0.1"), 3).orElseThrow();
        assertTrue(f.loopback());
        assertEquals("127.0.0.1", f.ip());
        assertTrue(BedrockIpCheck.loopback("::1"));
        assertTrue(BedrockIpCheck.loopback("0:0:0:0:0:0:0:1"));
    }

    @Test
    void manyOnOneIpIsAFinding() {
        Map<UUID, String> ips = Map.of(UUID.randomUUID(), "10.0.0.5", UUID.randomUUID(), "10.0.0.5", UUID.randomUUID(), "10.0.0.5",
                UUID.randomUUID(), "81.1.1.1");
        var f = BedrockIpCheck.check(ips, 3).orElseThrow();
        assertEquals("10.0.0.5", f.ip());
        assertEquals(3, f.players());
        assertTrue(BedrockIpCheck.check(ips, 4).isEmpty());
    }
}
