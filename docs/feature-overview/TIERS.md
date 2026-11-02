# Tiers, clusters and order (31 Oct 2026)

Tier A = 26 full-design cards, Tier B = 46 half-page cards, Tier C = 30 verify-only cards. The tier of each card is in `INDEX.md` (set in `refresh.py`).

## Review and build order
| Step | Cluster | Cards | Tier | Why first | Build after it |
|---|---|---|---|---|---|
| 0 | Done | C1 Currencies (Locked), B17 Profiles (Locked) | A | foundation | **Wave 1**: credits rename and ledger split, purchase path, B17 fixes (soft delete, switch guards, settings, start money), quick fixes from BUGS-FOUND |
| 1 | Economy flow | C4 Selling, C5 Shop (+C15 price overhaul rules), C14 Season curve, C6 AH, C13 Black Market; C2, C3, C7, C8, C9, C10, C11, C12 as Tier B | A + B | money is 100% of the loop; sell-all-blocks bug lives here | Wave 2: gates (AH, freeze, alerts), sell list, shop undo |
| 2 | Islands and progress | B13 Level and pillars, B14 Upgrades and masteries (tree), B15 Size, B16 /is top; B3, B4, B10, B11, B12 as Tier B | A + B | owner wants pillars, /is top and masteries redone | Wave 3: island level engine, /is upgrade, mastery tree, perks |
| 3 | Seasons and security | I5 Seasons, H7 Anti-dupe; H2, H9 as Tier B | A + B | reset rules and "not possible" principle apply to everything | Wave 3b: season service, dupe checks |
| 4 | First hour | A3 Tutorial, A4 Starter chest, I6 Store (+I10 credit shop) | A | fixes starter-chest and friend-join bugs; store needs C1 | Wave 4: tutorial path/trail/titles, store |
| 5 | Depths and gear | E1 zones, E3 tiers, E5 drops, E10 tools, E12 enchants, F1 progression, F2 ranks, F5 crates, D2 spawners, D6 generators; D1, D3, D4, D5, D7-D10, E2, E4, E6-E9, E11, E13, E14, F3, F4, F6-F11 as Tier B | A + B | defines the 50% and 35% of income; needs steps 1-3 | Wave 5 |
| 6 | PvP and events | I11 PvP loadout (new card), I1, I2, I3, I4 | B | needs gear and economy | Wave 6 |
| 7 | Polish | messages overhaul, holograms, GUI and symbols (I7, I9), rollback H10, I8 launch | B + new | after the systems exist, so nothing is restyled twice | Wave 7 |
| - | Tier C | built or low-impact cards | C | verify pass only | bug fixes as found |

## Rules
- A cluster is reviewed in one sitting; shared questions are asked once.
- A wave is built only from Locked cards.
- New cards (H10, F12, I9, C15, I10, C16, I11, B18) are written when their cluster comes up (see NEW-CARDS.md).
