package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-BUGS: every ban asks first (details on the middle item), a permanent ban asks twice; the /p bar is the framework's. */
class PunishConfirmTest {

    @Test
    void everyBanNeedsAConfirmEvenWhenConfirmIsOff() throws Exception {
        assertTrue(PunishMenus.needsConfirm(false, Templates.Step.parse("ban 1d")));
        assertTrue(PunishMenus.needsConfirm(false, Templates.Step.parse("ban perm")));
        assertTrue(PunishMenus.needsConfirm(false, Templates.Step.parse("ipban perm")));
    }

    @Test
    void warnsMutesAndKicksFollowTheOwnerSetting() throws Exception {
        for (String step : new String[]{"warn", "mute 1h", "kick"}) {
            assertFalse(PunishMenus.needsConfirm(false, Templates.Step.parse(step)), step + " applies at once (owner decision)");
            assertTrue(PunishMenus.needsConfirm(true, Templates.Step.parse(step)), step + " confirms when menu.confirm is on");
        }
    }

    @Test
    void onlyAPermanentBanIsTwoStep() throws Exception {
        assertTrue(PunishMenus.twoStep(Templates.Step.parse("ban perm")));
        assertTrue(PunishMenus.twoStep(Templates.Step.parse("ipban perm")));
        assertFalse(PunishMenus.twoStep(Templates.Step.parse("ban 7d")));
        assertFalse(PunishMenus.twoStep(Templates.Step.parse("mute perm")));
        assertFalse(PunishMenus.twoStep(null));
    }

    @Test
    void buttonsOnTheFrameBarMoveToTheTopRow() {
        assertEquals(46, PunishMenus.frameSafeSlot(46, Set.of()));
        assertEquals(52, PunishMenus.frameSafeSlot(52, Set.of()), "a single screen has no pages: 52 stays free for buttons");
        assertEquals(3, PunishMenus.frameSafeSlot(45, Set.of(4)), "an old config's sanctions on 45 (Back) moves up");
        assertEquals(5, PunishMenus.frameSafeSlot(48, Set.of(3, 4)));
        assertEquals(2, PunishMenus.frameSafeSlot(50, Set.of(3, 4, 5)));
        assertEquals(-1, PunishMenus.frameSafeSlot(49, Set.of(1, 2, 3, 5, 6, 7)));
    }
}
