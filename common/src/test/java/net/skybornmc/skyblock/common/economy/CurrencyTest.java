package net.skybornmc.skyblock.common.economy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyTest {

    @Test
    void currenciesAreMoneyCrystalsCreditsShards() {
        assertEquals(List.of("money", "crystals", "credits", "credits_earned", "shards"),
                Arrays.stream(Currency.values()).map(Currency::key).toList());
        assertEquals(List.of(Currency.MONEY, Currency.CRYSTALS, Currency.CREDITS, Currency.SHARDS), Currency.listedValues());
    }

    @Test
    void oldGemsKeyMeansEarnedCreditsUntilTheAliasEnds() {
        try {
            Currency.aliasClock(() -> 1_000L);
            Currency.legacyGemsAliasUntil(2_000L);
            assertEquals(Optional.of(Currency.CREDITS_EARNED), Currency.byKey("gems"));
            assertEquals(Optional.empty(), Currency.byCanonicalKey("gems"), "store grants never accept the alias");
            Currency.legacyGemsAliasUntil(1_000L);
            assertEquals(Optional.empty(), Currency.byKey("gems"));
        } finally {
            Currency.aliasClock(null);
            Currency.legacyGemsAliasUntil(Long.MAX_VALUE);
        }
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

    @Test
    void crystalsCanNeverMoveBetweenAccounts() {
        org.junit.jupiter.api.Assertions.assertFalse(Currency.CRYSTALS.transferable());
        org.junit.jupiter.api.Assertions.assertTrue(Currency.MONEY.transferable());
        org.junit.jupiter.api.Assertions.assertTrue(Currency.SHARDS.transferable());
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertNotNull(Transaction.transfer(AccountRef.player(a), AccountRef.player(b),
                Currency.CRYSTALS, 5, TxReason.PAY).problem(), "pay");
        org.junit.jupiter.api.Assertions.assertNotNull(Transaction.transfer(AccountRef.player(a), AccountRef.player(b),
                Currency.CRYSTALS, 5, TxReason.TRADE).problem(), "trade");
        org.junit.jupiter.api.Assertions.assertNotNull(Transaction.transfer(AccountRef.player(a), AccountRef.island(b),
                Currency.CRYSTALS, 5, "island_deposit").problem(), "to an island");
        // earning and spending crystals stay possible
        org.junit.jupiter.api.Assertions.assertNull(Transaction.deposit(AccountRef.player(a), Currency.CRYSTALS, 5, "island_task").problem());
        org.junit.jupiter.api.Assertions.assertNull(Transaction.withdraw(AccountRef.player(a), Currency.CRYSTALS, 5, "enchant").problem());
        org.junit.jupiter.api.Assertions.assertNull(Transaction.transfer(AccountRef.player(a), AccountRef.player(b),
                Currency.MONEY, 5, TxReason.PAY).problem());
    }
}
