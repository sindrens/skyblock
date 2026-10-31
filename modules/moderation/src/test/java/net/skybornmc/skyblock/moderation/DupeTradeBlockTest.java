package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.moderation.SanctionService.Use;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner R3-28: a detected dupe trade-blocks the holders for 24 h; the block ends by itself. */
class DupeTradeBlockTest {

    private static final long NOW = 1_800_000_000_000L;
    private final UUID holder = UUID.randomUUID();

    @Test
    void defaultsAreOnAnd24Hours() {
        ModerationConfig config = new ModerationConfig();
        assertTrue(config.dupeTradeBlock);
        assertEquals(24, config.dupeTradeBlockHours);
    }

    @Test
    void theBlockCoversPayNotesAuctionAndTradeAndEndsByItself() {
        Sanction block = Sanctions.dupeDraft(holder, "Duper", "Copied item found (tool_x)", NOW, Duration.ofHours(24));
        assertEquals(Sanction.Kind.TRADE, block.kind());
        assertNull(block.issuer(), "issued by the system");
        assertEquals(NOW + Duration.ofHours(24).toMillis(), block.expires());
        SanctionRules.Settings s = SanctionRules.Settings.DEFAULT;
        List<Sanction> active = List.of(block);
        for (Use use : List.of(Use.PAY, Use.NOTE, Use.AUCTION_LIST, Use.AUCTION_BUY, Use.TRADE)) {
            assertTrue(SanctionRules.blocking(active, use, s, NOW + 1).isPresent(), "blocks " + use);
        }
        assertFalse(SanctionRules.blocking(active, Use.SHOP, s, NOW + 1).isPresent(), "the shop stays open");
        assertTrue(SanctionRules.blocking(active, Use.PAY, s, NOW + Duration.ofHours(23).toMillis()).isPresent());
        assertFalse(SanctionRules.blocking(active, Use.PAY, s, NOW + Duration.ofHours(24).toMillis()).isPresent(),
                "after 24 h it is over without staff");
        assertTrue(block.expiredAt(NOW + Duration.ofHours(24).toMillis()), "the expiry task then marks it EXPIRED");
        Transaction pay = Transaction.transfer(AccountRef.player(holder), AccountRef.player(UUID.randomUUID()), Currency.MONEY, 10,
                TxReason.PAY);
        assertTrue(SanctionRules.blocks(pay, p -> p.equals(holder) ? active : List.of(), p -> p, s, NOW + 1),
                "the economy safety net refuses the payment too");
        assertFalse(SanctionRules.blocks(pay, p -> p.equals(holder) ? active : List.of(), p -> p, s,
                NOW + Duration.ofHours(24).toMillis()));
    }
}
