package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-5: chat vs Bedrock popup, queuing behind an open window, expiry. */
class OfferLogicTest {

    private record Fake(String id, long expiresAt, AtomicBoolean valid) implements OfferQueue.Queued {
        Fake(String id) {
            this(id, 0, new AtomicBoolean(true));
        }

        @Override
        public boolean expired(long now) {
            return Offer.expired(expiresAt, valid::get, now);
        }
    }

    private static final UUID P = UUID.randomUUID();

    // ------------------------------------------------------------------ platform

    @Test
    void javaGetsChatOnlyBedrockGetsThePopup() {
        assertEquals(Offers.Route.CHAT_ONLY, Offers.route(true, false, true));
        assertEquals(Offers.Route.POPUP, Offers.route(true, true, true));
    }

    @Test
    void popupsOffOrNothingToClickMeansChatOnly() {
        assertEquals(Offers.Route.CHAT_ONLY, Offers.route(false, true, true));
        assertEquals(Offers.Route.CHAT_ONLY, Offers.route(true, true, false));
    }

    // ------------------------------------------------------------------ waiting behind an open window

    @Test
    void popupWaitsWhileAWindowOrAChatPromptIsOpen() {
        assertTrue(Offers.mustWait(true, false));
        assertTrue(Offers.mustWait(false, true));
        assertFalse(Offers.mustWait(false, false));
    }

    @Test
    void queueIsFirstInFirstOut() {
        OfferQueue<Fake> q = new OfferQueue<>(5, 0);
        q.add(P, new Fake("a"), 0);
        q.add(P, new Fake("b"), 0);
        assertEquals("a", q.next(P, 1).orElseThrow().id());
        assertEquals("b", q.next(P, 1).orElseThrow().id());
        assertTrue(q.next(P, 1).isEmpty());
        assertFalse(q.has(P));
    }

    @Test
    void sameIdReplacesTheOlderOneAndMovesBack() {
        OfferQueue<Fake> q = new OfferQueue<>(5, 0);
        Fake first = new Fake("tpa:x");
        Fake renewed = new Fake("tpa:x");
        q.add(P, first, 0);
        q.add(P, new Fake("trade:y"), 0);
        q.add(P, renewed, 0);
        assertEquals(2, q.size(P));
        assertEquals("trade:y", q.next(P, 1).orElseThrow().id());
        assertTrue(q.next(P, 1).orElseThrow() == renewed);
    }

    @Test
    void oldestIsDroppedWhenFull() {
        OfferQueue<Fake> q = new OfferQueue<>(2, 0);
        q.add(P, new Fake("a"), 0);
        q.add(P, new Fake("b"), 0);
        q.add(P, new Fake("c"), 0);
        assertEquals(2, q.size(P));
        assertEquals("b", q.next(P, 1).orElseThrow().id());
    }

    @Test
    void playersHaveTheirOwnQueue() {
        OfferQueue<Fake> q = new OfferQueue<>(5, 0);
        UUID other = UUID.randomUUID();
        q.add(P, new Fake("a"), 0);
        assertTrue(q.next(other, 1).isEmpty());
        q.forget(P);
        assertTrue(q.next(P, 1).isEmpty());
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void expiryByTimeAndByValidity() {
        assertFalse(Offer.expired(0, null, Long.MAX_VALUE), "no limit, no check = never expires");
        assertFalse(Offer.expired(1_000, null, 999));
        assertTrue(Offer.expired(1_000, null, 1_000));
        assertTrue(Offer.expired(0, () -> false, 0), "answered or cancelled = expired");
        assertFalse(Offer.expired(1_000, () -> true, 10));
    }

    @Test
    void expiredAndAnsweredOffersAreSkippedInTheQueue() {
        OfferQueue<Fake> q = new OfferQueue<>(5, 0);
        Fake timed = new Fake("timed", 100, new AtomicBoolean(true));
        Fake answered = new Fake("answered", 0, new AtomicBoolean(true));
        q.add(P, timed, 0);
        q.add(P, answered, 0);
        q.add(P, new Fake("live"), 0);
        answered.valid().set(false);
        assertEquals("live", q.next(P, 200).orElseThrow().id());
        assertFalse(q.has(P));
    }

    @Test
    void queuedOffersAgeOut() {
        OfferQueue<Fake> q = new OfferQueue<>(5, 1_000);
        q.add(P, new Fake("old"), 0);
        q.add(P, new Fake("new"), 900);
        assertEquals("new", q.next(P, 1_000).orElseThrow().id());
    }

    // ------------------------------------------------------------------ layout and builder

    @Test
    void buttonsSitOnTheMiddleRow() {
        assertArrayEquals(new int[0], OfferMenu.buttonSlots(0));
        assertArrayEquals(new int[]{13}, OfferMenu.buttonSlots(1));
        assertArrayEquals(new int[]{11, 15}, OfferMenu.buttonSlots(2));
        assertArrayEquals(new int[]{11, 13, 15}, OfferMenu.buttonSlots(3));
        assertThrows(IllegalArgumentException.class, () -> OfferMenu.buttonSlots(4));
    }

    @Test
    void builderLimitsButtonsAndKnowsWhenThereIsAPopup() {
        Offer.Builder b = Offer.builder("essentials", "tpa.popup").id("tpa:1");
        assertFalse(b.build().hasPopup(), "no buttons, no click: chat only");
        b.button(Offer.Button.accept("/tpaccept Steve"));
        Offer offer = b.build();
        assertTrue(offer.hasPopup());
        assertEquals("tpaccept Steve", offer.buttons().getFirst().command(), "the slash is stripped");
        assertEquals("tpa.popup.title", offer.titleKey());
        b.button(Offer.Button.deny("tpdeny Steve")).button(Offer.Button.run("spawn"));
        assertThrows(IllegalArgumentException.class, () -> b.button(Offer.Button.run("menu")));
        assertTrue(Offer.builder("social", "links.popup").onInfoClick(p -> { }).build().hasPopup());
    }
}
