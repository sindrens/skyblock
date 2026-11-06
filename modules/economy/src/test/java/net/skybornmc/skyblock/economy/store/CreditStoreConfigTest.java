package net.skybornmc.skyblock.economy.store;

import net.skybornmc.skyblock.economy.EconomyConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CREDIT-SHOP 5.1: the default packages (C1-R2-28) and the package line parser. */
class CreditStoreConfigTest {

    @Test
    void defaultsAreTheFourPacks() {
        EconomyConfig config = new EconomyConfig();
        List<String> problems = new ArrayList<>();
        CreditPackages packs = CreditPackages.parse(config.creditStorePackages, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(List.of("c1000", "c14000", "c2700", "c500"), List.copyOf(packs.ids()));
        assertEquals(new CreditPackages.Pack("c2700", 2_700, 2_500), packs.get("c2700").orElseThrow());
        assertEquals(13_000, packs.get("c14000").orElseThrow().usdCents());
        assertEquals(20_000, config.creditStoreMaxGrant);
        assertTrue(packs.ids().stream().allMatch(id -> packs.get(id).orElseThrow().credits() <= config.creditStoreMaxGrant),
                "no default package is above max-grant");
    }

    @Test
    void badLinesAreSkippedWithOneProblemEach() {
        List<String> problems = new ArrayList<>();
        CreditPackages packs = CreditPackages.parse(List.of("ok_1=100|1", "BAD ID=5|1", "c=abc|1", "c2=0|1", "c3=10|-1",
                "c4=10|1.999", "noequals", "ok_1=200|2", "c5=1_000|4.99"), problems::add);
        assertEquals(7, problems.size(), String.join("\n", problems));
        assertEquals(100, packs.get("OK_1").orElseThrow().credits(), "ids are case-insensitive; the first line wins");
        assertEquals(499, packs.get("c5").orElseThrow().usdCents());
        assertEquals(1_000, packs.get("c5").orElseThrow().credits());
        assertEquals(2, packs.size());
    }
}
