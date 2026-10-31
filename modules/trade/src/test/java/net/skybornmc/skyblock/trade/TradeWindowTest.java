package net.skybornmc.skyblock.trade;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner U7 (trade safety): ?, help, Close and the main menu never cancel a trade; only staying away too long does. */
class TradeWindowTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private TradeSession session() {
        return new TradeSession(1, new TradeSession.Side(alice, UUID.randomUUID(), "Alice"),
                new TradeSession.Side(bob, UUID.randomUUID(), "Bob"), 0);
    }

    /** A screen opened from another (help, the money picker). */
    private static final class Screen extends Menu {
        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return Component.empty();
        }

        @Override
        protected void build() {
        }
    }

    @Test
    void helpOpenedFromTheTradeCountsAsTheTradeWindow() {
        TradeMenu trade = new TradeMenu(null, session());
        Menu help = new Screen().parent(trade);
        Menu topic = new Screen().parent(help);
        assertTrue(TradeModule.isTradeView(trade));
        assertTrue(TradeModule.isTradeView(help), "the ? button opens help with the trade as Back: not a close");
        assertTrue(TradeModule.isTradeView(topic));
        assertFalse(TradeModule.isTradeView(new Screen()), "the main menu starts a new stack: the window is closed");
        assertFalse(TradeModule.isTradeView(null));
    }

    @Test
    void cancelIsNotOnTheBackSlot() {
        assertEquals(53, TradeMenu.SLOT_CANCEL);
        assertFalse(TradeMenu.SLOT_CANCEL == Menu.SLOT_BACK || TradeMenu.SLOT_CANCEL == Menu.SLOT_INFO
                || TradeMenu.SLOT_CANCEL == Menu.SLOT_CLOSE || TradeMenu.SLOT_CANCEL == Menu.SLOT_MAIN_MENU);
    }

    @Test
    void closingTheWindowKeepsTheTradeAndTheConfirmation() {
        TradeSession s = session();
        assertTrue(s.beginAdd(alice, 12));
        s.finishAdd(alice, new Offer(1, new byte[]{1}, "sword", null, "STONE", 1, "sword"));
        assertEquals(TradeSession.Confirm.READY, s.confirm(alice, 0, 3000));
        assertTrue(s.away(alice, 1_000));
        assertFalse(s.away(alice, 2_000), "already away: the timer is not restarted");
        assertTrue(s.side(alice).away());
        assertTrue(s.side(alice).ready(), "closing alone changes nothing in the offer");
        assertFalse(s.ended());
        assertNull(s.awayTooLong(61_000, 60_000));
    }

    @Test
    void stayingAwayTooLongEndsTheTrade() {
        TradeSession s = session();
        s.away(bob, 1_000);
        assertNull(s.awayTooLong(61_000, 60_000));
        assertSame(s.b, s.awayTooLong(61_001, 60_000));
    }

    @Test
    void comingBackStopsTheTimer() {
        TradeSession s = session();
        s.away(alice, 1_000);
        assertTrue(s.back(alice));
        assertFalse(s.back(alice));
        assertNull(s.awayTooLong(1_000_000, 60_000));
        assertTrue(s.away(alice, 2_000_000), "a second close starts a fresh timer");
        assertNull(s.awayTooLong(2_030_000, 60_000));
    }
}
