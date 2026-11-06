# Batch 10: Black Market items, Ranks v2 leftovers, money x100 (plan, 7 Nov 2026)

Owner, 7 Nov 2026: "begynn å planlegg og implementer alt dette". Read first: what already exists (two read-only audits).

## Correction to Q-71
Sell Wand, Auto-Sell Chest and Chunk Collector **are built** (economy and stacking modules). They were only taken off the
Black Market on 7 Nov. Wardrobe Tome is built too, but has no way to be sold. Tool Skin and Hologram have no code.

## A. Black Market items back (update138)
1. Fix before selling:
   - Auto-Sell Chest: breaking it gives back an unbound copy of a bound item. Fix: the returned item stays bound.
   - Chunk Collector: breaking it drops the collector and its contents on the ground, so anyone can take them.
     Fix: they go to the breaker (inventory, then /box), and the item stays bound.
   - Chunk Collector FX-01 (stacked drops): already fixed in code. Add a test.
2. Back on sale (Black Market v6; an owner-edited list is kept): Sell Wand (25 crystals, 100 uses, 2 per rotation),
   Auto-Sell Chest (120, 1 per rotation), Chunk Collector (100, 1 per rotation). These are the old prices.
3. Wardrobe Tome: sold through a new `WardrobeService.createTome`. The common slot sells the Tome (+2,500 wardrobe XP, 30
   crystals); the rare slot sells the Greater Tome (+10,000 XP, 100 crystals). 20 crystals for +10,000 was too cheap.
4. Not built (Q-74 below): Tool Skins need held-item models in the pack (pack v2). Hologram has no description at all.

## B. Ranks v2 leftovers (update139)
Already built: names, looks, LuckPerms groups and perks, auto Ashborn, rank rewards (weekly kit, monthly tag),
`/trash` admin-only. Left:
- Rank names can't be taken as nicknames (reserved words).
- The `/rank preview` line.
- Tests: rank looks parse, and every look matches a LuckPerms group with the same weight and colour.
Not possible yet: prices and upgrade-difference (needs the credit shop), won ranks given by a season script.

## C. Money x100 (update140, separate)
As planned in `ECONOMY_SCALE.md`, with one start-up migration guarded by a marker. It runs only on a database that
already has money and no marker. Built last and released alone, so it can be tested on its own.

## D. Pack v2
Needs your art direction (the current look is what you disliked). Not started.

## Questions (defaults used)
- Q-74: Tool Skins: which skins (the pack has Frostbite, Phoenix Flame and Royal Gold icons) and for which tools?
  Hologram: what should it be? (default: neither is sold yet)
- Q-75: Tome: XP for the wardrobe set (as built) or +1 wardrobe slot (the 4 Nov note)? (default: XP)
