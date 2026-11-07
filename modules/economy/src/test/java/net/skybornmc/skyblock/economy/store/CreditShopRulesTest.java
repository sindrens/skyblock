package net.skybornmc.skyblock.economy.store;

import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.store.CreditShop;
import net.skybornmc.skyblock.economy.EconomyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026 (plans/STORE_FULL_PLAN.md 1): the pure parts of the credit shop. */
class CreditShopRulesTest {

    @Test
    void ranksCostOnlyTheDifference() {
        RankLadder ladder = RankLadder.parse(new EconomyConfig().creditShopRanks, p -> {
            throw new AssertionError(p);
        });
        assertEquals(List.of("hatchling", "fledgling", "firewing", "flamecrest", "sunfeather", "pyrelord", "immortal"),
                ladder.steps().stream().map(RankLadder.Step::group).toList());
        assertEquals(List.of(500L, 1000L, 2000L, 3000L, 4500L, 6500L, 10000L), ladder.steps().stream().map(RankLadder.Step::price).toList());
        assertEquals(10_000, ladder.price(6, -1));
        assertEquals(1_000, ladder.price(2, 1)); // Fledgling → Firewing
        assertEquals(3_500, ladder.price(6, 5)); // Pyrelord → Immortal
        assertEquals(-1, ladder.price(1, 2)); // lower than owned
        assertEquals(-1, ladder.price(2, 2)); // owned
        // LuckPerms inheritance: a Firewing also has the lower groups
        assertEquals(2, ladder.highest(Set.of("hatchling", "fledgling", "firewing")::contains));
        assertEquals(-1, ladder.highest(g -> false));
    }

    @Test
    void badRankLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        RankLadder ladder = RankLadder.parse(List.of("a:100", "b", "c:x", "d:50", "e:200"), problems::add);
        assertEquals(List.of("a", "e"), ladder.steps().stream().map(RankLadder.Step::group).toList());
        assertEquals(3, problems.size());
    }

    @Test
    void boostersParse() {
        for (String line : new EconomyConfig().creditShopBoosters) {
            assertNotNull(CreditShopImpl.parseBooster(line), line);
        }
        assertArrayEquals(new Object[]{BoosterType.SELL, 50, 30, 300L}, CreditShopImpl.parseBooster("sell:50:30:300"));
        assertNull(CreditShopImpl.parseBooster("sell:50:30"));
        assertNull(CreditShopImpl.parseBooster("money:50:30:300"));
        assertNull(CreditShopImpl.parseBooster("sell:0:30:300"));
    }

    @Test
    void ggIsShortAndClear() {
        assertTrue(CreditShopImpl.isGg("gg"));
        assertTrue(CreditShopImpl.isGg("GG!"));
        assertTrue(CreditShopImpl.isGg("gg wp"));
        assertTrue(CreditShopImpl.isGg("ggwp"));
        assertFalse(CreditShopImpl.isGg("eggs"));
        assertFalse(CreditShopImpl.isGg("good game everyone, gg!!"));
        assertFalse(CreditShopImpl.isGg(null));
    }

    @Test
    void purchaseIdsFitTheKeyLimit() {
        String id = CreditShopImpl.newPurchaseId();
        assertTrue(id.startsWith("cs:"));
        assertEquals(23, id.length());
        assertTrue(("r" + id + ":2").length() <= 94);
        assertNotEquals(id, CreditShopImpl.newPurchaseId());
    }

    @Test
    void ranksSitInOneRowOtherTabsFill() {
        assertArrayEquals(new int[]{19, 20, 21, 22, 23, 24, 25}, CreditShopMenu.slotsFor(CreditShop.Tab.RANKS, 7));
        assertArrayEquals(new int[]{21, 22, 23}, CreditShopMenu.slotsFor(CreditShop.Tab.RANKS, 3));
        int[] content = CreditShopMenu.slotsFor(CreditShop.Tab.EXTRAS, 3);
        assertEquals(28, content.length);
        assertEquals(10, content[0]);
        List<Integer> all = Arrays.stream(CreditShopMenu.contentSlots()).boxed().toList();
        for (int edge : new int[]{0, 9, 17, 18, 44, 45, 53}) {
            assertFalse(all.contains(edge), "slot " + edge);
        }
        assertTrue(all.contains(43));
    }

    @Test
    void offersAreRegisteredStatically() {
        CreditShop.register("test", CreditShop.Offer.link("t1", CreditShop.Tab.EXTRAS, 1, (a, b) -> null, p -> { }));
        assertTrue(CreditShop.offers().get("test").containsKey("t1"));
        CreditShop.unregisterAll("test");
        assertFalse(CreditShop.offers().containsKey("test"));
    }
}
