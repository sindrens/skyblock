# Sell prices

**Status:** needs owner input

Your notes on selling: live prices, a push limit per player, own values for Depths blocks, what can't be sold, and price news in chat.

## What you already decided
- **612:** prices are shared by the whole server. Plus price news in chat, for example "sugarcane prices have gone up 22% the past 24 hours" (chat part: CHAT_BROADCASTS.md).
- **613:** limit one player pushing a price down (about 10% per hour suggested): "this has to be planned".
- **658:** prices recover by 2% of the gap to base every 10 minutes.
- **692:** draft prices go live on the test server first.
- **693:** emeralds become sellable (villagers won't be used). A better `/shop` needs a plan (SHOP.md).
- **694:** items that give craft or smelt profit stay unsellable.
- **695:** custom blocks (Depths logs, mine materials) have their own sell values, and are also worth using for other things. Example: "sell for different types of currency".
- **700 / 752:** `/sell all` and satchel Sell all skip Forge materials with a "Keep Forge materials" toggle.
- **703:** "prices refresh more often so they see live", not click, wait and get it much cheaper.
- **704:** vanilla items with a custom name, enchants or damage are unsellable, **and** some custom loot from the Depths.
- **#46:** max total sell bonus +300%. **1 / 2:** sell price lines red, actions bold yellow.

## What exists in the game now
- `sell.yml`: base price per item. Not listed = not sellable. Unique items never sell.
- **Dynamic price:** base × a multiplier from 40% to 150%. Each item sold pushes it down a little. Every 10 min the gap to base closes by 2%. Items unsold for 60 min drift up 10% a day. Steady selling settles at 85% of base.
- **Hot Item:** one item a day sells for +25%.
- **The sale uses the price at the click.** Inside one sale the price drops item by item, so splitting a sale pays the same.
- The menu shows price per item, trend over the last hour (Rising / Falling / Steady) and % from base. The menu does **not** refresh while open.
- No limit per player. No price news.

## The problem / what to decide
1. **Stale prices (703):** the menu shows the price from when you opened it. Prices only step every 10 minutes.
2. **One player can crash a price (613)** for everyone with one huge dump.
3. **Depths blocks (695):** what do they sell for, and in which currency?
4. **What is unsellable (704):** which Depths loot exactly?
5. **Price news (612):** which changes are worth a chat line?

## Our proposal
**1. Live prices.**
- The sell chest and `/sell` menu refresh prices and the total every 2 seconds while open.
- Recovery runs every minute instead of every 10 (same speed per hour, just smoother).
- If the price moved more than 5% since you added items, the Sell button shows the new total before you click. No surprise.

**2. Push limit per player (613).**
- The server price moves from everyone's sales, but **one player can move an item's server price at most 10% per hour**.
- Past that, your own sales keep paying less (your personal price keeps falling), but the server price stops moving. Dumping hurts only the dumper.
- Island members count together (no spreading a dump over 5 members).

**3. Depths blocks (695).**
- Every custom Depths material has its own base price in `sell.yml → custom-items`.
- Most sell for **money**. A few rare ones sell for **crystals** or **shards** instead (for example tier 9+ cores for shards). Shown in the lore: `| Sell Price ◆ 12`.
- Their best use is still crafting (Forge, enchants, sigils): sell price is set below the value of using them.

**4. Unsellable.**
- Vanilla items with a custom name, enchants or damage.
- Custom items marked `sellable: false`: Forge materials of the current tier band, blueprint parts, summon fragments, key fragments, quest items.
- The lore says `| Can't be sold` so nobody wonders.

**5. Price news (612).** Every 30 minutes the broadcaster may post the biggest mover (at least ±15% in 24 h), at most one price line per 30 minutes:
`Market » Sugar cane is up 22% in 24 hours. Sell now with /sell.`

## Questions for you
1. Refresh prices and the total every 2 seconds while the sell menu is open? **Suggested: Yes.**
2. Run price recovery every minute instead of every 10 minutes (same speed per hour)? **Suggested: Yes.**
3. Show the new total on the Sell button when the price moved 5% or more? **Suggested: Yes.**
4. One player moves an item's server price at most 10% per hour; past that only their own price falls? **Suggested: Yes.**
5. Count island members together for that limit? **Suggested: Yes.**
6. Depths materials sell mostly for money, a few rare ones for crystals or shards? **Suggested: Yes.**
7. Keep sell prices of Depths materials below their crafting value? **Suggested: Yes.**
8. Unsellable: renamed/enchanted/damaged vanilla items + current Forge materials, blueprint parts, fragments and quest items? **Suggested: Yes.** Tell us which Depths loot you meant in 704.
9. Show `| Can't be sold` in the lore of unsellable items? **Suggested: Yes.**
10. Price news in chat for movers of ±15% in 24 h, max one per 30 minutes? **Suggested: Yes.**
11. Keep the daily Hot Item (+25%) and announce it once a day? **Suggested: Yes.**
12. Keep floor 40% and ceiling 150% of base? **Suggested: Yes.**

## Your notes (write here)

