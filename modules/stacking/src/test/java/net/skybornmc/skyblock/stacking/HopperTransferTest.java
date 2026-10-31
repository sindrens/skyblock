package net.skybornmc.skyblock.stacking;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F12: hoppers move 64 items every 8 ticks. The stacked-item hopper path (ItemStacker#onHopper) hands a hopper one
 * 64-stack as one part, never per item, and never more stacks than the hopper has slots; the tracked server configs carry
 * the decided hopper settings.
 */
class HopperTransferTest {

    private static final int HOPPER_SLOTS = 5;

    @Test
    void sixtyFourStackIsOnePart() {
        long offered = StackMath.hopperOffer(1_000, 64, 64, HOPPER_SLOTS);
        assertEquals(64, offered);
        assertArrayEquals(new int[]{64}, StackMath.stackSizes(offered, 64));
    }

    @Test
    void smallerStackIsTakenWhole() {
        assertEquals(40, StackMath.hopperOffer(40, 64, 64, HOPPER_SLOTS));
        assertArrayEquals(new int[]{40}, StackMath.stackSizes(40, 64));
    }

    @Test
    void sixteenStackItemsSplitIntoFourParts() {
        long offered = StackMath.hopperOffer(1_000, 64, 16, HOPPER_SLOTS);
        assertEquals(64, offered);
        assertArrayEquals(new int[]{16, 16, 16, 16}, StackMath.stackSizes(offered, 16));
    }

    @Test
    void unstackablesAreCappedAtTheSlots() {
        long offered = StackMath.hopperOffer(1_000, 64, 1, HOPPER_SLOTS);
        assertEquals(5, offered, "never 64 sword clones for a 5-slot hopper");
        assertEquals(5, StackMath.stackSizes(offered, 1).length);
    }

    @Test
    void edgeCases() {
        assertEquals(0, StackMath.hopperOffer(0, 64, 64, HOPPER_SLOTS));
        assertEquals(0, StackMath.hopperOffer(100, 64, 64, 0));
        assertEquals(0, StackMath.stackSizes(0, 64).length);
        assertArrayEquals(new int[]{64, 64, 2}, StackMath.stackSizes(130, 64));
        assertArrayEquals(new int[]{1, 1}, StackMath.stackSizes(2, 0), "max below 1 counts as 1");
        assertEquals(3456, StackMath.hopperOffer(1_000_000, 3456, 64, 54), "a double chest takes up to the config max");
    }

    @Test
    void trackedServerConfigsHaveTheDecidedHopperSettings() throws Exception {
        Path spigot = Path.of("../../server/spigot.yml");
        Path paper = Path.of("../../server/config/paper-world-defaults.yml");
        assertTrue(Files.exists(spigot) && Files.exists(paper), "tracked server configs missing");
        YamlConfiguration s = YamlConfiguration.loadConfiguration(spigot.toFile());
        assertEquals(64, s.getInt("world-settings.default.hopper-amount"), "F12: hopper-amount 64");
        assertEquals(8, s.getInt("world-settings.default.ticks-per.hopper-transfer"), "F12: hopper-transfer 8");
        assertEquals(8, s.getInt("world-settings.default.ticks-per.hopper-check"), "F12: hopper-check 8");
        YamlConfiguration p = YamlConfiguration.loadConfiguration(paper.toFile());
        assertTrue(p.getBoolean("hopper.cooldown-when-full"), "F12: cooldown-when-full true");
        assertFalse(p.getBoolean("hopper.disable-move-event", true), "F12: disable-move-event must stay false");
    }
}
