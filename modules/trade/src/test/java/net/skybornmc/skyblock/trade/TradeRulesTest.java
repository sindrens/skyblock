package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may trade, what can be offered, distance and currency rules. */
class TradeRulesTest {

    private static final TradeRules.PlayerState FREE = new TradeRules.PlayerState(false, false, false, 0);

    @Test
    void crystalsAreNeverTradeable() {
        List<String> all = List.of("money", "crystals", "gems", "shards", "tokens");
        assertFalse(TradeRules.currencyAllowed(Currency.CRYSTALS, true, all));
        assertTrue(TradeRules.currencyAllowed(Currency.MONEY, true, all));
        assertTrue(TradeRules.currencyAllowed(Currency.SHARDS, true, all));
    }

    @Test
    void gemsFollowTheEconomyRule() {
        List<String> all = List.of("money", "gems", "shards");
        assertFalse(TradeRules.currencyAllowed(Currency.GEMS, false, all)); // decided: gems not tradeable
        assertTrue(TradeRules.currencyAllowed(Currency.GEMS, true, all));
        assertFalse(TradeRules.currencyAllowed(Currency.MONEY, false, all));
        assertFalse(TradeRules.currencyAllowed(Currency.SHARDS, true, List.of("money")));
        assertFalse(TradeRules.currencyAllowed(null, true, all));
    }

    @Test
    void allowedCurrenciesWithDefaults() {
        Set<Currency> economy = Set.of(Currency.MONEY, Currency.SHARDS);
        assertEquals(List.of(Currency.MONEY, Currency.SHARDS),
                TradeRules.allowedCurrencies(economy::contains, new TradeConfig().currencies));
        assertEquals(List.of(Currency.MONEY), TradeRules.allowedCurrencies(Set.of(Currency.MONEY, Currency.CRYSTALS)::contains,
                List.of("money", "crystals")));
    }

    @Test
    void distanceRules() {
        assertEquals(Optional.empty(), TradeRules.distance(true, 15 * 15, 16, true));
        assertEquals(Optional.of(TradeRules.Problem.TOO_FAR), TradeRules.distance(true, 17 * 17, 16, true));
        assertEquals(Optional.of(TradeRules.Problem.OTHER_WORLD), TradeRules.distance(false, 0, 16, false));
        assertEquals(Optional.of(TradeRules.Problem.OTHER_WORLD), TradeRules.distance(false, 0, 0, true));
        assertEquals(Optional.empty(), TradeRules.distance(false, 0, 0, false));
        assertEquals(Optional.empty(), TradeRules.distance(true, 1e9, 0, true));
    }

    @Test
    void movingAwayEndsTheTrade() {
        assertFalse(TradeRules.tooFarDuringTrade(true, 20 * 20, 24, true, 16));
        assertTrue(TradeRules.tooFarDuringTrade(true, 25 * 25, 24, true, 16));
        assertTrue(TradeRules.tooFarDuringTrade(false, 0, 24, true, 16));
        assertFalse(TradeRules.tooFarDuringTrade(true, 1e9, 0, true, 16));
        assertFalse(TradeRules.tooFarDuringTrade(false, 0, 0, false, 0));
    }

    @Test
    void requestChecksInOrder() {
        assertEquals(Optional.of(TradeRules.Problem.SELF), TradeRules.request(true, FREE, FREE, true, 0));
        assertEquals(Optional.of(TradeRules.Problem.YOU_BUSY),
                TradeRules.request(false, new TradeRules.PlayerState(true, false, false, 0), FREE, true, 0));
        assertEquals(Optional.of(TradeRules.Problem.FROZEN),
                TradeRules.request(false, new TradeRules.PlayerState(false, true, false, 0), FREE, true, 0));
        assertEquals(Optional.of(TradeRules.Problem.STAFF_MODE),
                TradeRules.request(false, new TradeRules.PlayerState(false, false, true, 0), FREE, true, 0));
        assertEquals(Optional.of(TradeRules.Problem.COMBAT),
                TradeRules.request(false, new TradeRules.PlayerState(false, false, false, 500), FREE, true, 0));
        assertEquals(Optional.of(TradeRules.Problem.TARGET_BUSY),
                TradeRules.request(false, FREE, new TradeRules.PlayerState(true, false, false, 0), true, 0));
        assertEquals(Optional.of(TradeRules.Problem.TARGET_FROZEN),
                TradeRules.request(false, FREE, new TradeRules.PlayerState(false, true, false, 0), true, 0));
        assertEquals(Optional.of(TradeRules.Problem.TARGET_STAFF_MODE),
                TradeRules.request(false, FREE, new TradeRules.PlayerState(false, false, true, 0), true, 0));
        assertEquals(Optional.of(TradeRules.Problem.TARGET_COMBAT),
                TradeRules.request(false, FREE, new TradeRules.PlayerState(false, false, false, 1), true, 0));
        assertEquals(Optional.of(TradeRules.Problem.REQUESTS_OFF), TradeRules.request(false, FREE, FREE, false, 0));
        assertEquals(Optional.of(TradeRules.Problem.COOLDOWN), TradeRules.request(false, FREE, FREE, true, 10));
        assertEquals(Optional.empty(), TradeRules.request(false, FREE, FREE, true, 0));
    }

    @Test
    void moneyOffers() {
        assertEquals(Optional.of(TradeRules.MoneyProblem.NOT_TRADEABLE), TradeRules.money(false, 10, 0, 100, 0));
        assertEquals(Optional.of(TradeRules.MoneyProblem.NEGATIVE), TradeRules.money(true, -1, 0, 100, 0));
        assertEquals(Optional.of(TradeRules.MoneyProblem.TOO_MUCH), TradeRules.money(true, 101, 0, 100, 0));
        assertEquals(Optional.empty(), TradeRules.money(true, 100, 0, 100, 0));
        // New accounts (owner #40, 30 min) can't raise an offer, but can always lower or remove it.
        assertEquals(Optional.of(TradeRules.MoneyProblem.NEW_ACCOUNT), TradeRules.money(true, 10, 0, 100, 60_000));
        assertEquals(Optional.empty(), TradeRules.money(true, 0, 50, 100, 60_000));
        assertEquals(Optional.empty(), TradeRules.money(true, 40, 50, 0, 60_000));
    }

    @Test
    void playtimeRule() {
        assertEquals(30 * 60_000L, TradeRules.playtimeLeft(30, 0));
        assertEquals(60_000L, TradeRules.playtimeLeft(30, 29 * 60_000L));
        assertEquals(0, TradeRules.playtimeLeft(30, 31 * 60_000L));
        assertEquals(0, TradeRules.playtimeLeft(0, 0));
        assertEquals(30, new TradeConfig().newAccountMinutes);
    }

    @Test
    void blockedIds() {
        List<String> blocked = new TradeConfig().blockedIds;
        // PHASE-4 #1: starter kit pieces are tradeable via /trade (only the auction house blocks them).
        assertFalse(TradeRules.idBlocked("starter_pickaxe", blocked));
        assertTrue(TradeRules.idBlocked("WARDROBE_PIECE", blocked));
        assertTrue(TradeRules.idBlocked("starter_pickaxe", List.of("starter_*")));
        assertTrue(TradeRules.idBlocked("tool_starter_axe", List.of("Tool_Starter_*")));
        assertFalse(TradeRules.idBlocked("set_token", blocked)); // Set Tokens are tradeable
        assertFalse(TradeRules.idBlocked("money_note", blocked)); // notes are tradeable
        assertFalse(TradeRules.idBlocked("enchant_book", blocked)); // enchant books are tradeable
        assertFalse(TradeRules.idBlocked(null, blocked));
    }

    @Test
    void combatTimer() {
        assertEquals(0, TradeRules.combatLeft(0, 5000, 10));
        assertEquals(6000, TradeRules.combatLeft(1000, 5000, 10));
        assertEquals(0, TradeRules.combatLeft(1000, 20_000, 10));
        assertEquals(0, TradeRules.combatLeft(1000, 2000, 0));
    }

    @Test
    void pickerSteps() {
        assertArrayEquals(new long[]{1, 10, 100}, TradeConfig.parseSteps(List.of("100", "1", "10", "1000"), new long[]{5}));
        assertArrayEquals(new long[]{5}, TradeConfig.parseSteps(List.of("x", "-3", "0"), new long[]{5}));
    }
}
