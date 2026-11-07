# Plan: store, ranks, credit shop and the rest of Batch 5 (7 Nov 2026)

Owner answers on `DECISIONS_STORE_RANKS_7NOV.html` (7 Nov 2026), then "please plan everything before implementing".
Open points are on `STORE_SUGGESTIONS.html`. Nothing here is built before those answers.

## Owner answers (round 1)
1. **Tebex sells only credit packs** (500 = 5, 1,000 = 10, 2,700 = 25, 14,000 = 130). Everything else is bought in
   game.
2. **Rank prices in credits:** Hatchling 500, Fledgling 1,000, Firewing 2,000, Flamecrest 3,000, Sunfeather 4,500,
   Pyrelord 6,500, Immortal 10,000. An upgrade costs only the difference.
3. **/credit shop: fully built, with everything we discussed**, plus suggestions for more things to sell (page B).
4. **Earned credits** (votes, gg, streaks, thanks): spend on everything except ranks (already planned).
5. **/credit pay:** yes (bought credits only, confirm screen, receipt).
6. **Tradeable or not:** "Other" without a note → page A1 asks.
7. **Chargebacks:** remove what was bought + ban until settled.
8. **Rank sell/XP bonus:** no. Ranks are convenience and cosmetics.
9. **/pv vaults:** yes (1 Hatchling … 4 Immortal, 27 slots each).
10. **Monthly rank reward:** yes, and "a lot of custom cosmetics" (designs from the owner later), **some with perks such
    as speed, all upgradeable with cosmetic points** → Cosmetics v2 (section 6, page C).
11. **Join when full:** yes (from Firewing).
12. **gg after a purchase:** yes, **no daily limit**.
13. **Discord:** later (webhooks, linking, tickets and appeals all in one Discord release).
14. **Island Fuel:** no → removed (with the Fuel Tank upgrade and Fuel Efficiency mastery).
15. **Custom island crops:** no for now; **minions** instead (a farmer minion plants its own crops and is worth more when
    upgraded). **Reminder: we plan minions today.**
16. **Masteries:** Swift Hands and Beast Tamer, and **many more that boost island members at /farm, /mine, /fish and
    /pve** (page D).
17. **Black Market:** a full update with more relevant, good items (page E).
18. **KOTH:** no island XP; it gives a key and **an island booster** (whole island). Boosters come as personal, island
    and server-wide ones, and you can buy them (page F asks with what).

## What already exists (so nothing is built twice)
- Credits: bought and earned kept apart, `/credit bal`, `/credit grant` (Tebex runs it; one grant per order), chargeback
  records, credits in `/trade`. Votes give 3 earned credits, the 7-day streak 10, `/thanks` 1.
- Already costs credits: profile slot 2 (500) / 3 (1,500), tool skins (250-1,000), wardrobe sets Lorekeeper 400,
  Thornward and Stormkeel 900, Island Charter (250 / 500 / 750), the Eclipse key (400, see page B16).
- Rank perks already granted in LuckPerms: `/hat`, `/craft`, `/ec`, `/nick` (+ colours, hex), island fly and `/fly`
  from Flamecrest, loadout slots, profile slots, AH slots 6-16, the weekly kit (S to XL), rank chat colour.
- Not built: `/credit shop`, `/credit pay`, `/credit history`, rank buying, `/pv`, join when full, the monthly reward
  (its pool is empty), gg, streak save (switched off), premium pass for credits (staff-only now), island boosters.

## Releases (in this order, each after the build passes)

### update167: the credit shop
- **`/credit shop`** (also `/store` in game and the Credits button in `/menu`): one menu with tabs **Ranks**,
  **Cosmetics**, **Boosters**, **Extras**; the top shows bought and earned credits; every buy has a confirm screen
  with the price, what you have after, and which credits pay (earned first where allowed).
- **Ranks tab:** the 7 ranks as tiles (perks in the lore, your rank marked), price = the difference to your current
  rank, bought credits only. Buying sets the LuckPerms group for the account (all profiles), a server line
  "Steve became Firewing!", the gg window opens.
- **Extras tab:** premium battle pass 1,000 (once per season, per account), streak save 100 (within 2 days of the
  break), profile slots (move here from the profile menu, same prices), Island Charter (moves here, the Black Market
  keeps the shard price).
- **Cosmetics tab:** tool skins and credit wardrobe sets move here (same prices), the tags sold for credits, and the
  new items from page B.
- **Boosters tab:** server-wide boosters for credits (page F).
- **`/credit pay <player> <amount>`** (bought only, confirm, receipt, refused when frozen or trade-blocked).
- **`/credit history`:** the last 50 credit changes (bought, earned, spent, paid, received, chargeback).
- **gg:** after a rank or credit purchase, everyone who types gg within 30 s gets 1 earned credit, no daily limit;
  one per player per purchase, the buyer and the same IP as the buyer get none.
- **Chargeback:** the credits are taken back first; if spent, the ranks and items bought since the purchase are
  removed and the account is banned until it is settled (staff `/eco revert` lifts it).
- Owner: make the Tebex webstore with the 4 credit packs, each running `credit grant {id} <package> {transaction}`.

### update168: rank perks part 2
- **`/pv [1-4]`**: private vaults per account (1/1/2/2/3/3/4), 27 slots, saved to the database like the Island Vault;
  bound items allowed, nothing lost if a rank expires (vaults above your rank become take-out only).
- **Join when full:** Firewing+ join a full server (up to +10% slots), the others see "Server full, ranks can join".
- **Monthly rank reward:** the monthly chest per rank (contents from page C until Cosmetics v2 exists).

### update169: boosters and KOTH
- **Island boosters** (new kind): the whole island (all members, everywhere), same types as personal (sell, skill XP,
  tool XP, crystals) + shards. Shown in `/boosters` and the island menu.
- **KOTH prize:** 2 Conqueror keys + 500 crystals (as now) + an island booster (sell +50% for 30 min, page G).
- Where boosters are bought: page F.

### update170: island masteries and Island Fuel removed
- Swift Hands and Beast Tamer live, plus the new masteries picked on page D (all boost every island member).
- Island Fuel, the Fuel Tank upgrade and Fuel Efficiency removed from menus and code paths (saved fuel data kept, not
  used).

### update171: Black Market v8
- The new offer list picked on page E, rotation as now (00:00 and 12:00, 1 headline, 3 rare, 4 common + fixed row).

### Later (own plans)
- **Cosmetics v2** (page C): custom particles, trails, break effects …, cosmetic points, upgrade levels, perks; needs
  your designs first.
- **Minions** (plan today): farmer minion plants and harvests its own crops on the island, upgrades make it worth more.
- **Discord** (one release): webhooks (events, punishments, store), `/discord link` (tag + rank role), tickets and
  appeals bot. Needs webhook links and a bot token from you.
- /farm fun (update166 part 2 of the hoe plan: pick your crop, Hot Crop, Fresh Field, Crop Golem, Combo Fever, Lucky
  Clover, Fertilizer, Top Harvesters) is parked until you say where it goes in this order.

## Note: Mojang's rules for selling
Mojang's commercial guidelines (updated 2025) allow selling things that do **not** give a competitive gameplay
advantage or make other players' experience worse. So: ranks with convenience and cosmetics, server-wide boosters
(they help everyone) and cosmetics are fine; **personal or island boosters and cosmetics with real perks (speed) bought
with real money are the risky part**. Page C and F offer safe ways to have them (earned with play, or bought with
in-game money).
