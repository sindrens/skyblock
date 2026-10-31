package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Card C1-R1-10: one credit transfer of 1,000 or more alerts staff; untouched old files move from 10,000 to 1,000. */
class WatchCreditThresholdTest {

    @Test
    void creditMinimumsAreOneThousand() {
        WatchConfig config = new WatchConfig();
        assertEquals(1_000, config.bigMin(Currency.CREDITS));
        assertEquals(1_000, config.staffMin(Currency.CREDITS));
        assertEquals(50_000_000, config.bigMin(Currency.MONEY), "other currencies unchanged");
        assertEquals(50_000, config.bigMin(Currency.SHARDS), "other currencies unchanged");
    }

    @Test
    void onlyTheUntouchedOldDefaultMigrates() {
        WatchConfig config = new WatchConfig();
        assertEquals(5, config.version());
        var change = config.valueChanges().get(5).get("rules.big-transfer.min.credits");
        assertTrue(change.matches(10_000), "YAML reads ints");
        assertEquals(1_000L, change.to());
        assertFalse(change.matches(25_000), "owner-edited values are kept");
    }
}
