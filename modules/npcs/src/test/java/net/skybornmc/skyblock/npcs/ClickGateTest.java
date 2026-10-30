package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UX3: right- and left-click both act once per click on NPCs and holograms. */
class ClickGateTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @Test
    void onlyTheMainHandInteractActs() {
        assertTrue(ClickGate.interactActs(true), "right-click (26.x: only the 'at' event arrives) acts");
        assertFalse(ClickGate.interactActs(false), "the off-hand copy of the same click does not");
    }

    @Test
    void oneActionPerClick() {
        ClickGate gate = new ClickGate();
        assertTrue(gate.pass(steve, 1_000, 600), "first packet of a click");
        assertFalse(gate.pass(steve, 1_001, 600), "second packet of the same click (old 'at' + plain, or tap = hit + use)");
        assertTrue(gate.pass(alex, 1_001, 600), "per player");
        assertFalse(gate.pass(steve, 1_599, 600));
        assertTrue(gate.pass(steve, 1_600, 600), "the next click after the cooldown");
    }

    @Test
    void clockJumpsAndQuitsNeverLockAPlayerOut() {
        ClickGate gate = new ClickGate();
        assertTrue(gate.pass(steve, 5_000, 600));
        assertTrue(gate.pass(steve, 4_000, 600), "clock went back: accept instead of blocking");
        gate.forget(steve);
        assertTrue(gate.pass(steve, 4_001, 600), "forgotten on quit");
    }
}
