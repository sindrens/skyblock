package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1: join/leave lines only reach players who keep join-messages on. */
class EssentialsSettingsTest {

    @Test
    void joinLinesSkipPlayersWhoTurnedThemOff() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Set<UUID> off = Set.of(b);
        List<UUID> out = EssentialsSettings.recipients(List.of(a, b, c), id -> !off.contains(id));
        assertEquals(List.of(a, c), out, "keeps order, drops b");
    }

    @Test
    void nobodyOnlineOrEveryoneOff() {
        assertTrue(EssentialsSettings.recipients(List.<UUID>of(), id -> true).isEmpty());
        assertTrue(EssentialsSettings.recipients(List.of(UUID.randomUUID(), UUID.randomUUID()), id -> false).isEmpty());
    }

    @Test
    void settingIdsMatchTheMenuPlaceholders() {
        assertEquals("join-messages", EssentialsSettings.JOIN_MESSAGES);
        assertEquals("tpa-requests", EssentialsSettings.TPA_REQUESTS);
    }
}
