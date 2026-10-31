package net.skybornmc.skyblock.common.visibility;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityRulesTest {

    private static final BooleanSupplier NO = () -> false;
    private static final BooleanSupplier YES = () -> true;

    private static VisibilityRules.Viewer viewer(boolean seesVanished, ShowMode mode, boolean inZone) {
        return new VisibilityRules.Viewer(seesVanished, mode, inZone, "depths");
    }

    private static VisibilityRules.Target target(boolean vanished, boolean staff, boolean inZone) {
        return new VisibilityRules.Target(vanished, staff, inZone, "depths");
    }

    // ------------------------------------------------------------------ vanish

    @Test
    void vanishedHiddenFromPlayersWithoutSeePermission() {
        assertTrue(VisibilityRules.hidden(viewer(false, ShowMode.EVERYONE, false), target(true, true, false), NO, NO));
    }

    @Test
    void vanishedShownToStaffWhoMaySee() {
        assertFalse(VisibilityRules.hidden(viewer(true, ShowMode.EVERYONE, false), target(true, true, false), NO, NO));
    }

    @Test
    void vanishedStaffFollowVanishOnlyNotRulesOrSettings() {
        // A see-vanished viewer with "Nobody" in the AFK pool still sees vanished staff: vanish alone decides.
        assertFalse(VisibilityRules.hidden(viewer(true, ShowMode.NOBODY, true), target(true, false, true), YES, NO));
    }

    // ------------------------------------------------------------------ staff

    @Test
    void staffNotVanishedAlwaysShown() {
        assertFalse(VisibilityRules.hidden(viewer(false, ShowMode.NOBODY, true), target(false, true, true), YES, NO));
    }

    // ------------------------------------------------------------------ the Show players choice

    @Test
    void everyoneShowsEveryone() {
        assertFalse(VisibilityRules.hidden(viewer(false, ShowMode.EVERYONE, true), target(false, false, true), NO, NO));
    }

    @Test
    void nobodyHidesPlayersInTheZone() {
        assertTrue(VisibilityRules.hidden(viewer(false, ShowMode.NOBODY, true), target(false, false, true), NO, YES));
    }

    @Test
    void friendsHidesOnlyStrangers() {
        assertTrue(VisibilityRules.hidden(viewer(false, ShowMode.FRIENDS, true), target(false, false, true), NO, NO));
        assertFalse(VisibilityRules.hidden(viewer(false, ShowMode.FRIENDS, true), target(false, false, true), NO, YES));
    }

    @Test
    void choiceDoesNothingOutsideTheZone() {
        // Viewer at spawn (not a place where the choice applies): everyone stays visible.
        assertFalse(VisibilityRules.hidden(viewer(false, ShowMode.NOBODY, false), target(false, false, true), NO, NO));
        // Target outside the zone (e.g. walking past the AFK pool): stays visible to a viewer inside.
        assertFalse(VisibilityRules.hidden(viewer(false, ShowMode.NOBODY, true), target(false, false, false), NO, NO));
    }

    @Test
    void choiceNeedsTheSameWorld() {
        var v = new VisibilityRules.Viewer(false, ShowMode.NOBODY, true, "depths");
        var t = new VisibilityRules.Target(false, false, true, "spawn");
        assertFalse(VisibilityRules.hidden(v, t, NO, NO));
    }

    @Test
    void relationAskedOnlyForFriendsChoice() {
        AtomicInteger asked = new AtomicInteger();
        BooleanSupplier counting = () -> {
            asked.incrementAndGet();
            return true;
        };
        VisibilityRules.hidden(viewer(false, ShowMode.NOBODY, true), target(false, false, true), NO, counting);
        VisibilityRules.hidden(viewer(false, ShowMode.EVERYONE, true), target(false, false, true), NO, counting);
        assertEquals(0, asked.get());
        VisibilityRules.hidden(viewer(false, ShowMode.FRIENDS, true), target(false, false, true), NO, counting);
        assertEquals(1, asked.get());
    }

    @Test
    void nullModeIsEveryone() {
        assertEquals(ShowMode.EVERYONE, new VisibilityRules.Viewer(false, null, true, "w").mode());
    }

    // ------------------------------------------------------------------ module rules (AFK pool)

    @Test
    void ruleHidesRegardlessOfChoice() {
        assertTrue(VisibilityRules.hidden(viewer(false, ShowMode.EVERYONE, false), target(false, false, false), YES, NO));
    }

    @Test
    void ruleNotAskedWhenStaffOrVanished() {
        AtomicInteger asked = new AtomicInteger();
        BooleanSupplier counting = () -> {
            asked.incrementAndGet();
            return true;
        };
        VisibilityRules.hidden(viewer(false, ShowMode.EVERYONE, true), target(false, true, true), counting, NO);
        VisibilityRules.hidden(viewer(true, ShowMode.EVERYONE, true), target(true, false, true), counting, NO);
        assertEquals(0, asked.get());
    }

    // ------------------------------------------------------------------ diff

    @Test
    void diffAppliesOnlyChanges() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        // a: hidden, stays hidden; b: hidden, now shown; c: shown, now hidden; d: shown, stays shown.
        var changes = VisibilityRules.diff(Set.of(a, b), Map.of(a, true, b, false, c, true, d, false), false);
        assertEquals(Set.of(c), changes.hide());
        assertEquals(Set.of(b), changes.show());
    }

    @Test
    void diffNoChangesIsEmpty() {
        UUID a = UUID.randomUUID();
        assertTrue(VisibilityRules.diff(Set.of(a), Map.of(a, true), false).isEmpty());
        assertTrue(VisibilityRules.diff(Set.of(), Map.of(a, false), false).isEmpty());
    }

    @Test
    void diffLeavesOfflineTargetsAlone() {
        UUID offline = UUID.randomUUID();
        assertTrue(VisibilityRules.diff(Set.of(offline), Map.of(), false).isEmpty());
    }

    @Test
    void forcedDiffAppliesEveryWantedState() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var changes = VisibilityRules.diff(Set.of(a), Map.of(a, true, b, false), true);
        assertEquals(Set.of(a), changes.hide());
        assertEquals(Set.of(b), changes.show());
    }

    // ------------------------------------------------------------------ choice ids

    @Test
    void showModeIds() {
        assertEquals(java.util.List.of("everyone", "friends", "nobody"), ShowMode.ids());
        assertEquals(ShowMode.FRIENDS, ShowMode.byId(" Friends ").orElseThrow());
        assertTrue(ShowMode.byId("pinned").isEmpty());
        assertTrue(ShowMode.byId(null).isEmpty());
    }
}
