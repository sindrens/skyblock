package net.skybornmc.skyblock.essentials;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EssentialsLogicTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void tpaRequestsExpireAndRefuse() {
        TpaRequests requests = new TpaRequests(now::get, 60_000, 2);
        assertEquals(Optional.of(TpaRequests.Refusal.SELF), requests.create(a, a, TpaRequests.Type.TO_TARGET));
        assertTrue(requests.create(a, b, TpaRequests.Type.TO_TARGET).isEmpty());
        assertEquals(Optional.of(TpaRequests.Refusal.ALREADY_SENT), requests.create(a, b, TpaRequests.Type.TO_TARGET));
        assertTrue(requests.create(a, b, TpaRequests.Type.HERE).isEmpty(), "a different type replaces the request");
        assertTrue(requests.create(c, b, TpaRequests.Type.TO_TARGET).isEmpty());
        assertEquals(Optional.of(TpaRequests.Refusal.TARGET_FULL), requests.create(UUID.randomUUID(), b, TpaRequests.Type.TO_TARGET));
        assertEquals(c, requests.find(b, null).orElseThrow().requester(), "newest request first");
        now.addAndGet(60_000);
        assertEquals(2, requests.expire().size());
        assertTrue(requests.find(b, null).isEmpty());
    }

    @Test
    void forgetDropsBothDirections() {
        TpaRequests requests = new TpaRequests(now::get, 60_000, 5);
        requests.create(a, b, TpaRequests.Type.TO_TARGET);
        requests.create(b, c, TpaRequests.Type.HERE);
        assertEquals(2, requests.forget(b).size());
        assertTrue(requests.sentBy(a).isEmpty());
    }

    @Test
    void parsesGameModesAndTimes() {
        assertEquals(GameMode.CREATIVE, StaffCommands.parseMode("c"));
        assertEquals(GameMode.SPECTATOR, StaffCommands.parseMode("sp"));
        assertEquals(GameMode.SURVIVAL, StaffCommands.parseMode("0"));
        assertEquals(1000L, StaffCommands.parseTime("day"));
        assertEquals(18000L, StaffCommands.parseTime("midnight"));
        assertEquals(500L, StaffCommands.parseTime("500"));
    }
}
