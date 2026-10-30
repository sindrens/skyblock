# Auction house (`modules/auction`)

Players sell items to each other for money. Every listing, sale, cancel and expiry is logged (audit category `auction`, actions `ah_list`, `ah_buy`, `ah_cancel`, `ah_expire`, `ah_collect`, `ah_list_failed`) with the full item (incl. the serialized stack, so staff can restore it); payments are in the ledger as `ah_fee`, `ah_buy`, `ah_sale` (`/transactions`, `/logs pay <player>`). Settings: `auction.yml`; texts: `messages/auction.yml`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/ah` (`/auction`, `/auctionhouse`) | Browse menu: categories (automatic by item type), search (chat prompt: item name or a seller's exact name), sort (newest, ending soon, lowest/highest price), Sell held item, My listings (cancel), Collection bin (take items back), buy confirm dialog. Staff with `skyblock.staff.auction.remove` can remove any listing from it (pick a reason) | `skyblock.auction.use` | ✅ |
| `/ah sell <price> [12h\|24h\|48h]` | List the item in your main hand. Without a duration a dialog shows the fee, tax and what you receive, with one button per duration. Prices like `1500`, `1.5k`, `2M` | `skyblock.auction.sell` | ✅ |

Rules (all in `auction.yml`): listing fee 1% (min $1, paid when listing, not refunded on cancel by default), sales tax 5% (taken from what the seller receives), price $10 to $1T, 5 listings at once (more with `skyblock.auction.slots.<n>`, highest wins, capped by `listing.max-slots`), 10 s between listings, the same item at most 3 times per 30 min, players with less than 30 min playtime can't list or buy (`skyblock.auction.bypass-playtime` skips it), frozen players can't use it. Not listable: starter kit items (`items.blocked-ids`, `items.blocked-data-keys`), money notes (until the notes module allows them), dupe-flagged items, filled shulker boxes/bundles, admin blocks (`items.blocked-materials`).

Money goes to the seller even when they are offline; they get a chat message on the sale (online) or on their next join. Expired and cancelled items, and items bought with a full inventory, wait in the collection bin.

Other permissions: `skyblock.staff.auction.remove` (mods and up), `skyblock.auction.bypass-playtime` (admins).

For developers: the listing state machine is `AuctionEngine` (pending → active → buying → sold; active → cancelled/expired; pending → failed). Each change is a conditional `UPDATE … WHERE status = …`; payments use `EconomyService` with idempotency keys `ah_fee:<id>`, `ah_buy:<id>`, `ah_sale:<id>`; listings left in pending/buying by a crash are resolved on startup and every `tasks.cache-resync-seconds`.

## Profiles (PROF)
Listings, the collection bin and seller notices belong to a **profile**: `ah_listings.seller`/`buyer` and `ah_collection.owner` hold profile keys (profile 1 = the player UUID, so old rows are profile 1's). A sale always pays the profile that listed, never the player's active profile. Items for a profile that isn't active (or while its player switches) wait in that profile's collection bin. Buying a listing of your own other profile is refused (`buy.own-profile`, engine outcome `OWN_PROFILE`, audited `profile_transfer_blocked`). A profile with active listings can't be deleted (`profiles.delete-listings`); its collection bin is deleted with it.
