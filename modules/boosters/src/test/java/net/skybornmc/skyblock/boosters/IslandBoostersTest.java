package net.skybornmc.skyblock.boosters;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026: island boosters (same strength adds time, another strength waits) and the booster shop offers. */
class IslandBoostersTest {

    private static final long MIN = 60_000L;

    @Test
    void sameStrengthAddsTimeOtherStrengthIsRefused() {
        long now = 1_000_000L;
        assertEquals(now + 30 * MIN, IslandBoosters.addTo(null, 50, 30 * MIN, now, 240 * MIN));
        IslandBoosters.Run run = new IslandBoosters.Run(50, now + 10 * MIN);
        assertEquals(now + 40 * MIN, IslandBoosters.addTo(run, 50, 30 * MIN, now, 240 * MIN));
        assertEquals(-1, IslandBoosters.addTo(run, 100, 30 * MIN, now, 240 * MIN));
        assertEquals(-2, IslandBoosters.addTo(new IslandBoosters.Run(50, now + 230 * MIN), 50, 30 * MIN, now, 240 * MIN));
        // an ended booster starts fresh, whatever its strength was
        assertEquals(now + 30 * MIN, IslandBoosters.addTo(new IslandBoosters.Run(100, now - 1), 50, 30 * MIN, now, 240 * MIN));
    }

    @Test
    void defaultShopOffersParse() {
        List<String> problems = new ArrayList<>();
        var offers = BoosterShopMenu.parse(new BoostersSettings().shopOffers, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(12, offers.size());
        assertTrue(offers.stream().filter(BoosterShopMenu.Offer::island).allMatch(o -> o.currency() == net.skybornmc.skyblock.common.economy.Currency.CRYSTALS));
        assertTrue(offers.stream().filter(o -> !o.island()).allMatch(o -> o.currency() == net.skybornmc.skyblock.common.economy.Currency.MONEY));
        assertEquals(1, BoosterShopMenu.parse(List.of("island:sell:50:30:credits:5", "personal:sell:25:30:money:100"), problems::add).size());
    }
}
