package net.skybornmc.skyblock.shop;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Price math: totals without overflow, the buy > sell rule, amount limits and stack splitting. */
class ShopMathTest {

    private static final Function<String, OptionalDouble> SELL = id -> switch (id) {
        case "cobblestone" -> OptionalDouble.of(7.5);
        case "redstone" -> OptionalDouble.of(247.5);
        case "carrot" -> OptionalDouble.of(105);
        default -> OptionalDouble.empty();
    };

    @Test
    void totals() {
        assertEquals(64 * 6L, ShopMath.total(6, 64).getAsLong());
        assertEquals(2_304 * 2_400L, ShopMath.total(2_400, 2_304).getAsLong());
        assertTrue(ShopMath.total(Long.MAX_VALUE / 2, 3).isEmpty(), "overflow is refused, not wrapped");
        assertTrue(ShopMath.total(0, 5).isEmpty());
        assertTrue(ShopMath.total(5, 0).isEmpty());
        assertTrue(ShopMath.total(-5, 3).isEmpty());
    }

    @Test
    void maxSellValueTakesTheBetterWay() {
        assertEquals(0, ShopMath.maxSellValue("oak_log", Map.of(), SELL));
        assertEquals(105, ShopMath.maxSellValue("carrot", Map.of(), SELL));
        assertEquals(9 * 247.5, ShopMath.maxSellValue("redstone_block", Map.of("redstone", 9), SELL), 1e-9);
        // Selling itself vs. its parts: the larger counts, never both.
        assertEquals(247.5, ShopMath.maxSellValue("redstone", Map.of("cobblestone", 1), SELL), 1e-9);
    }

    @Test
    void buyMustBeAboveSell() {
        assertTrue(ShopMath.undercutsSell("carrot", 105, Map.of(), SELL), "equal to the sell value is a loop too");
        assertTrue(ShopMath.undercutsSell("carrot", 50, Map.of(), SELL));
        assertFalse(ShopMath.undercutsSell("carrot", 106, Map.of(), SELL));
        assertTrue(ShopMath.undercutsSell("redstone_block", 2_000, Map.of("redstone", 9), SELL));
        assertFalse(ShopMath.undercutsSell("redstone_block", 2_400, Map.of("redstone", 9), SELL));
        assertTrue(ShopMath.undercutsSell("stone", 7, Map.of("cobblestone", 1), SELL));
        assertFalse(ShopMath.undercutsSell("oak_log", 1, Map.of(), SELL), "items /sell doesn't buy are always safe");
    }

    @Test
    void maxAmount() {
        assertEquals(64, ShopMath.maxAmount(5, 64, 2_304));
        assertEquals(2_304, ShopMath.maxAmount(5, 10_000, 2_304));
        assertEquals(0, ShopMath.maxAmount(5, 0, 2_304));
        assertEquals(0, ShopMath.maxAmount(0, 64, 2_304));
        assertEquals(2, ShopMath.maxAmount(Long.MAX_VALUE / 2, 64, 2_304), "never a total above Long.MAX_VALUE");
    }

    @Test
    void stacks() {
        assertArrayEquals(new int[]{64, 64, 2}, ShopMath.stacks(130, 64));
        assertArrayEquals(new int[]{16, 16}, ShopMath.stacks(32, 16));
        assertArrayEquals(new int[]{1, 1, 1}, ShopMath.stacks(3, 1));
        assertArrayEquals(new int[0], ShopMath.stacks(0, 64));
    }
}
