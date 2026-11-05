package net.skybornmc.skyblock.social;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner 4 Nov 2026: /friends add sends a request, or accepts theirs; limits on both sides. */
class FriendsTest {

    private final UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID you = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void addRequestsOrAccepts() {
        assertEquals(Friends.AddResult.SELF, Friends.add(me, me, Set.of(), 0, Set.of(), Set.of(), 50));
        assertEquals(Friends.AddResult.REQUESTED, Friends.add(me, you, Set.of(), 0, Set.of(), Set.of(), 50));
        assertEquals(Friends.AddResult.ALREADY_REQUESTED, Friends.add(me, you, Set.of(), 0, Set.of(), Set.of(you), 50));
        assertEquals(Friends.AddResult.ACCEPTED, Friends.add(me, you, Set.of(), 0, Set.of(you), Set.of(), 50), "they asked first");
        assertEquals(Friends.AddResult.ALREADY, Friends.add(me, you, Set.of(you), 0, Set.of(), Set.of(), 50));
        assertEquals(Friends.AddResult.LIMIT, Friends.add(me, you, Set.of(UUID.randomUUID()), 0, Set.of(), Set.of(), 1));
        assertEquals(Friends.AddResult.THEIR_LIMIT, Friends.add(me, you, Set.of(), 1, Set.of(you), Set.of(), 1));
    }

    @Test
    void pairsAreStoredInOneOrder() {
        assertArrayEquals(Friends.pair(me, you), Friends.pair(you, me));
        assertEquals(me, Friends.pair(you, me)[0]);
    }
}
