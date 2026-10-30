package net.skybornmc.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1: the /settings scoreboard toggle. */
class ScoreboardToggleTest {

    @Test
    void sidebarNeedsConfigWorldAndToggle() {
        assertTrue(HudService.visible(true, false, true));
        assertFalse(HudService.visible(true, false, false), "player turned it off");
        assertFalse(HudService.visible(false, false, true), "off in hud.yml");
        assertFalse(HudService.visible(true, true, true), "disabled world");
    }

    @Test
    void legacyOffMovesToTheToggle() {
        assertEquals("false", HudService.migrated("off", null));
        assertEquals("true", HudService.migrated("off", "true"), "a value set with /settings wins");
        assertNull(HudService.migrated(null, null));
        assertNull(HudService.migrated("garbage", null));
        assertEquals("false", HudService.migrated(null, "false"));
    }

    @Test
    void storageKeyIsTheSettingsKey() {
        assertEquals("setting.scoreboard", HudService.STORAGE_KEY);
    }
}
