package net.skybornmc.skyblock.box;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cap levels, inventory fit planning and source keys. */
class BoxRulesTest {

    /** SMALL2: the join count runs async; the main thread is only used when the active profile has stacks waiting. */
    @Test
    void joinNoticeOnlyWhenTheActiveProfileHasStacks() {
        java.util.UUID active = java.util.UUID.randomUUID();
        java.util.UUID other = java.util.UUID.randomUUID();
        assertTrue(BoxService.needsNotice(java.util.Map.of(active, 3, other, 0), active));
        assertFalse(BoxService.needsNotice(java.util.Map.of(active, 0, other, 9), active), "other profiles don't count");
        assertFalse(BoxService.needsNotice(java.util.Map.of(other, 9), active), "not loaded");
        assertFalse(BoxService.needsNotice(java.util.Map.of(active, 3), null));
    }

    @Test
    void capLevels() {
        assertEquals(BoxCap.Level.OK, BoxCap.of(0, 500, 90));
        assertEquals(BoxCap.Level.OK, BoxCap.of(449, 500, 90));
        assertEquals(BoxCap.Level.WARN, BoxCap.of(450, 500, 90));
        assertEquals(BoxCap.Level.WARN, BoxCap.of(499, 500, 90));
        assertEquals(BoxCap.Level.FULL, BoxCap.of(500, 500, 90));
        assertEquals(BoxCap.Level.FULL, BoxCap.of(800, 500, 90), "over the cap is still only a warning level");
        assertEquals(BoxCap.Level.OK, BoxCap.of(499, 500, 100), "100% warns only when full");
        assertFalse(BoxCap.isFull(499, 500));
        assertTrue(BoxCap.isFull(500, 500));
        // SECFIX1 F-09: the hard safety cap sits far above the soft cap and refuses new actions from there.
        assertFalse(BoxCap.isHardFull(4_999, 500, 5_000));
        assertTrue(BoxCap.isHardFull(5_000, 500, 5_000));
        assertTrue(BoxCap.isHardFull(9_000, 500, 5_000));
        assertFalse(BoxCap.isHardFull(600, 500, 5_000), "over the soft cap only warns");
        assertTrue(BoxCap.isHardFull(500, 500, 100), "a hard cap below the soft cap counts as the soft cap");
        assertFalse(BoxCap.isHardFull(499, 500, 100));
    }

    private static List<FitPlan.Slot<String>> inventory(int free, String... used) {
        List<FitPlan.Slot<String>> slots = new ArrayList<>();
        for (String u : used) {
            String[] p = u.split(":");
            slots.add(new FitPlan.Slot<>(p[0], Integer.parseInt(p[1]), 64));
        }
        for (int i = 0; i < free; i++) {
            slots.add(new FitPlan.Slot<>(null, 0, 0));
        }
        return slots;
    }

    @Test
    void fitFillsSimilarStacksThenFreeSlots() {
        List<FitPlan.Slot<String>> inv = inventory(1, "stone:60");
        // 4 stone top up the existing stack, 64 dirt take the free slot, then nothing else fits.
        List<Integer> fit = FitPlan.plan(inv, List.of(new FitPlan.Stack<>("stone", 4, 64), new FitPlan.Stack<>("dirt", 64, 64),
                new FitPlan.Stack<>("stone", 1, 64)), Objects::equals, false);
        assertEquals(List.of(0, 1), fit);
    }

    @Test
    void takeAllSkipsBigStacksButTakesLaterSmallOnes() {
        List<FitPlan.Slot<String>> inv = inventory(1, "stone:63");
        List<FitPlan.Stack<String>> wanted = List.of(new FitPlan.Stack<>("dirt", 64, 64), new FitPlan.Stack<>("sand", 64, 64),
                new FitPlan.Stack<>("stone", 1, 64));
        assertEquals(List.of(0, 2), FitPlan.plan(inventory(1, "stone:63"), wanted, Objects::equals, false));
        assertEquals(List.of(0), FitPlan.plan(inv, wanted, Objects::equals, true), "single take stops at the first miss");
    }

    @Test
    void aStackNeverPartlyFits() {
        List<FitPlan.Slot<String>> inv = inventory(0, "stone:40");
        assertFalse(FitPlan.place(inv, new FitPlan.Stack<>("stone", 30, 64), Objects::equals));
        assertEquals(40, inv.getFirst().amount, "nothing changed");
        assertTrue(FitPlan.place(inv, new FitPlan.Stack<>("stone", 24, 64), Objects::equals));
        assertEquals(64, inv.getFirst().amount);
    }

    @Test
    void unstackableItemsNeedOneSlotEach() {
        List<FitPlan.Slot<String>> inv = inventory(2);
        List<FitPlan.Stack<String>> swords = List.of(new FitPlan.Stack<>("sword", 1, 1), new FitPlan.Stack<>("sword", 1, 1),
                new FitPlan.Stack<>("sword", 1, 1));
        assertEquals(List.of(0, 1), FitPlan.plan(inv, swords, Objects::equals, false));
    }

    @Test
    void sourceKeysAreCleaned() {
        assertEquals("sell", net.skybornmc.skyblock.common.box.BoxSource.clean(" Sell "));
        assertEquals("other", net.skybornmc.skyblock.common.box.BoxSource.clean("bad key!"));
        assertEquals("other", net.skybornmc.skyblock.common.box.BoxSource.clean(null));
        assertEquals("forge", new BoxItem(new byte[]{1}, "STONE", 1, "Stone", "FORGE", 1, null).source());
    }
}
