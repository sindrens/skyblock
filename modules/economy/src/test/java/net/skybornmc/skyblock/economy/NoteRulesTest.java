package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The /withdraw and /eco note checks (pure logic). */
class NoteRulesTest {

    @Test
    void moneyOnlyByDefault() {
        EconomyConfig config = new EconomyConfig();
        assertTrue(NoteRules.allowed(config, Currency.MONEY));
        assertFalse(NoteRules.allowed(config, Currency.SHARDS));
        assertFalse(NoteRules.allowed(config, Currency.GEMS));
        assertEquals(Optional.of(NoteRules.Problem.NOT_ALLOWED), NoteRules.withdraw(config, 500, Currency.SHARDS, false));
    }

    @Test
    void untradeableCurrenciesStayBlockedEvenWhenListed() {
        EconomyConfig config = new EconomyConfig();
        config.noteCurrencies = List.of("money", "tokens", " Shards ");
        assertFalse(NoteRules.allowed(config, Currency.CRYSTALS), "old 'tokens' = crystals, which are not tradeable (CR3)");
        assertTrue(NoteRules.allowed(config, Currency.SHARDS));
    }

    @Test
    void limitsAndFrozen() {
        EconomyConfig config = new EconomyConfig();
        assertEquals(Optional.empty(), NoteRules.withdraw(config, 100, Currency.MONEY, false));
        assertEquals(Optional.of(NoteRules.Problem.BELOW_MINIMUM), NoteRules.withdraw(config, 99, Currency.MONEY, false));
        assertEquals(Optional.of(NoteRules.Problem.ABOVE_MAXIMUM),
                NoteRules.withdraw(config, config.noteMaxMoney + 1, Currency.MONEY, false));
        assertEquals(Optional.of(NoteRules.Problem.FROZEN), NoteRules.withdraw(config, 500, Currency.MONEY, true));
        // Owner answer 131 (round 1): no new-account limit, a fresh account can make notes.
        assertEquals(Optional.empty(), NoteRules.withdraw(config, 500, Currency.MONEY, false));
    }

    @Test
    void adminNotesOnlyCheckTheMaximum() {
        EconomyConfig config = new EconomyConfig();
        assertEquals(Optional.empty(), NoteRules.admin(config, 1));
        assertEquals(Optional.of(NoteRules.Problem.ABOVE_MAXIMUM), NoteRules.admin(config, config.noteMaxMoney + 1));
    }

    @Test
    void idempotencyKeysAndShortIdsFitTheLedger() {
        UUID id = UUID.randomUUID();
        assertTrue(MoneyNote.withdrawKey(id).length() <= 96);
        assertTrue(MoneyNote.redeemKey(id).length() <= 96);
        assertFalse(MoneyNote.withdrawKey(id).equals(MoneyNote.redeemKey(id)));
        assertEquals(8, MoneyNote.shortId(id).length());
        assertEquals(MoneyNote.Status.VOID, MoneyNote.Status.byKey("garbage"), "unknown status is never redeemable");
    }
}
