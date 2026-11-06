# Dynamic prices v2 (plan, 7 Nov 2026 night)

From FULL_PLAN §1. The owner is asleep: the defaults below are used and listed as Q-77.

## Rules
- **Per island, per item, per day**: the first 3,000 items an island sells of one item pay 100%; after that every
  100 more items pay 1% less, down to 60% (at 7,000). Members of one island share it; a player without an island has
  their own count.
- **Recovery at 04:00 (Oslo)**: half of the excess over 3,000 carries into the next day (so a heavy seller is a bit
  lower the next morning too); a quiet day brings it back to 100% within a few days.
- **Weekly drift every Monday 00:00 (Oslo)**: each item gets -10% to +10% in 2% steps for the week, the same for
  everyone, worked out from the week number and the item (nothing stored). "Wheat +6% this week".
- **Hot Item** stays (+25%), **sell bonuses** stay as they are.
- **Profile guard**: the count used is the higher of the island's and the seller's own, so hopping islands or a coop
  alt does not reset it.
- **Display**: item lore and /sell show "Price today: 85%" (green 95% and up, yellow 75% and up, red below) and the
  week's drift.
- The old global model (one price per item for the whole server that falls with every sale) is retired.

## Code (economy module)
1. Pure classes: `DayPrice` (block sum of a sale from a count, factor of the next item, rollover) and `WeeklyDrift`.
2. `DayCounts`: per (scope, item) count with reserve/undo like `PriceBook`, lazy 04:00 rollover, dirty set.
3. Database `sell_island_daily` (scope, item, day, sold), loaded at enable, saved in batches (30 s) and at stop.
4. `SaleCalculator` / `SellServiceImpl`: the factor replaces the multiplier; the seller's profile picks the scope.
5. Max sell for the shop's "no profit" rule: base x 1.10 (drift max) x 1.25 x 4 = **5.5x** (was 7.5x); the shop prices
   stay, so the rule only gets looser.
6. Config `sell.dynamic.*` replaced by the new keys; texts in messages/economy.yml.

## Questions (Q-77, defaults used)
- "Price today" includes the week's drift (yes)? Carried excess eats into the next day's 3,000 (yes)? Profile guard (on)?
  Colours 95% / 75%?
