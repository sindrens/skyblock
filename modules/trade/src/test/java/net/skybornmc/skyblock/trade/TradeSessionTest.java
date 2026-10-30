package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trade state machine: confirmations, resets, countdown, locking. */
class TradeSessionTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private long row = 100;

    private TradeSession session() {
        return new TradeSession(1, new TradeSession.Side(alice, UUID.randomUUID(), "Alice"),
                new TradeSession.Side(bob, UUID.randomUUID(), "Bob"), 0);
    }

    private Offer stored(String key) {
        return new Offer(++row, new byte[]{1}, key, null, "STONE", 1, key);
    }

    private void add(TradeSession s, UUID player, String key) {
        assertTrue(s.beginAdd(player, 12));
        s.finishAdd(player, stored(key));
    }

    @Test
    void bothConfirmStartsTheCountdown() {
        TradeSession s = session();
        add(s, alice, "sword");
        assertEquals(TradeSession.Confirm.READY, s.confirm(alice, 1000, 3000));
        assertEquals(TradeSession.Confirm.ALREADY, s.confirm(alice, 1000, 3000));
        assertEquals(TradeSession.State.OPEN, s.state());
        assertEquals(TradeSession.Confirm.COUNTDOWN, s.confirm(bob, 2000, 3000));
        assertEquals(TradeSession.State.COUNTDOWN, s.state());
        assertEquals(3, s.secondsLeft(2000));
        assertEquals(1, s.secondsLeft(4500));
        assertFalse(s.countdownDone(4999));
        assertTrue(s.countdownDone(5000));
        assertTrue(s.beginCompleting());
        assertEquals(TradeSession.State.COMPLETING, s.state());
    }

    @Test
    void anyItemChangeResetsBothConfirmations() {
        TradeSession s = session();
        add(s, alice, "sword");
        s.confirm(alice, 0, 3000);
        s.confirm(bob, 0, 3000);
        assertEquals(TradeSession.State.COUNTDOWN, s.state());
        // Bob adds an item during the countdown: everything resets.
        assertTrue(s.beginAdd(bob, 12));
        assertEquals(TradeSession.State.OPEN, s.state());
        assertFalse(s.a.ready());
        assertFalse(s.b.ready());
        assertEquals(TradeSession.Confirm.BUSY, s.confirm(alice, 0, 3000)); // still being stored
        s.finishAdd(bob, stored("apple"));
        assertEquals(TradeSession.Confirm.READY, s.confirm(alice, 0, 3000));
        // Taking an item back resets too.
        long id = s.b.items().getFirst().rowId();
        assertTrue(s.beginRemove(bob, id).isPresent());
        assertFalse(s.a.ready());
        s.finishRemove(bob);
        assertTrue(s.b.items().isEmpty());
    }

    @Test
    void moneyChangeResetsConfirmations() {
        TradeSession s = session();
        assertTrue(s.setMoney(alice, Currency.MONEY, 500));
        s.confirm(alice, 0, 3000);
        s.confirm(bob, 0, 3000);
        assertEquals(TradeSession.State.COUNTDOWN, s.state());
        assertTrue(s.setMoney(alice, Currency.MONEY, 400));
        assertEquals(TradeSession.State.OPEN, s.state());
        assertFalse(s.a.ready());
        assertFalse(s.b.ready());
        assertFalse(s.setMoney(alice, Currency.MONEY, 400), "same amount is no change");
        assertFalse(s.setMoney(alice, Currency.MONEY, -1));
        assertTrue(s.setMoney(alice, Currency.MONEY, 0));
        assertTrue(s.a.money().isEmpty());
    }

    @Test
    void unconfirmStopsTheCountdown() {
        TradeSession s = session();
        add(s, alice, "sword");
        s.confirm(alice, 0, 3000);
        s.confirm(bob, 0, 3000);
        assertTrue(s.unconfirm(bob));
        assertEquals(TradeSession.State.OPEN, s.state());
        assertTrue(s.a.ready());
        assertFalse(s.countdownDone(10_000));
        assertFalse(s.beginCompleting());
    }

    @Test
    void emptyTradeCantBeConfirmed() {
        TradeSession s = session();
        assertEquals(TradeSession.Confirm.EMPTY, s.confirm(alice, 0, 3000));
    }

    @Test
    void completingLocksEverything() {
        TradeSession s = session();
        add(s, alice, "sword");
        s.confirm(alice, 0, 3000);
        s.confirm(bob, 0, 3000);
        assertTrue(s.beginCompleting());
        assertFalse(s.beginAdd(bob, 12));
        assertFalse(s.setMoney(bob, Currency.MONEY, 5));
        assertTrue(s.beginRemove(alice, s.a.items().getFirst().rowId()).isEmpty());
        assertFalse(s.unconfirm(alice));
        assertEquals(TradeSession.Confirm.LOCKED, s.confirm(alice, 0, 3000));
        s.reopen();
        assertEquals(TradeSession.State.OPEN, s.state());
        assertFalse(s.a.ready());
        s.end("cancelled");
        assertTrue(s.ended());
        assertEquals("cancelled", s.endReason());
        assertFalse(s.beginAdd(alice, 12));
    }

    @Test
    void itemLimitCountsItemsOnTheirWay() {
        TradeSession s = session();
        assertTrue(s.beginAdd(alice, 2));
        assertTrue(s.beginAdd(alice, 2));
        assertFalse(s.beginAdd(alice, 2));
        s.failAdd(alice);
        assertTrue(s.beginAdd(alice, 2));
    }

    @Test
    void failedReleaseOffersTheItemAgain() {
        TradeSession s = session();
        add(s, alice, "sword");
        Offer o = s.beginRemove(alice, s.a.items().getFirst().rowId()).orElseThrow();
        s.failRemove(alice, o);
        assertEquals(1, s.a.items().size());
        assertEquals(0, s.a.inFlight());
    }

    @Test
    void countdownNeverFinishesWithItemsInFlight() {
        TradeSession s = session();
        add(s, alice, "sword");
        s.confirm(alice, 0, 1000);
        s.confirm(bob, 0, 1000);
        assertTrue(s.countdownDone(1000));
        assertEquals(0, s.a.inFlight());
    }

    @Test
    void sidesAndSnapshot() {
        TradeSession s = session();
        add(s, alice, "sword");
        s.setMoney(bob, Currency.SHARDS, 3);
        assertEquals(s.b, s.other(alice));
        assertEquals(s.a, s.side(alice));
        assertTrue(s.involves(bob));
        assertThrows(IllegalArgumentException.class, () -> s.side(UUID.randomUUID()));
        TradeSession.Snapshot snap = s.snapshot();
        assertEquals(1, snap.itemsA().size());
        assertEquals(3L, snap.moneyB().get(Currency.SHARDS));
        assertEquals(snap.itemsA().getFirst().rowId(), snap.idsA().getFirst());
        assertThrows(IllegalArgumentException.class, () -> new TradeSession(2, new TradeSession.Side(alice, alice, "A"),
                new TradeSession.Side(alice, alice, "A"), 0));
    }
}
