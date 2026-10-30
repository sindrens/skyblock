package net.skybornmc.skyblock.common.sell;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of {@link SellService#sell}.
 *
 * <ul>
 *   <li>{@link Status#SOLD}: the money is paid; {@link #giveBack()} holds the stacks that could not be sold (unique
 *       items, items not in the price table) and must be returned by the caller.</li>
 *   <li>any other status: nothing was paid and <b>every</b> stack of the request is in {@link #giveBack()}.</li>
 * </ul>
 *
 * @param quote   what was (or would have been) paid, null when nothing could be priced
 * @param saleId  the sale id (ledger reference {@code sell:<id>}), null when nothing was paid
 * @param balance the profile's money balance after the sale, null when not paid
 */
public record SellResult(Status status, SellQuote quote, List<ItemStack> giveBack, UUID saleId, Long balance) {

    public enum Status {
        /** Paid. */
        SOLD,
        /** No stack of the request can be sold. */
        NOTHING_SELLABLE,
        /** The items are worth less than $1 together at today's prices. */
        TOO_CHEAP,
        /** The balance would go above the largest possible balance. */
        OVERFLOW,
        /** The same sale was already paid (a replayed request). */
        DUPLICATE,
        /** Database or other error: nothing changed. */
        ERROR
    }

    public SellResult {
        giveBack = giveBack == null ? List.of() : List.copyOf(giveBack);
    }

    public boolean sold() {
        return status == Status.SOLD;
    }

    /** Money paid (0 unless sold). */
    public long paid() {
        return sold() && quote != null ? quote.total() : 0;
    }
}
