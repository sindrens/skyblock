package net.skybornmc.skyblock.auction;

import java.util.Map;

/**
 * Old bundled texts of messages/auction.yml that became wrong with ECON-SELL-SHOP-AH C3 (the collection bin is gone, items
 * go to /box) and C2 (the summary shows your listings). A live text still equal to its old text is upgraded on load; an
 * owner-edited text is kept ({@code Messages#retire}).
 */
final class AuctionTexts {

    static final Map<String, String> RETIRED = Map.ofEntries(
            Map.entry("sell.fee-unpaid", "<prefix><c:error><sym:cross></c:error> <c:text>Couldn't pay the fee of <fee><c:text>. Your item is back (or in your collection bin)."),
            Map.entry("sell.pending", "<prefix><c:text>Listing... It shows up in a moment, or the item goes to your collection bin."),
            Map.entry("sell.duration.lore", "<c:text><amount>x <c:value><item>\n\n<c:text>This will:\n<c:muted><sym:bullet></c:muted> <c:text>take the item from your hand\n<c:muted><sym:bullet></c:muted> <c:text>charge a fee of <fee> <c:muted>(<fee-percent>%)\n<c:muted><sym:bullet></c:muted> <c:text>list it for <price>\n<c:text>When it sells you get <net> <c:muted>(<tax-percent>% tax: </c:muted><tax><c:muted>)\n<c:muted>Unsold items come back to your collection bin.\n\n<bold><c:action>» CLICK</c:action></bold> <c:text>to list for <time>"),
            Map.entry("buy.processing", "<prefix><c:text>Buying <c:value><item></c:value><c:text>... You get the item or keep your money; check your collection bin in a minute."),
            Map.entry("cancel.confirm-lines", "<c:muted><sym:bullet></c:muted> <c:text>remove <c:value><amount>x <item></c:value> <c:text>from the auction house\n<c:muted><sym:bullet></c:muted> <c:text>give the item back <c:muted>(collection bin if your inventory is full)\n<c:muted>The listing fee (</c:muted><fee><c:muted>) is not refunded."),
            Map.entry("cancel.confirm-lines-refund", "<c:muted><sym:bullet></c:muted> <c:text>remove <c:value><amount>x <item></c:value> <c:text>from the auction house\n<c:muted><sym:bullet></c:muted> <c:text>give the item back <c:muted>(collection bin if your inventory is full)\n<c:muted><sym:bullet></c:muted> <c:text>refund the listing fee of <fee>"),
            Map.entry("staff.remove.lore", "<c:text>The item goes back to <c:value><seller></c:value><c:text>'s\n<c:text>collection bin. Logged with this reason.\n\n<bold><c:action>» CLICK</c:action></bold> <c:text>to remove"),
            Map.entry("staff.remove-button.lore", "<c:text>Pick a reason (<c:value><count></c:value><c:text>), then confirm.\n<c:text>The item goes back to <c:value><seller></c:value><c:text>'s\n<c:text>collection bin.\n\n<bold><c:action>» CLICK</c:action></bold> <c:text>to pick a reason"),
            Map.entry("staff.confirm.details", "<c:text>Back to <c:value><seller></c:value><c:text>'s collection bin.\n<c:text>Reason: <c:value><reason>\n<c:text>The seller gets a message."),
            Map.entry("staff.removed", "<prefix><c:success><sym:check></c:success> <c:text>Removed <c:value><seller></c:value><c:text>'s listing of <c:value><item></c:value><c:text>. It's in their collection bin."),
            Map.entry("staff.removed-seller", "<prefix><c:text>Staff removed your listing of <c:value><item></c:value><c:text>. It's in your collection bin (<c:value>/ah</c:value>)."),
            Map.entry("collection.delivered", "<prefix><c:success><sym:check></c:success> <c:value><item></c:value> <c:text>is in your inventory."),
            Map.entry("notify.expired", "<prefix><c:text>Your <c:value><amount>x <item></c:value> <c:text>listing expired. It's in your collection bin (<c:value>/ah</c:value>)."),
            Map.entry("notify.expired-offline", "<prefix><c:value><count></c:value> <c:text>of your listings expired."),
            Map.entry("mine.entry.ending", "\n<c:muted>Ended; moving to your collection bin."),
            Map.entry("browse.summary.lore", "<c:text>Items for sale: <c:value><listings>\n<c:text>Your balance: </c:text><balance>\n\n<c:muted>Listing fee <fee>%, paid when listing.\n<c:muted>Sales tax <tax>%, taken from the price."));

    private AuctionTexts() {
    }
}
