package net.skybornmc.skyblock.profiles;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026 (island = profile): snapshot expiry, the join step of the player file, wipe intents. */
class WipeRulesTest {

    private static final long DAY = WipeRules.DAY_MILLIS;

    @Test
    void snapshotsLastTheConfiguredDays() {
        assertEquals(1_000 + 14 * DAY, WipeRules.expiresAt(1_000, 14));
        assertEquals(1_000 + DAY, WipeRules.expiresAt(1_000, 0), "at least one day");
    }

    @Test
    void snapshotStatusFollowsRestoreExpiryAndPurge() {
        long expires = 14 * DAY;
        assertEquals(WipeRules.SnapshotStatus.OPEN, WipeRules.status(null, null, expires, expires - 1));
        assertEquals(WipeRules.SnapshotStatus.EXPIRED, WipeRules.status(null, null, expires, expires));
        assertEquals(WipeRules.SnapshotStatus.RESTORED, WipeRules.status(5L, null, expires, 10));
        assertEquals(WipeRules.SnapshotStatus.RESTORED, WipeRules.status(5L, null, expires, expires + 1), "restored stays restored");
        assertEquals(WipeRules.SnapshotStatus.PURGED, WipeRules.status(null, 7L, expires, expires + 1));

        assertTrue(WipeRules.restorable(null, null, expires, 0));
        assertFalse(WipeRules.restorable(null, null, expires, expires), "expired can't be restored");
        assertFalse(WipeRules.restorable(1L, null, expires, 0), "restored once only");

        assertTrue(WipeRules.purgeDue(null, null, expires, expires));
        assertFalse(WipeRules.purgeDue(null, null, expires, expires - 1));
        assertFalse(WipeRules.purgeDue(1L, null, expires, expires + 1), "a restored profile is live again: never purged");
        assertFalse(WipeRules.purgeDue(null, 1L, expires, expires + 1), "purged once");
    }

    @Test
    void joinStepProtectsThePlayerFile() {
        UUID active = UUID.randomUUID();
        UUID old = UUID.randomUUID();
        assertEquals(WipeRules.JoinStep.NOTHING, WipeRules.joinStep(active, null, false));
        assertEquals(WipeRules.JoinStep.APPLY_PENDING, WipeRules.joinStep(active, null, true));
        // wiped while offline: the file still holds the old profile's items → save them first, then apply
        assertEquals(WipeRules.JoinStep.CAPTURE_THEN_APPLY, WipeRules.joinStep(active, old, true));
        assertEquals(WipeRules.JoinStep.CAPTURE_THEN_APPLY, WipeRules.joinStep(active, old, false));
        // stale row pointing at the active profile
        assertEquals(WipeRules.JoinStep.DROP_CAPTURE, WipeRules.joinStep(active, active, false));
        assertEquals(WipeRules.JoinStep.DROP_CAPTURE_THEN_APPLY, WipeRules.joinStep(active, active, true));
    }

    @Test
    void onlyAnOfflineActiveProfileWithoutSavedStateNeedsACapture() {
        assertTrue(WipeRules.needsCapture(true, false, false, false));
        assertFalse(WipeRules.needsCapture(false, false, false, false), "inactive: its state is in the database already");
        assertFalse(WipeRules.needsCapture(true, true, false, false), "online: captured right away");
        assertFalse(WipeRules.needsCapture(true, false, true, false), "a pending row: the file is a stale copy");
        assertFalse(WipeRules.needsCapture(true, false, false, true), "the file belongs to the earlier capture");
    }

    @Test
    void intentsFinishOrDropAfterTheGrace() {
        long grace = 60_000;
        assertEquals(WipeRules.IntentAction.WAIT, WipeRules.intent(true, false, 1_000, grace));
        assertEquals(WipeRules.IntentAction.WIPE, WipeRules.intent(true, false, grace, grace), "left the island, wipe missing");
        assertEquals(WipeRules.IntentAction.DROP, WipeRules.intent(true, true, grace, grace), "removal never saved");
        assertEquals(WipeRules.IntentAction.DROP, WipeRules.intent(false, false, 0, grace), "already wiped");
    }

    @Test
    void itemCountsAreClamped() {
        assertEquals(0, WipeRules.clampItems(-5));
        assertEquals(Integer.MAX_VALUE, WipeRules.clampItems(Long.MAX_VALUE));
        assertEquals(42, WipeRules.clampItems(42));
    }
}
