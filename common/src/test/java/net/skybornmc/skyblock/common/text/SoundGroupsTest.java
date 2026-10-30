package net.skybornmc.skyblock.common.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1: per-player UI and event sound muting. */
class SoundGroupsTest {

    private static final Set<String> UI = Set.of("click", "menu-open", "menu-close", "page");

    @Test
    void uiSoundsFollowTheUiSetting() {
        assertTrue(Sounds.audible(Sounds.Group.UI, "error", UI, true, false));
        assertFalse(Sounds.audible(Sounds.Group.UI, "error", UI, false, true), "every sound a menu plays is a UI sound");
        assertFalse(Sounds.audible(Sounds.Group.UI, "click", UI, false, true));
    }

    @Test
    void uiKeysAreMutedWhereverTheyPlay() {
        assertFalse(Sounds.audible(Sounds.Group.NORMAL, "click", UI, false, true), "click played outside a menu");
        assertTrue(Sounds.audible(Sounds.Group.NORMAL, "click", UI, true, false));
        assertTrue(Sounds.audible(Sounds.Group.NORMAL, "money", UI, false, false), "normal sounds never follow either setting");
        assertTrue(Sounds.audible(Sounds.Group.NORMAL, null, UI, false, false));
    }

    @Test
    void eventSoundsFollowOnlyTheEventSetting() {
        assertTrue(Sounds.audible(Sounds.Group.EVENT, "unlock", UI, false, true));
        assertFalse(Sounds.audible(Sounds.Group.EVENT, "unlock", UI, true, false));
        assertTrue(Sounds.audible(Sounds.Group.EVENT, "click", UI, false, true), "an explicit event sound ignores the UI list");
    }
}
