package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownsTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Cooldowns cooldowns = new Cooldowns(now::get);

    @Test
    void pruneDropsExpiredEntriesAndEmptyPlayers() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        cooldowns.set(a, "tpa", Duration.ofSeconds(5));
        cooldowns.set(b, "tpa", Duration.ofSeconds(60));
        cooldowns.set(b, "report", Duration.ofSeconds(5));
        now.addAndGet(10_000);
        assertEquals(2, cooldowns.prune());
        assertEquals(1, cooldowns.size());
        assertTrue(cooldowns.ready(a, "tpa"));
        assertFalse(cooldowns.ready(b, "tpa"), "a running cooldown survives the prune");
    }

    @Test
    void runningCooldownIsKeptSoARelogCannotSkipIt() {
        UUID a = UUID.randomUUID();
        assertTrue(cooldowns.tryUse(a, "pay", Duration.ofSeconds(30)));
        assertEquals(0, cooldowns.prune());
        assertFalse(cooldowns.tryUse(a, "pay", Duration.ofSeconds(30)));
    }

    @Test
    void writesPruneByThemselves() {
        for (int i = 0; i < Cooldowns.PRUNE_EVERY - 1; i++) {
            cooldowns.set(UUID.randomUUID(), "k", Duration.ofMillis(1));
        }
        now.addAndGet(5);
        assertEquals(Cooldowns.PRUNE_EVERY - 1, cooldowns.size());
        cooldowns.set(UUID.randomUUID(), "k", Duration.ofSeconds(10)); // the 256th write prunes the expired ones
        assertEquals(1, cooldowns.size());
    }
}
