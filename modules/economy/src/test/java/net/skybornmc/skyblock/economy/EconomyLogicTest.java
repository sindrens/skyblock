package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure economy logic: formatting, /pay rules, the display cache, lock order, transaction validation. */
class EconomyLogicTest {

    private final UUID steve = UUID.randomUUID();

    @Test
    void amountsAreExactBelow100kAndCompactAbove() {
        assertEquals("0", Amounts.plain(0));
        assertEquals("12,500", Amounts.plain(12_500));
        assertEquals("99,999", Amounts.plain(99_999));
        assertEquals("100k", Amounts.plain(100_000));
        assertEquals("1.2M", Amounts.plain(1_234_567));
        assertEquals("9223.3Q", Amounts.plain(Long.MAX_VALUE)); // Q = quadrillion (NumberFormatter)
        assertEquals("$1,000", Amounts.withSymbol("$", Currency.MONEY, 1_000));
        assertEquals("-$500", Amounts.withSymbol("$", Currency.MONEY, -500));
        assertEquals("◆ 250k", Amounts.withSymbol("◆", Currency.CRYSTALS, 250_000));
        assertEquals("✧ 7", Amounts.withSymbol("✧", Currency.SHARDS, 7));
    }

    @Test
    void payRulesFollowTheOwnerDecisions() {
        EconomyConfig c = new EconomyConfig();
        assertTrue(PayRules.check(c, "Alex", "Steve", 1_500, Currency.MONEY, false).isEmpty());
        assertTrue(PayRules.check(c, "Alex", "Steve", 5, Currency.SHARDS, false).isEmpty());
        assertEquals(PayRules.Problem.NOT_TRADEABLE, PayRules.check(c, "Alex", "Steve", 5, Currency.GEMS, false).orElseThrow());
        assertEquals(PayRules.Problem.SELF, PayRules.check(c, "Alex", "alex", 1_500, Currency.MONEY, false).orElseThrow());
        assertEquals(PayRules.Problem.FROZEN, PayRules.check(c, "Alex", "Steve", 1_500, Currency.MONEY, true).orElseThrow());
        assertEquals(PayRules.Problem.BELOW_MINIMUM, PayRules.check(c, "Alex", "Steve", 9, Currency.MONEY, false).orElseThrow());

        assertFalse(PayRules.needsConfirm(c, 999_999, Currency.MONEY));
        assertTrue(PayRules.needsConfirm(c, 1_000_000, Currency.MONEY));
        assertTrue(PayRules.needsConfirm(c, 2_000_000_000L, Currency.MONEY));
        c.payConfirmMoney = 0;
        assertFalse(PayRules.needsConfirm(c, Long.MAX_VALUE, Currency.MONEY));
    }

    @Test
    void decidedDefaults() {
        EconomyConfig c = new EconomyConfig();
        assertEquals(1_000, c.start(Currency.MONEY));
        assertEquals(0, c.start(Currency.GEMS));
        assertTrue(c.tradeable(Currency.MONEY));
        assertTrue(c.tradeable(Currency.SHARDS));
        assertFalse(c.tradeable(Currency.GEMS));
        assertEquals(1_000_000, c.payConfirmAbove(Currency.MONEY));
        assertEquals(30, c.baltopRefreshSeconds);
    }

    @Test
    void cacheIgnoresLateOlderResults() {
        BalanceCache cache = new BalanceCache();
        assertTrue(cache.get(steve, Currency.MONEY).isEmpty());
        cache.apply(steve, Currency.MONEY, 50, 1); // not tracked: ignored
        assertTrue(cache.get(steve, Currency.MONEY).isEmpty());

        cache.track(steve);
        assertTrue(cache.get(steve, Currency.MONEY).isEmpty());
        cache.apply(steve, Currency.MONEY, 700, 12);
        cache.load(steve, Map.of(Currency.MONEY, 500L, Currency.CRYSTALS, 3L)); // older read: money kept
        assertEquals(700, cache.get(steve, Currency.MONEY).getAsLong());
        assertEquals(3, cache.get(steve, Currency.CRYSTALS).getAsLong());

        cache.apply(steve, Currency.MONEY, 900, 14);
        cache.apply(steve, Currency.MONEY, 800, 13); // finished later on another thread, but older
        assertEquals(900, cache.get(steve, Currency.MONEY).getAsLong());

        cache.refresh(steve, Currency.MONEY, 950);
        assertEquals(950, cache.get(steve, Currency.MONEY).getAsLong());
        cache.apply(steve, Currency.MONEY, 1_000, 15);
        assertEquals(1_000, cache.get(steve, Currency.MONEY).getAsLong());

        cache.forget(steve);
        assertTrue(cache.get(steve, Currency.MONEY).isEmpty());
    }

    @Test
    void lockOrderIsTotalAndStable() {
        AccountRef a = AccountRef.player(new UUID(0, 1));
        AccountRef b = AccountRef.player(new UUID(-1, 0)); // high bit set: unsigned compare puts it last
        AccountRef island = AccountRef.island(1L);
        List<AccountRef> list = new ArrayList<>(List.of(island, b, a));
        list.sort(AccountRef.LOCK_ORDER);
        assertEquals(List.of(a, b, island), list);
        assertEquals(new UUID(0, 42), AccountRef.island(42L).owner());
    }

    @Test
    void transactionValidation() {
        AccountRef a = AccountRef.player(steve);
        AccountRef b = AccountRef.player(UUID.randomUUID());
        assertNull(Transaction.transfer(a, b, Currency.MONEY, 1, TxReason.PAY).problem());
        assertNotNull(Transaction.transfer(a, a, Currency.MONEY, 1, TxReason.PAY).problem());
        assertNotNull(Transaction.deposit(a, Currency.MONEY, 0, TxReason.PAY).problem());
        assertNotNull(Transaction.deposit(a, Currency.MONEY, 1, "admin-give").problem());
        assertNotNull(Transaction.deposit(a, Currency.MONEY, 1, TxReason.PAY).idempotencyKey(" ").problem());
        assertNotNull(Transaction.deposit(a, Currency.MONEY, 1, TxReason.PAY).reference("x".repeat(65)).problem());
        Transaction t = Transaction.deposit(a, Currency.MONEY, 1, TxReason.PAY).detail("k", "v").detail("k", null);
        assertTrue(t.details().isEmpty());
    }

    @Test
    void timeFilters() {
        assertNull(TransactionsMenu.parseRange("all"));
        assertNull(TransactionsMenu.parseRange("nonsense"));
        assertEquals(Duration.ofDays(7), TransactionsMenu.parseRange("7d"));
        assertEquals(Duration.ofHours(24), TransactionsMenu.parseRange("24h"));
    }

    @Test
    void startKeyIsPerSeasonCurrencyAndPlayer() {
        assertEquals("start:3:money:" + steve, EconomyServiceImpl.startKey(3, Currency.MONEY, steve));
        assertFalse(EconomyServiceImpl.startKey(3, Currency.MONEY, steve).equals(EconomyServiceImpl.startKey(4, Currency.MONEY, steve)));
    }
}
