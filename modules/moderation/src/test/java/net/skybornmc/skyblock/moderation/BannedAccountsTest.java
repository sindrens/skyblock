package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner #4: a ban clears the freeze. The ban event goes to every account the ban hits, each once. */
class BannedAccountsTest {

    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void banHitsTargetAndOnlineSameAddressPlayersOnce() {
        Punishment ban = TestData.punishment(1, Punishment.Type.BAN, 0, null, null, null);
        Map<UUID, String> online = new LinkedHashMap<>();
        online.put(TestData.STEVE, "Steve");
        online.put(OTHER, "Alt");
        Map<UUID, String> out = PunishmentService.bannedAccounts(ban, online);
        assertEquals(List.of(TestData.STEVE, OTHER), List.copyOf(out.keySet()));
        assertEquals("Alt", out.get(OTHER));
    }

    @Test
    void offlineTargetStillCounts() {
        Punishment ban = TestData.punishment(1, Punishment.Type.BAN, 0, null, null, null);
        assertEquals(Map.of(TestData.STEVE, "Steve"), PunishmentService.bannedAccounts(ban, Map.of()));
    }

    @Test
    void otherTypesHitNobody() {
        for (Punishment.Type type : List.of(Punishment.Type.MUTE, Punishment.Type.WARN, Punishment.Type.KICK)) {
            Punishment p = TestData.punishment(1, type, 0, null, null, null);
            assertTrue(PunishmentService.bannedAccounts(p, Map.of(TestData.STEVE, "Steve")).isEmpty());
        }
    }
}
