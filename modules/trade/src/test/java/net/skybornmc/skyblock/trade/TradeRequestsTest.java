package net.skybornmc.skyblock.trade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRequestsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void requestsExpire() {
        TradeRequests r = new TradeRequests();
        r.send(a, b, 1000, 60_000);
        assertTrue(r.has(a, b, 1000));
        assertFalse(r.has(b, a, 1000));
        assertEquals(List.of(a), r.incoming(b, 30_000));
        assertFalse(r.has(a, b, 61_000));
        List<UUID[]> expired = r.prune(61_000);
        assertEquals(1, expired.size());
        assertEquals(a, expired.getFirst()[0]);
        assertEquals(b, expired.getFirst()[1]);
        assertTrue(r.incoming(b, 61_000).isEmpty());
    }

    @Test
    void oldestFirstAndRemove() {
        TradeRequests r = new TradeRequests();
        r.send(a, c, 0, 60_000);
        r.send(b, c, 10, 60_000);
        assertEquals(List.of(a, b), r.incoming(c, 20));
        assertTrue(r.remove(a, c, 20));
        assertFalse(r.remove(a, c, 20));
        assertEquals(List.of(b), r.incoming(c, 20));
    }

    @Test
    void cooldown() {
        TradeRequests r = new TradeRequests();
        assertEquals(0, r.cooldownLeft(a, 0, 5000));
        r.send(a, b, 1000, 60_000);
        assertEquals(4000, r.cooldownLeft(a, 2000, 5000));
        assertEquals(0, r.cooldownLeft(a, 7000, 5000));
        assertEquals(0, r.cooldownLeft(a, 2000, 0));
    }

    @Test
    void forgetRemovesBothDirections() {
        TradeRequests r = new TradeRequests();
        r.send(a, b, 0, 60_000);
        r.send(c, a, 0, 60_000);
        r.forget(a);
        assertTrue(r.incoming(b, 1).isEmpty());
        assertTrue(r.incoming(a, 1).isEmpty());
    }
}
