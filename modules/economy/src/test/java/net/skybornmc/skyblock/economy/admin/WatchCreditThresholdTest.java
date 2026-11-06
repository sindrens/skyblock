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
        assertEquals(5_000_000_000L, config.bigMin(Currency.MONEY), "money x100; other currencies unchanged");
        assertEquals(500_000, config.bigMin(Currency.SHARDS), "owner 7 Nov 2026: shards x10");
    }

    @Test
    void onlyTheUntouchedOldDefaultMigrates() {
        WatchConfig config = new WatchConfig();
        assertEquals(7, config.version());
        var change = config.valueChanges().get(5).get("rules.big-transfer.min.credits");
        assertTrue(change.matches(10_000), "YAML reads ints");
        assertEquals(1_000L, change.to());
        assertFalse(change.matches(25_000), "owner-edited values are kept");
    }

    @Test
    void storeGrantsAreNeverEarnings() {
        WatchConfig config = new WatchConfig();
        assertTrue(config.ignoreReasons.contains("store_grant"));
        assertTrue(config.ignoreReasons.contains("store_chargeback"));
        var change = config.valueChanges().get(6).get("earnings.ignore-reasons");
        assertEquals(WatchConfig.IGNORE_REASONS_V5, change.from());
        assertEquals(config.ignoreReasons, change.to());
        assertEquals(WatchConfig.IGNORE_REASONS_V5, config.valueChanges().get(2).get("earnings.ignore-reasons").to(),
                "the version 2 step still lands on the old list; step 6 adds the store reasons");
    }
}
