package net.skybornmc.skyblock.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Broadcast cards (owner answer 3, 4 Nov 2026): activity rule, fair rotation, places and per-player rules. */
class BroadcastLogicTest {

    private static final long MIN = 60_000L;
    private static final BroadcastPacing.Rules RULES = new BroadcastPacing.Rules(7 * MIN, 6, 2, 3);
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    // ------------------------------------------------------------------ activity rule

    @Test
    void cardNeedsTimeMessagesChattersAndPlayers() {
        BroadcastPacing pacing = new BroadcastPacing(0);
        for (int i = 0; i < 6; i++) {
            pacing.chat(i % 2 == 0 ? A : B);
        }
        assertFalse(pacing.due(7 * MIN - 1, 10, RULES), "before the interval");
        assertTrue(pacing.due(7 * MIN, 10, RULES));
        assertFalse(pacing.due(7 * MIN, 2, RULES), "below min-online");
    }

    @Test
    void quietChatGetsNoCard() {
        BroadcastPacing pacing = new BroadcastPacing(0);
        for (int i = 0; i < 5; i++) {
            pacing.chat(i % 2 == 0 ? A : B);
        }
        assertFalse(pacing.due(60 * MIN, 50, RULES), "5 of 6 messages");
        pacing.chat(A);
        assertTrue(pacing.due(60 * MIN, 50, RULES));
    }

    @Test
    void oneChatterIsNotEnough() {
        BroadcastPacing pacing = new BroadcastPacing(0);
        for (int i = 0; i < 20; i++) {
            pacing.chat(A);
        }
        assertEquals(20, pacing.messages());
        assertEquals(1, pacing.chatters());
        assertFalse(pacing.due(10 * MIN, 50, RULES));
        pacing.chat(B);
        assertTrue(pacing.due(10 * MIN, 50, RULES));
    }

    @Test
    void sentStartsANewWindowSoCardsNeverStack() {
        BroadcastPacing pacing = new BroadcastPacing(0);
        for (int i = 0; i < 6; i++) {
            pacing.chat(i % 2 == 0 ? A : B);
        }
        assertTrue(pacing.due(30 * MIN, 10, RULES));
        pacing.sent(30 * MIN);
        assertEquals(0, pacing.messages());
        assertEquals(0, pacing.chatters());
        assertEquals(30 * MIN, pacing.windowStart());
        assertFalse(pacing.due(60 * MIN, 10, RULES), "no new chat since the last card: nothing, however long it was");
    }

    @Test
    void zeroThresholdsOnlyNeedTheInterval() {
        BroadcastPacing pacing = new BroadcastPacing(0);
        BroadcastPacing.Rules any = new BroadcastPacing.Rules(MIN, 0, 0, 0);
        assertFalse(pacing.due(MIN - 1, 0, any));
        assertTrue(pacing.due(MIN, 0, any));
    }

    // ------------------------------------------------------------------ per player

    @Test
    void playerRules() {
        long grace = 2 * MIN;
        assertTrue(BroadcastPacing.receives(true, 5 * MIN, grace, false, false));
        assertTrue(BroadcastPacing.receives(true, -1, grace, false, false), "online since before a reload");
        assertTrue(BroadcastPacing.receives(true, grace, grace, false, false));
        assertFalse(BroadcastPacing.receives(true, grace - 1, grace, false, false), "just joined");
        assertFalse(BroadcastPacing.receives(false, 5 * MIN, grace, false, false), "Tips off");
        assertFalse(BroadcastPacing.receives(true, 5 * MIN, grace, true, false), "AFK");
        assertFalse(BroadcastPacing.receives(true, 5 * MIN, grace, false, true), "already at the place");
    }

    // ------------------------------------------------------------------ rotation

    @Test
    void rotationShowsEveryCardOnceBeforeAnyRepeat() {
        BroadcastRotation rotation = new BroadcastRotation(new Random(42));
        List<String> cards = List.of("shop", "farm", "mine", "vote", "daily");
        for (int round = 0; round < 20; round++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < cards.size(); i++) {
                assertTrue(seen.add(rotation.next(cards)), "a repeat inside one round");
            }
            assertEquals(Set.copyOf(cards), seen);
        }
    }

    @Test
    void rotationNeverRepeatsBackToBack() {
        for (long seed = 0; seed < 50; seed++) {
            BroadcastRotation rotation = new BroadcastRotation(new Random(seed));
            List<String> cards = List.of("a", "b", "c");
            String last = null;
            for (int i = 0; i < 300; i++) {
                String next = rotation.next(cards);
                assertNotEquals(last, next, "seed " + seed + " step " + i);
                last = next;
            }
        }
    }

    @Test
    void rotationIsRandomButFair() {
        BroadcastRotation rotation = new BroadcastRotation(new Random(7));
        List<String> cards = List.of("a", "b", "c", "d");
        Map<String, Integer> counts = new HashMap<>();
        Set<List<String>> orders = new HashSet<>();
        for (int round = 0; round < 100; round++) {
            List<String> order = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                String id = rotation.next(cards);
                order.add(id);
                counts.merge(id, 1, Integer::sum);
            }
            orders.add(order);
        }
        cards.forEach(c -> assertEquals(100, counts.get(c)));
        assertTrue(orders.size() > 5, "the order changes between rounds");
    }

    @Test
    void rotationSkipsCardsThatAreGoneAndHandlesTwoOrOne() {
        BroadcastRotation rotation = new BroadcastRotation(new Random(1));
        assertNull(rotation.next(List.of()));
        assertNull(rotation.next(null));
        String first = rotation.next(List.of("a", "b", "c"));
        for (int i = 0; i < 10; i++) {
            String id = rotation.next(List.of("a", "b"));
            assertTrue(id.equals("a") || id.equals("b"));
        }
        assertEquals("solo", rotation.next(List.of("solo")));
        assertEquals("solo", rotation.next(List.of("solo")), "with one card it repeats");
        assertTrue(List.of("a", "b", "c").contains(first));
        String prev = rotation.next(List.of("x", "y"));
        for (int i = 0; i < 20; i++) {
            String id = rotation.next(List.of("x", "y"));
            assertNotEquals(prev, id);
            prev = id;
        }
    }

    // ------------------------------------------------------------------ places

    @Test
    void placesParseAndMatch() {
        BroadcastPlaces.Parsed parsed = BroadcastPlaces.parse(List.of("farm=grind,0,500", "Mine = grind , 1000 , 500",
                "event=events", "", "bad", "x=grind,1", "y=grind,a,b", "z=,1,2", "w=grind,0,-1"));
        assertEquals(3, parsed.places().size());
        assertEquals(5, parsed.problems().size());
        assertTrue(parsed.at("farm", "grind", 12.5));
        assertTrue(parsed.at("farm", "GRIND", -500));
        assertFalse(parsed.at("farm", "grind", 1000), "the mine is not the farm");
        assertTrue(parsed.at("mine", "grind", 1003));
        assertFalse(parsed.at("mine", "world", 1000), "other world");
        assertTrue(parsed.at("event", "events", 123456), "whole world");
        assertFalse(parsed.at("shop", "grind", 0), "a card without a place");
        assertFalse(parsed.at("farm", null, 0));
        assertTrue(BroadcastPlaces.parse(null).places().isEmpty());
    }

    @Test
    void commandRootOfACardCommand() {
        assertEquals("is", Broadcasts.commandRoot("/is upgrade"));
        assertEquals("shop", Broadcasts.commandRoot("shop"));
        assertEquals("shop", Broadcasts.commandRoot(" /Shop "));
        assertNull(Broadcasts.commandRoot("/"));
        assertNull(Broadcasts.commandRoot(null));
    }
}
