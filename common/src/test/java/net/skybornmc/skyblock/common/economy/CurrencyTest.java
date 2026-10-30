package net.skybornmc.skyblock.common.economy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyTest {

    @Test
    void currenciesAreMoneyCrystalsGemsShards() {
        assertEquals(List.of("money", "crystals", "gems", "shards"), Arrays.stream(Currency.values()).map(Currency::key).toList());
    }

    @Test
    void oldTokensKeyMapsToCrystals() {
        assertEquals(Optional.of(Currency.CRYSTALS), Currency.byKey("tokens"));
        assertEquals(Optional.of(Currency.CRYSTALS), Currency.byKey(" Tokens "));
        assertEquals(Optional.of(Currency.CRYSTALS), Currency.byKey("crystals"));
        assertEquals("crystals", Currency.byKey("tokens").orElseThrow().key(), "data is always written as crystals");
    }

    @Test
    void keysRoundTripAndUnknownIsEmpty() {
        for (Currency c : Currency.values()) {
            assertEquals(Optional.of(c), Currency.byKey(c.key()));
        }
        assertEquals(Optional.empty(), Currency.byKey("token"));
        assertEquals(Optional.empty(), Currency.byKey(null));
    }
}
