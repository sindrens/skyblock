# Ranks, Titles and Achievements Plan (PLANRANKS)

> **Status:** plan, 2026-10-30. Nothing here is built yet. **Every name, number and rule is a default** the owner can change. All numbers go into typed configs, all text into `messages/<module>.yml`.
> Decisions that matter have an id (`RK` ranks, `TT` titles and tags, `AC` achievements). The owner answers them on the one-click page (§E, JSON at the end).
> Sources: `OWNER_ANSWERS_ROUND2.md` (**355, 375, 376, 377, 386–391, 397, 398, 460**), `OWNER_ANSWERS_ROUND1.md` (**120, 243**), `OWNER_PRINCIPLES.md` (rules 2, 6, 8, 9, 10), `DECISIONS_2026-10-30.md` (P2, P6, P8, PR1–PR5, O9, O11, O18, RW4, L2, #46, #52, #61, crates), `ECONOMY_BALANCE.md` (§1.1, §2.7, §3.4, §7.5, §9.2, §9.4, §9.8), `DESIGN.md` §9 + §11, `FEATURE_IDEAS.md` (45, 47, 88–92), `server/permissions/*.txt`, `modules/perks`, `modules/cosmetics`, `modules/achievements`, `plans/WARDROBE.md` (LD2).

---

## 0. Summary

- **7 donor ranks** with phoenix names. Recommended set: **Hatchling → Fledgling → Firewing → Flamecrest → Sunfeather → Pyrelord → Immortal** (a phoenix growing up). Two other name sets are in §A1.
- **The free rank is renamed** from Ember to **Ashborn** ("born from the ash"). "Ember" stays a crate name only.
- **Two kinds of rank** (owner 387): **bought ranks** are permanent, account-wide and kept every season. **Won ranks** (Torchbearer, Flameheart, Firecrown) are won from the final standings of a season, held for the whole next season, then removed.
- **Upgrades pay the difference** (owner 388).
- **Perks give convenience and some progress, "to a degree"** (owner 389). The progress part has a hard ceiling (**RK-CAP**): at most **+15% sell bonus** (inside the +300% cap) and **+10% skill XP**. Nothing else a rank gives changes money, drops, crystals, gems or island progress. A maxed donor earns at most ~14% more per hour early in the season and ~5–8% more late (§A7).
- **Staff ranks stay** (Builder, Helper, Mod, Admin, Owner). **No Trainee** (owner 390). **`/trash` becomes admin/owner only** (owner 376); spawn trash bins stay for everyone (O9).
- **Titles are earned, tags are owned** (as built). A themed catalog of ~45 titles and ~35 tags, 7 rarities, clear sources (§B).
- **Achievements come in two kinds** (owner 355): **personal** (progress and boost for that profile) and **island** (progress shared by the team, boost for the whole island). Every tier gives boost points; boosts sit inside the existing bonus families with their own small caps. Everything resets with the season; titles stay (owner 354).
- **Milestones:** RK1–RK6, TT1–TT3, AC1–AC5 (§D). About 45 yes/no questions for the owner (§E).

---

## 1. Rules this plan follows

| Rule | What it means here |
|---|---|
| Donations give "both, to a degree" (389) | Convenience and looks first. A small, capped progress part: sell bonus and skill XP only |
| **RK-CAP: no pay-to-win beyond a stated cap** | Rank sell bonus max +15% (the value ECONOMY_BALANCE already uses). Rank skill XP max +10%. No rank perk adds money, crystals, drops, gems, island XP, upgrades, keys of high crates or PvP power |
| Profiles are islands (P2, P4, PR3) | A rank is account-wide and works on every profile. Vault contents are per profile. Nothing a rank gives can move value between your own profiles |
| Keys are earned only (DESIGN §9, 2026-10-29) | Monthly rank keys must be **claimed by playing** (5 h that month) and are low crates only (§A5, RK17) |
| No waiting, no busywork (principle 1) | Claims are one click in `/menu` → Rank rewards. No new commands needed for the rank system |
| Bedrock first | Every rank menu is click-only. Rank names are ≤ 10 characters so prefixes fit Bedrock chat and tab |
| English only (principle 6) | All names and texts in English |
| Everything audited | Rank grants, upgrades, won-rank grants, claims, title grants, achievement tiers and boosts all write audit rows |

---

# A. RANKS

## A1. Names (owner 386: "more relevant to the theme, 7 ranks")

Names avoid the crate names (Ember, Cinder, Blaze, Inferno, Phoenix, Eclipse, Conqueror) so a rank never sounds like a key.

| # | Set 1: **Phoenix life** (recommended) | Set 2: Rising fire | Set 3: Flame keepers |
|---|---|---|---|
| Free | **Ashborn** | Tinder | Ashwalker |
| 1 | **Hatchling** | Spark | Kindler |
| 2 | **Fledgling** | Kindle | Flamekeeper |
| 3 | **Firewing** | Flare | Firebrand |
| 4 | **Flamecrest** | Wildfire | Pyromancer |
| 5 | **Sunfeather** | Firestorm | Sunforged |
| 6 | **Pyrelord** | Sunfire | Starfire |
| 7 | **Immortal** | Ascendant | Eternal |

**Why Set 1:** it tells one story (a phoenix hatches, grows wings, becomes immortal). It fits the server name. It never clashes with crate names or the free tags. Set 2 reuses "Spark" (a free tag today). Set 3 sounds like classes, not ranks.

**Free rank (owner 390):** "Ashborn" fits every set's story: every player rises from the ash. It replaces "Ember" in the `ember` LuckPerms group display name, prefix, `/freerank` texts and docs. The group id can stay `ember` internally (no data change), or be renamed with a migration (RK3).

### A1.1 Colors and prefixes (defaults)
Donors use a bracket prefix in normal case; staff keep **bold caps** (`MOD`, `ADMIN`). So a donor never looks like staff.

| Rank | Prefix (Bedrock-safe classic) | Hex color (Java) | Badge glyph (resource pack, RP-D17) |
|---|---|---|---|
| Ashborn | `&7[Ashborn]` | `#BFB3A8` ash | N01 |
| Hatchling | `&e[Hatchling]` | `#F2C46B` | N02 |
| Fledgling | `&6[Fledgling]` | `#FFA43A` | N03 |
| Firewing | `&6[Firewing]` | `#FF7A1F` | N04 |
| Flamecrest | `&c[Flamecrest]` | `#FF4E2E` | N05 |
| Sunfeather | `&e[Sunfeather]` | `#FFD84D` | N06 |
| Pyrelord | `&4[Pyrelord]` | `#D81E3A` | N07 |
| Immortal | `&6&l[Immortal]` | gradient `#FF3D2E → #FFB000 → #FFF2A8` | N08 |
| Torchbearer (won) | `&e[Torchbearer]` | `#FFE27A` | N09 |
| Flameheart (won) | `&c[Flameheart]` | `#FF5A3C` | N10 |
| Firecrown (won) | `&6&l[Firecrown]` | gradient `#FFB000 → #FF3D2E` | N11 |

The art brief §N lists N01–N11 with these names and colours (updated by REV3-FIX).

## A2. Staff ranks (owner 390, 391)

Unchanged: **Builder, Helper, Mod, Admin, Owner**. **No Trainee.** Only the owner hands out staff ranks (391).

LuckPerms weights decide which prefix shows. Staff must always win over donors:

| Group | Weight now | Weight planned |
|---|---|---|
| default | 0 | 0 |
| ember (Ashborn) | 1 | 1 |
| 7 donor groups | — | 2–8 |
| 3 won groups | — | 2–8 band too (same weight as the tier they copy, see A4) |
| builder | 5 | **9** (moved above donors) |
| helper / mod / admin / owner | 10 / 20 / 30 / 100 | unchanged |

## A3. Bought ranks (permanent)

- Bought on the store (Tebex) with **store credits** (DESIGN §11). Delivered by a console command into LuckPerms (`lp user <name> parent add <rank>`), plus an audit row.
- **Account-wide**, works on **every profile** (PR3). **Kept across seasons** (387). Never expires.
- **Inheritance:** each donor group inherits the one below (`fledgling` → `hatchling` → `ember`). A player keeps only the highest donor group; buying a higher one removes the lower group (so the prefix is right).
- **Upgrade pays the difference** (388): price of the new rank minus the **list price** of the rank you own. Example: Firewing (€20) → Pyrelord (€65) costs €45. Tebex does this with one "upgrade" package per pair, or with a store credit discount (RK13).
- **Chargeback or refund:** the rank is removed and the player is flagged for staff (RK15). Perks already used (kits, keys) are not taken back from other players' hands; the account keeps a negative note.
- **Gifts:** a rank can be bought for another player (Tebex gift). The audit row stores both names.

### A3.1 Price ladder (default, set in Tebex, not in code)
| Rank | Price | Upgrade from the rank below |
|---|---|---|
| Hatchling | €5 | — |
| Fledgling | €10 | €5 |
| Firewing | €20 | €10 |
| Flamecrest | €30 | €10 |
| Sunfeather | €45 | €15 |
| Pyrelord | €65 | €20 |
| Immortal | €100 | €35 |

## A4. Won ranks (season only, owner 387)

Three won ranks. They are **prizes**, not purchases. Each one copies the perks of one donor tier.

| Won rank | Perks equal to | How it is won (final standings of a season) |
|---|---|---|
| **Firecrown** | Pyrelord (tier 6) | #1 team island and #1 solo island (every member) |
| **Flameheart** | Flamecrest (tier 4) | #2–3 team and solo islands; #1 of each season board (5 skills, KOTH captures, boss damage) |
| **Torchbearer** | Fledgling (tier 2) | #4–10 team and solo islands; #2–3 of each season board; the Final Week event winners |

Rules:
- **When it is held:** won at the end of season N, given at the start of season N+1, **removed at the reset to season N+2**. One season, then gone (387).
- **Who qualifies:** at least **10 h played** in season N, and a member of that island for the **last 7 days** of the season (no "carry" by joining at the end) (RK9).
- **Best of each perk, no stacking:** a player with a bought rank and a won rank gets, perk by perk, the better of the two. Example: Firewing + Flameheart = Flamecrest perks for that season.
- **Prefix:** the player picks which rank shows in `/display` (bought or won). Default: the won rank while it lasts (it is rarer).
- **A permanent memory:** every winner also gets a season title that never goes away, e.g. `Firecrown · S1` (§B4). The rank goes, the title stays.
- **Technical:** LuckPerms groups `torchbearer`, `flameheart`, `firecrown` given with `lp user … parent add` at the season start and removed by the season reset script (Phase 12 plan). The list of winners is computed from the frozen season boards and shown to an admin for approval before it is applied (RK10).
- **Profile slot from a won rank:** if the copied tier gives a profile slot, the slot is lost at the reset like other non-gem slots (P8); a profile in it is locked but deletable (owner 120).

## A5. Perk table

"Tier" columns: Free = Ashborn, 1–7 = donor ranks. A won rank uses the column of the tier it copies. Every line is a config value or a permission node; each rank gets the lines of its column (higher columns include everything to the left unless a number replaces it).

| Perk | Free | 1 Hatchling | 2 Fledgling | 3 Firewing | 4 Flamecrest | 5 Sunfeather | 6 Pyrelord | 7 Immortal | Type |
|---|---|---|---|---|---|---|---|---|---|
| Rank prefix + name color | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ (gradient) | look |
| Rank tag (own tag with the rank name, kept forever) | pick 1 of 3 free tags | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | look |
| Name color picker (palette) | — | — | — | — | 6 colors | 6 | 12 colors | 12 + gradients | look |
| Island border colors (extra, on top of gem/crate ones) | — | — | 1 | 1 | 2 | 2 | 3 | 4 (animated) | look |
| Particle trail (when trails exist, idea 89) | — | — | — | — | — | 1 | 1 | 2 | look |
| Join/leave message | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | look |
| `/hat` | — | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | convenience |
| `/craft` | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | convenience |
| `/ec` (ender chest) | — | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | convenience |
| `/nick` plain | — | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | convenience |
| `/nick` with `&` colors | — | — | — | — | — | ✔ | ✔ | ✔ | look |
| `/nick` with hex + gradients | — | — | — | — | — | — | — | ✔ | look |
| Island fly (`skyblock.island.fly`, owner 243) | — | — | — | — | ✔ | ✔ | ✔ | ✔ | convenience |
| AH listing slots (default 5) | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 15 | convenience |
| `/pv` vaults (27 slots each, per profile) | — | 1 | 1 | 2 | 2 | 3 | 3 | 4 | convenience |
| Loadout presets (2 free, LD2) | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 5 | convenience |
| Profile slots (1 free; gems also unlock 2 and 3) | 1 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | convenience |
| Join when the server is full (reserved slots) | — | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | convenience |
| Queue priority (when a proxy queue exists) | — | low | low | mid | mid | high | high | top | convenience |
| Lockdown bypass (owner 460) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | access |
| **Sell bonus** (sell family, +300% total cap) | 0% | +2% | +4% | +6% | +8% | +10% | +12% | **+15%** | progress |
| **Skill XP bonus** (skill XP family, +300% cap) | 0% | +1% | +2% | +3% | +4% | +6% | +8% | **+10%** | progress |
| Weekly kit (bound items, §A5.2) | — | S | S | M | M | L | L | XL | small progress |
| Monthly keys (claim after 5 h played that month) | — | 1 Ember | 2 Ember | 2 Ember, 1 Cinder | 3 Ember, 1 Cinder | 3 Ember, 2 Cinder | 3 Ember, 2 Cinder, 1 Blaze | 4 Ember, 2 Cinder, 1 Blaze | small progress |
| `/trash` | — | — | — | — | — | — | — | — | admin/owner only (376) |

### A5.1 Where each perk lives
| Perk | Module / node | State |
|---|---|---|
| Prefix, color, weight | `server/permissions/base.txt` (groups) | Ashborn exists as `ember`; donor groups new |
| Rank tag | `tags-titles.yml` tag per rank, granted by a `RankTagSync` on join (rank lost → tag kept, it is permanent) | new |
| Name color picker, border colors, trails | `modules/cosmetics` (`/display` already edits the look) + islands borders | new nodes |
| Join message | `server/permissions/essentials.txt` (already "ranks only") | add donor groups |
| `/hat` `/craft` `/ec` `/nick` | `modules/perks` nodes exist (`skyblock.perks.*`) | grants only. `/ec` and `/craft` already must be blocked in warzone, boss arena and 10 s after combat (owner 377) |
| Island fly | `skyblock.island.fly` exists (ISL2) | grants only |
| AH slots | `skyblock.auction.slots.<n>` exists for 6, 7, 8, 10, 15, 20 | add nodes 9, 11, 12 |
| `/pv` vaults | not built (`/pv` is in `docs/commands/planned.md`) | new module or perks feature (RK5) |
| Loadout presets | `skyblock.loadout.slots.<n>` exists (commented grants) | grants with the new names |
| Profile slots | `skyblock.profiles.slots.<n>` exists (commented grants) | grants with the new names |
| Reserved slots | `skyblock.join.full` (new node, Paper login event) | new, small |
| Sell bonus | `skyblock.sell.rank.<percent>` read by the sell multiplier (one family, `MultiplierCap`) | check what economy reads today; one node per step |
| Skill XP bonus | `skyblock.skills.rank.<percent>` into the skill XP family (`BonusService`) | new, small |
| Kits and keys | `/menu` → **Rank rewards** button (no new command) | new, needs crates (keys) |
| `/trash` | `skyblock.perks.trash`: only admin + owner | change the plan line "donators" (O9 is replaced by 376) |

### A5.2 Weekly kit (default)
Claimed once a week per **profile** from `/menu` → Rank rewards. All items are **bound** (like starter-kit items): not sellable, not tradeable, not on the AH, not in `/box` gifts to others. So a kit can never become money for someone else.

| Size | Contents |
|---|---|
| S | 16 cooked food, 64 basic building blocks, 1 h island fuel |
| M | S + 2 h island fuel (3 h total), 1 firework show (cosmetic) |
| L | M + 3 h fuel (6 h total), 1 Wardrobe dye |
| XL | L + 4 h fuel (10 h total), 1 hologram line token (idea 74) |

Fuel is capped by the island fuel tank, so extra fuel only fills what the tank holds.

## A6. What changes from the old plan
| Old (DESIGN §11, permission files) | New |
|---|---|
| 5 ranks Squire → Monarch | 7 ranks Hatchling → Immortal |
| Free rank "Ember" | "Ashborn" (crate "Ember" keeps its name) |
| Progress Boost 2–10% on pillar XP | Skill XP +1–10% (personal) and sell +2–15%. **No island/pillar XP from ranks** (a donor should not push the whole island) (RK20) |
| Profile slot 2 = Knight+, slot 3 = Warlord+ (PR5) | slot 2 = Firewing+, slot 3 = Pyrelord+ |
| Loadout Knight 3, Warlord 4, Monarch 5 (LD2) | Fledgling 3, Flamecrest 4, Pyrelord 5 |
| `/trash` for donators (O9) | admin + owner only (376); spawn bins for all |
| Ashborn perks "not decided" | +1 AH slot, lockdown bypass, a free tag, name color |
| Free tags ember, spark, ash | spark, smoke, kindling (ember stays a crate tag) (TT4) |

## A7. Balance check against `ECONOMY_BALANCE.md`

| Check | Number | Verdict |
|---|---|---|
| Sell bonus | Max +15% = the "best rank +15%" the sheet already uses (§2.7, §12 item 7). The model players use 0 / 4 / 8% (casual / regular / grinder) = no rank / Fledgling / Flamecrest | **No model change.** Best of everything stays 305% → capped 300% |
| Income edge, early | Casual day 3: +5% → ×1.05 free vs ×1.20 Immortal | Immortal sells **~14% more** per item |
| Income edge, late | Regular month 3: +100% → ×2.00 free vs ×2.15 Immortal | **~7.5% more**. Grinder month 3 (+120% w/o rank): ~6.8% more |
| Skill XP +10% | Skill cost grows ×1.20 per level to 35, ×1.13 after (§3.4). +10% XP = 9% less time to the same level = **~0.5 level ahead** early, **~0.8** late | Small. A grinder reaches mine tier 12 on ~day 47 instead of 51 |
| Keys | Immortal: 7 keys a month (no Inferno or higher). AFK pool alone gives up to 2 Ember + 1 Cinder + 1 Blaze **per day** (§7.5) | Monthly rank keys ≈ **2 days of AFK pool**. Fine |
| Kits | Bound items + fuel (capped by the tank) | No money enters the economy |
| AH slots | More listings = more 1% fees + 5% tax | Sink grows, no money created |
| Profile slots | More islands, each starts from zero, nothing moves between them (P4) | No power per island |
| Fly, /craft, /ec, /hat | No effect on money. `/ec` and `/craft` blocked in combat zones (377) | Convenience only |
| PvP / KOTH | No rank perk adds damage, health or armor. Sets are normalized in the warzone | **No PvP edge** |

**RK-CAP stated:** "A rank never gives more than +15% sell bonus and +10% skill XP, and nothing else that changes money, drops, crystals, gems, island progress or combat." The economy model's self-check "best of everything" covers it.

**Won ranks and snowballing:** a Firecrown island gets Pyrelord perks (+12% sell) for one season. With island upgrades, masteries and sets this is a small part. It ends after one season. Acceptable (RK8).

## A8. Risks
| Risk | Plan |
|---|---|
| **Mojang commercial usage guidelines** (DESIGN §11 warning, REVIEW) | The owner must check the current Minecraft Usage Guidelines before the store opens: paid ranks with gameplay perks, monthly keys from a paid rank, and whether paid items may come from random rewards. If they forbid it, set the "progress" lines to 0 (configs) and keep the look + convenience lines. RK1 |
| Players feel donors win | RK-CAP shown on the store page and in `/help ranks`: exact numbers, nothing hidden |
| Chargebacks | Rank removed, staff alerted, account flagged (RK15) |
| Alt farming of monthly keys | Claim needs 5 h played that month on that account; one claim per account, not per profile |
| A won rank given wrongly | Admin approves the winners list before it is applied; every grant audited and undoable |

---

# B. TITLES AND TAGS

## B1. What is what (as built, kept)
- **Title** = **earned** (achievements, seasons, events, special). Shown **before** the rank: `First Flame [Firewing] Pouched (Spark) » hi`. Not tradeable.
- **Tag** = **owned** (crates, store, ranks, free rank, holiday events). Shown **after** the name in muted brackets. Crate and store tags come as a **tag item** you redeem; spare tag items can be traded and sold on the AH.
- One title and one tag at a time, picked in `/tags`. Both are **account-wide** and **kept every season**.
- Who may give them (owner 397): **admins** give any title or tag; **mods** give event titles from a short list (`event_winner`, `quick_spark`, `ash_seeker`, `lava_angler`). Earned automatic titles can't be given by hand (as built).

## B2. Rarity ladder (as built: 7 rarities)
| Rarity | Color role | Typical source | Share of all titles |
|---|---|---|---|
| Common | text grey | first steps, free picks | ~10% |
| Uncommon | green | early achievements, Ember/Cinder crates | ~15% |
| Rare | blue | mid achievements, Blaze crate, event wins | ~25% |
| Epic | purple | high achievement tiers, Inferno crate | ~25% |
| Legendary | gold | top tiers, Phoenix crate, season winners | ~15% |
| Mythic | phoenix gradient | season #1, collection log, Eclipse monthly | ~5% |
| Special | red | staff-given, launch, one-off honors | ~5% |

## B3. Display rules
- Chat: `<title> <rank> <name> (<tag>) » message`. Tab: same without the message. Hover card shows rank, island, playtime (owner 302) plus the title's rarity and source.
- Max **16 visible characters** per title or tag (Bedrock chat width).
- A secret title shows as `???` in `/tags` until earned (as built).
- Season titles carry the season number: `Firecrown · S1`. Each season makes new ids (`s1_firecrown`), so old ones stay unique.
- A player can hide their title or tag (empty pick), never someone else's.
- Rank tags show only the rank's own name and color; they can't be faked by a store or crate tag (reserved names in the editor).

## B4. Title catalog (default)

### B4.1 Achievement titles: keep the ids, give them themed names (TT2)
Ids are database values and never change. Only the shown name changes.

| Id (kept) | Shown now | Proposed name | Rarity | How |
|---|---|---|---|---|
| `prospector` | Prospector | **Magma Delver** | epic | Mining 100 |
| `lumberjack` | Lumberjack | **Ashwood Feller** | epic | Foraging 100 |
| `warrior` | Warrior | **Flameblade** | epic | Combat 100 |
| `harvester` | Harvester | **Sunreaper** | epic | Farming 100 |
| `angler` | Angler | **Ember Angler** | epic | Fishing 100 |
| `deep_diver` | Deep Diver | **Voidwalker** | rare | Mining 66 (last Depths tier) |
| `all_rounder` | All-Rounder | **Five Flames** | epic | Skill total 300 |
| `grandmaster` | Grandmaster | **Grand Pyre** | legendary, secret | Any skill 150 |
| `quarry_master` | Quarry Master | **Stoneforger** | epic | 1M island blocks mined |
| `woodcutter` | Woodcutter | **Timberblaze** | epic | 500k island logs |
| `green_thumb` | Green Thumb | **Ashbloom** | epic | 500k island crops |
| `builder` | Builder | **Hearthbuilder** | rare | 250k blocks placed |
| `crowd_pleaser` | Crowd Pleaser | **Beacon** | epic | 2,500 unique visitors |
| `exterminator` | Exterminator | **Purifier** | epic | 100k island mobs |
| `slayer` | Slayer | **Pyre Slayer** | legendary | 1M stacked kills |
| `overkill` | Overkill | Overkill (keep) | epic, secret | 64 from one stack at once |
| `merchant` | Merchant | **Ember Trader** | epic | 10M items sold |
| `mogul` | Mogul | **Gold Phoenix** | legendary | $1B earned by selling |
| `tycoon` | Tycoon | **Gilded Wing** | epic | $1B held at once |
| `jackpot` | Jackpot | Jackpot (keep) | epic, secret | One $1M sale |
| `dealmaker` | Dealmaker | Dealmaker (keep) | rare | 250 trades |
| `runesmith` | Runesmith | **Runeforger** | epic | 200 enchants applied |
| `toolsmith` | Toolsmith | **Anvilheart** | rare | Tool level 50 |
| `phoenix_reborn` | Reborn | Reborn (keep) | epic | First tool prestige |
| `trendsetter` | Trendsetter | **Plumage** | epic | 16 sets owned |
| `set_master` | Set Master | **Radiant** | legendary | A set at level 50 |
| `dedicated` | Dedicated | **Everburning** | epic | 500 h played |
| `unstoppable` | Unstoppable | **Undying Flame** | legendary, secret | 1,000 h played |
| `explorer` | Explorer | **Skywanderer** | rare | 500 islands visited |
| `collector` | Collector | **Keeper of Ashes** | mythic | Whole collection log |
| `night_owl` | Night Owl | **Moonflame** | uncommon, secret | Play at the quietest hour |
| `newcomer` | Newcomer | **Hatched** | common | First join (auto-title) |

### B4.2 New titles
| Id | Name | Rarity | Source | How |
|---|---|---|---|---|
| `founder` (exists) | **First Flame** | special | launch | 1 h played in the first 7 days of Season 1 (TT5) |
| `island_architect` (exists) | Architect (keep) | rare | island achievement | Island level 50 |
| `s<N>_firecrown` | Firecrown · S<N> | mythic | season | Won the Firecrown rank |
| `s<N>_flameheart` | Flameheart · S<N> | legendary | season | Won the Flameheart rank |
| `s<N>_torchbearer` | Torchbearer · S<N> | epic | season | Won the Torchbearer rank |
| `season_champion` (exists) | replaced by `s<N>_firecrown` | — | — | Kept for anyone who has it |
| `koth_king` (exists) | **Hillkeeper** | legendary | event | Win a KOTH |
| `boss_slayer` (exists) | **Titan Breaker** | epic | event | Final hit on a world boss |
| `envoy_hawk` | Supply Hawk | rare | event | Open 50 envoy crates |
| `ash_seeker` | Ash Seeker | rare | event (mods may give) | Win a treasure hunt |
| `lava_angler` | Lava Angler | rare | event (mods may give) | Win a fishing contest |
| `quick_spark` | Quick Spark | uncommon | event (mods may give) | Win 25 chat games |
| `weekly_blaze` | Weekly Blaze | epic | event | Win a weekly island competition (idea 77) |
| `last_ember` | Last Ember | legendary | event | Win the Final Week event |
| `flame_voice` | Voice of the Flame | rare | voting | 500 votes in one season |
| `streak_keeper` | Unbroken | epic | daily login | 30-day login streak |
| `honorable` (exists) | Honorable (keep) | rare | automatic | 60 days clean |
| `event_winner` | Champion of Flames | rare | event (mods may give) | Any staff-run event |
| `bug_hunter` | Bug Hunter | special | staff (admin) | Reported a real bug or exploit |
| `hearth_helper` | Hearth Helper | special | staff (admin) | Helped build the server |
| `island_*` (6) | see C3 | rare–mythic | island achievements | Last tier of an island achievement (every member gets it) |

## B5. Tag catalog (default)

| Group | Tags | Rarity | Source | Tradeable item? |
|---|---|---|---|---|
| Free pick (Ashborn) | **Spark**, **Smoke**, **Kindling** | common | `/freerank`, pick 1 of 3 | no |
| Rank tags | Hatchling, Fledgling, Firewing, Flamecrest, Sunfeather, Pyrelord, Immortal | by tier: uncommon → mythic | bought rank (kept forever) | no |
| Ember crate | Warm, Glow, Smolder | common | crate | yes |
| Cinder crate | Ashes (exists), Soot, Crackle | uncommon | crate | yes |
| Blaze crate | Ember (exists), Firefly, Scorch | rare | crate | yes |
| Inferno crate | Skyborn (exists), Wildfire, Magma | epic | crate | yes |
| Phoenix crate | Phoenix (exists), Rebirth, Solar | legendary | crate or store | yes |
| Eclipse crate | one new tag per month (e.g. "Frostfire" in December), never comes back; Inferno (exists) is the first | mythic | monthly crate | yes |
| Conqueror crate | Conqueror, Warbringer | legendary | KOTH key | yes |
| Store only | OG (exists), Patron, Torch | epic | store | yes |
| Holiday | Pumpkin Flame (October), Frostfire (December), Lovebird (February) | rare | holiday events, that month only | yes |

Rules: tags are looks only. A store tag never shows a rank name. Rank tags are not items (they can't be sold). The free `ember` pick moves to Blaze-crate only (TT4).

---

# C. ACHIEVEMENTS

## C1. Two kinds (owner 355)

| | **Personal achievements** | **Island achievements** |
|---|---|---|
| Progress | per **profile** (as built) | per **island**, all members add to it |
| Boost goes to | that profile only | **everyone on the island** (members, not coop or visitors) |
| Boost kinds | skill XP, tool XP, crystals, rare finds, PvE damage, AH slots | island production, island crystals, island XP, fuel efficiency |
| Title | to the account, at the last tier (as built) | to **every member** at the last tier (only members for ≥ 3 days) |
| Reset | at the season reset (with the profile) | at the season reset (with the island) |
| Leaving | profile progress stays with the profile | a member who leaves takes nothing; the island keeps its progress and boosts |
| Menu | `/achievements` (approved, 356) → **Personal** tab | `/achievements` → **Island** tab; also from `/is` |

## C2. Tiers and points
Every achievement has 1–5 tiers (some secret ones have 1). Each finished tier gives **boost points** in its category.

| Tier | Mark | Points | Rough effort |
|---|---|---|---|
| I | ✦ I | 1 | first session |
| II | ✦ II | 2 | first days |
| III | ✦ III | 3 | first weeks |
| IV | ✦ IV | 5 | month 2 |
| V | ✦ V | 8 | end of season (grinders) |

Points → boost: every point adds a fixed step to its category's boost, up to the category cap. Fast start, slower later (#61): early tiers are cheap, top tiers take long but give more points.

## C3. Personal categories, boosts and caps

| Category (as built + new) | Boost (family) | Step per point | Cap | Existing achievements (examples) |
|---|---|---|---|---|
| Skills | skill XP (skill XP family, cap +300%) | +0.3% | **+10%** | prospector, lumberjack, warrior, harvester, angler, well_rounded |
| Gear | tool XP (tool XP family, cap +100%) | +0.4% | **+10%** | runesmith, toolsmith, trendsetter, set_master |
| Combat | PvE mob damage (mob damage family; **never in PvP**) | +0.3% | **+8%** | exterminator, slayer, overkill |
| Exploring (+ Depths, new) | rare finds (rare finds family, cap +150%) | +0.3% | **+8%** | explorer, into_the_void, new Depths tier ones |
| Economy | +1 AH slot at 12 points, +2 at 30 | — | **+2 slots** | merchant, mogul, dealmaker, millionaire |
| Island (own stats) | crystals (crystals family, cap +200%) | +0.25% | **+6%** | quarry_master, woodcutter, green_thumb, builder |

- **No personal achievement boosts sell or money directly** (AC3). The sell family stays as the balance sheet has it.
- **Personal total cap:** all personal boosts together reach the caps only with ~all tiers of a season. A regular player ends a season at about half the caps (model it in AC5).
- Every boost goes through `BonusService` so the family caps still apply. They are listed in `/achievements` with "x / cap".

## C4. Island achievements (new)

Progress is shared. Metrics come from island services that exist or are planned (`IslandStatsService`, island level, masteries, spawners, bank, visits).

| Island achievement (proposal) | Metric | Tiers (goals) | Title at last tier |
|---|---|---|---|
| Rising Island | island level | 10 / 25 / 50 / 75 / 100 | Island Ascendant (legendary) |
| Deep Roots | crops harvested by all members | 10k / 100k / 1M / 5M | Ashbloom Grove (epic) |
| Stone Heart | blocks mined on the island | 10k / 100k / 1M / 10M | Forgeheart (epic) |
| Spawner Nest | spawners stacked (total) | 8 / 64 / 256 / 1,024 | Nestkeeper (epic) |
| Mastery Path | mastery levels bought | 5 / 20 / 50 / 100 | Path of Flame (legendary) |
| Full Vault | island bank deposits (total) | $1M / $100M / $10B | Treasure Hoard (epic) |
| Open Doors | unique visitors | 25 / 250 / 1,000 / 5,000 | Lighthouse (rare) |
| Loved Isle | likes received | 10 / 100 / 1,000 | Beloved Isle (rare) |
| Weekly Fire | weekly competition wins | 1 / 3 / 6 | Weekly Blaze (epic) |
| Team Flame | members active the same day (≥ 30 min each) | 2 / 3 / 4 / 5 | Kindred (rare) |
| Night Shift (secret) | fuel hours burned | 1,000 | Never Dark (epic) |

Island boosts (whole island, one pool of island points):

| Boost | Step per island point | Cap |
|---|---|---|
| Island production (generator, crop growth, mob loot, spawner rate: one factor) | +0.25% | **+8%** |
| Island crystals (island missions + island sources) | +0.25% | **+6%** |
| Island XP (pillars) | +0.2% | **+5%** |
| Fuel efficiency (less fuel per offline hour) | +0.5% | **+10%** |

- **Balance:** island production is ~35–42% of income (§7.3). +8% production ≈ **+3% total income** for an island with all island tiers. It belongs in the next model run (AC5).
- **Anti-abuse:** progress counts only from members (not coop). A member must be on the island ≥ 3 days to get the title. Members who leave take nothing. Alts on the same IP count once for "Team Flame". Max 3 accounts per IP rule (owner 292) applies to likes and visits.
- **Fair for solo islands:** goals are the same for solo and team islands, except "Team Flame" (team only). Team islands get more done faster but only compete with team islands (DESIGN), and boosts are small.

## C5. Rewards per tier (owner 355 "OTHER": boosts, not only titles)
- Every tier: **boost points** (C2) + a toast.
- Last tier: **title** (personal: account; island: every member).
- No crystals, money or keys per tier (AC4): boosts are the reward, so achievements never print currency. Owner can switch on small crystal rewards in config later.

## C6. Season reset (owner 354)
- At the reset: all progress, points and boosts go back to zero (profiles and islands are wiped anyway).
- **Titles stay** (account). The `/achievements` menu shows "Earned in S1" on titles from earlier seasons.
- A **season summary** (chat + `/achievements` → History): tiers done, boosts reached, titles won. Saved per season (like island value history, V2).
- Gauges that "only go up" (as built) are reset by the season reset script, not by a skill reset mid-season.

## C7. Menu (Bedrock-safe)
- `/achievements` (approved) and a button in `/menu` and `/tags`.
- Top row: **Personal** · **Island** · **History** tabs.
- Personal tab: one item per category with "points x / boost y% of cap z%".
- Island tab: island points, the four island boosts, member contribution top 5.
- Click-only, no typing; lore ≤ 36 characters per line.

---

# D. MILESTONES

Docs and configs first; nothing changes for players until RK2.

| # | Milestone | Depends on | Lane |
|---|---|---|---|
| **RK1** | Owner answers §E. Update DESIGN §11, OPEN_QUESTIONS, art brief N01–N11 | owner | docs |
| **RK2** | Rename free rank to Ashborn (display, prefix, `/freerank` texts, free tags spark/smoke/kindling); move builder weight to 9; `/trash` admin/owner only; lockdown bypass for donor groups | RK1 | server/permissions, modules/perks, cosmetics |
| **RK3** | Donor + won LuckPerms groups with prefixes, colors, weights, inheritance; enable the commented grants (profiles, loadout, AH, island fly, perks, join message) with the new names; new AH nodes 9/11/12 | RK2 | server/permissions, auction |
| **RK4** | Rank bonuses: sell rank step + skill XP step through `BonusService` (one family cap each); reserved slots node; rank tag sync | RK3 | economy, skills, cosmetics, essentials |
| **RK5** | `/menu` → Rank rewards (weekly bound kit per profile, monthly keys after 5 h); `/pv` vaults per profile | RK3, crates (PLAN10), `/pv` approval | menus, new or perks module |
| **RK6** | Store wiring: Tebex packages, upgrade pricing, gift, chargeback removal, audit; won-rank grant/removal in the season script with admin approval | RK3, PLAN11/PLAN12 | store/season modules |
| **TT1** | Themed names for existing titles (display only), new titles + tags in `tags-titles.yml` (bundled defaults; the live file keeps owner edits) | RK1 | cosmetics |
| **TT2** | Season titles (`s<N>_…`) created by the season script; mods' short list of event titles (397) | TT1, PLAN12 | cosmetics, moderation perms |
| **TT3** | Name color picker, border colors, trails per rank (when trails exist) | RK3 | cosmetics, islands |
| **AC1** | `/achievements` command + menu tabs (approved 356); categories get boost lines | — | achievements |
| **AC2** | Personal boost points → `BonusService` families with caps; AH slot boost | AC1 | achievements, common/bonus, auction |
| **AC3** | Island achievements: island progress store (per island), island metrics, island boosts, member titles (≥ 3 days) | AC1, island level (PLAN5) | achievements, islands |
| **AC4** | Season summary + history; reset hook in the season script | AC2, AC3, PLAN12 | achievements |
| **AC5** | Balance: add rank skill XP and achievement boosts to `tools/economy_model.py`; check RK-CAP and island +3% | RK4, AC2, AC3 | docs, tools |

---

# E. QUESTIONS

Every decision as yes/no. "Suggested" is what this plan does if the owner doesn't answer. `p: 1` = important for launch.

| Id | Question | Suggested |
|---|---|---|
| RK1 | Check Mojang's usage guidelines before the store opens, and turn progress perks to 0 if needed? | Yes |
| RK2 | Use the Phoenix life names (Hatchling → Immortal)? | Yes |
| RK3 | Rename the free rank to Ashborn? | Yes |
| RK4 | Donor prefixes in brackets, staff in bold caps? | Yes |
| RK5 | Move Builder weight above donors (9)? | Yes |
| RK6 | Bought ranks account-wide on every profile, forever? | Yes |
| RK7 | Use the price ladder €5 → €100? | Yes |
| RK8 | Won ranks copy a donor tier's perks (incl. sell/XP)? | Yes |
| RK9 | Won ranks need 10 h played and 7 days on the island? | Yes |
| RK10 | Admin approves the winners list before won ranks are given? | Yes |
| RK11 | Won rank shown by default over a bought rank? | Yes |
| RK12 | Three won ranks (Torchbearer, Flameheart, Firecrown)? | Yes |
| RK13 | Upgrade price uses the list price of your current rank? | Yes |
| RK14 | Max rank sell bonus +15%? | Yes |
| RK15 | Remove the rank on chargeback and flag the account? | Yes |
| RK16 | Max rank skill XP bonus +10%? | Yes |
| RK17 | Monthly low keys (Ember/Cinder/Blaze) claimable after 5 h played? | Yes |
| RK18 | Island fly from Flamecrest (tier 4)? | Yes |
| RK19 | Weekly bound kits per profile? | Yes |
| RK20 | No island/pillar XP boost from ranks? | Yes |
| RK21 | Profile slot 2 at Firewing, slot 3 at Pyrelord? | Yes |
| RK22 | AH slots 6 (free) up to 15 (Immortal)? | Yes |
| RK23 | `/pv` vaults 1–4 by rank, per profile? | Yes |
| RK24 | Join when full from Firewing? | Yes |
| RK25 | Ashborn gets +1 AH slot? | Yes |
| RK26 | Immortal does not include the premium battle pass? | Yes |
| RK27 | Claims in `/menu` → Rank rewards, no `/kit` command? | Yes |
| TT1 | Titles before the name, tags after (as built)? | Yes |
| TT2 | Give existing achievement titles themed names (ids kept)? | Yes |
| TT3 | Season winner titles with the season number? | Yes |
| TT4 | Free tags become Spark, Smoke, Kindling? | Yes |
| TT5 | "First Flame" for 1 h played in Season 1's first week? | Yes |
| TT6 | Eclipse crate gives one new tag a month that never returns? | Yes |
| TT7 | Every bought rank gets its own tag, kept forever? | Yes |
| TT8 | Holiday tags only in their month? | Yes |
| TT9 | Max 16 characters per title or tag? | Yes |
| AC1 | Personal and island achievements in one `/achievements` menu with tabs? | Yes |
| AC2 | Boost points per tier: 1/2/3/5/8? | Yes |
| AC3 | Personal achievements never boost sell or money? | Yes |
| AC4 | No crystals, money or keys per tier, boosts only? | Yes |
| AC5 | Personal boost caps (skill XP 10%, tool XP 10%, PvE 8%, rare 8%, crystals 6%, AH +2)? | Yes |
| AC6 | Island boost caps (production 8%, crystals 6%, island XP 5%, fuel 10%)? | Yes |
| AC7 | Island titles only for members of ≥ 3 days? | Yes |
| AC8 | Everything resets each season, titles stay? | Yes |
| AC9 | Season summary saved per season (History tab)? | Yes |
| AC10 | Same island goals for solo and team (except Team Flame)? | Yes |

```json
[
  {"q": "RK1: Check Mojang's usage guidelines before the store opens, and set rank progress perks to 0 if they forbid them?", "s": "Yes", "why": "Paid gameplay perks and paid keys can break the guidelines; looks and convenience stay either way.", "p": 1},
  {"q": "RK2: Name the 7 donor ranks Hatchling, Fledgling, Firewing, Flamecrest, Sunfeather, Pyrelord, Immortal?", "s": "Yes", "why": "One phoenix story, fits the server, no clash with crate names.", "p": 1},
  {"q": "RK3: Rename the free rank from Ember to Ashborn?", "s": "Yes", "why": "Every player rises from the ash; Ember stays a crate name only.", "p": 1},
  {"q": "RK4: Donor prefixes in normal [Brackets], staff prefixes in BOLD CAPS?", "s": "Yes", "why": "A donor can never look like staff.", "p": 0},
  {"q": "RK5: Move the Builder weight to 9, above all donor ranks?", "s": "Yes", "why": "Staff prefixes must always win over donor prefixes.", "p": 0},
  {"q": "RK6: Bought ranks are account-wide, work on every profile and never expire?", "s": "Yes", "why": "Matches your answer 387 and the profile rules.", "p": 1},
  {"q": "RK7: Prices 5, 10, 20, 30, 45, 65, 100 EUR?", "s": "Yes", "why": "Small first step, clear jumps, a real top rank; set in Tebex, easy to change.", "p": 1},
  {"q": "RK8: Won ranks give all perks of the donor tier they copy, sell and XP bonus included?", "s": "Yes", "why": "A prize should feel like a rank; the edge is small and lasts one season.", "p": 1},
  {"q": "RK9: To get a won rank: 10 h played that season and 7 days on the winning island?", "s": "Yes", "why": "Stops carries by joining a top island at the last minute.", "p": 1},
  {"q": "RK10: An admin approves the winners list before won ranks are given?", "s": "Yes", "why": "One check stops a wrong grant from a bug or exploit.", "p": 0},
  {"q": "RK11: Show the won rank by default instead of the bought one (player can switch)?", "s": "Yes", "why": "A won rank is rarer and lasts only one season.", "p": 0},
  {"q": "RK12: Three won ranks: Torchbearer (top 4-10), Flameheart (top 2-3 and board #1s), Firecrown (#1 islands)?", "s": "Yes", "why": "Enough winners to motivate, few enough to stay special.", "p": 1},
  {"q": "RK13: An upgrade costs the new price minus the list price of your current rank?", "s": "Yes", "why": "Simple and fair, even if the old rank was bought on sale.", "p": 0},
  {"q": "RK14: Top rank sell bonus +15% (steps 2, 4, 6, 8, 10, 12, 15)?", "s": "Yes", "why": "Same value the balance sheet already uses, inside the +300% cap.", "p": 1},
  {"q": "RK15: On a chargeback, remove the rank and flag the account for staff?", "s": "Yes", "why": "Standard store protection; staff see it at once.", "p": 0},
  {"q": "RK16: Top rank skill XP bonus +10% (steps 1, 2, 3, 4, 6, 8, 10)?", "s": "Yes", "why": "About half a skill level ahead; noticeable, not decisive.", "p": 1},
  {"q": "RK17: Monthly low keys (Ember, Cinder, Blaze only) for ranks, claimable after 5 h played that month?", "s": "Yes", "why": "Keys stay earned by playing; the top rank gets about 2 days of AFK-pool keys.", "p": 1},
  {"q": "RK18: Island fly from the 4th rank (Flamecrest) up?", "s": "Yes", "why": "Fly is a strong perk for the middle of the ladder; the Wings mastery gives it to everyone else.", "p": 1},
  {"q": "RK19: Weekly rank kits per profile with bound items (food, blocks, island fuel)?", "s": "Yes", "why": "Bound items can never be sold, so no money enters the economy.", "p": 0},
  {"q": "RK20: Ranks give no island or pillar XP boost?", "s": "Yes", "why": "One donor should not push a whole island ahead of others.", "p": 1},
  {"q": "RK21: Free profile slot 2 at Firewing and slot 3 at Pyrelord (gems still unlock them too)?", "s": "Yes", "why": "Same places as the old Knight and Warlord on the longer ladder.", "p": 0},
  {"q": "RK22: AH listing slots 6 for Ashborn up to 15 for Immortal?", "s": "Yes", "why": "More listings means more fees and tax, so it helps the money sinks.", "p": 0},
  {"q": "RK23: /pv vaults 1 to 4 by rank, contents per profile?", "s": "Yes", "why": "Classic storage perk; per profile keeps profiles separate.", "p": 0},
  {"q": "RK24: Ranks from Firewing up can join when the server is full?", "s": "Yes", "why": "Common donor perk that costs nothing in game.", "p": 0},
  {"q": "RK25: Ashborn (free rank) gets +1 AH slot as its small perk?", "s": "Yes", "why": "A small, useful perk for everyone who claims the free rank.", "p": 0},
  {"q": "RK26: Immortal does not include the premium battle pass?", "s": "Yes", "why": "Keeps rank power and pass progress apart, so the cap holds.", "p": 0},
  {"q": "RK27: Rank kits and keys are claimed in /menu -> Rank rewards, with no new /kit command?", "s": "Yes", "why": "One click, works on Bedrock, no new command to learn.", "p": 0},
  {"q": "TT1: Keep titles before the rank and tags after the name?", "s": "Yes", "why": "Already built and easy to read.", "p": 0},
  {"q": "TT2: Give the existing achievement titles themed names like Magma Delver and Gold Phoenix (ids stay)?", "s": "Yes", "why": "Makes them fit the phoenix theme without losing anyone's titles.", "p": 1},
  {"q": "TT3: Season winner titles carry the season number, like Firecrown S1?", "s": "Yes", "why": "The rank ends, the honor stays and stays unique.", "p": 0},
  {"q": "TT4: Change the free tags to Spark, Smoke and Kindling (Ember stays a crate tag)?", "s": "Yes", "why": "Keeps the crate tag special.", "p": 0},
  {"q": "TT5: The First Flame title for anyone with 1 h played in Season 1's first week?", "s": "Yes", "why": "Rewards launch players; 1 h stops join-and-leave alts.", "p": 1},
  {"q": "TT6: The Eclipse crate gives one new tag every month that never comes back?", "s": "Yes", "why": "Gives the monthly crate a reason to open every month.", "p": 0},
  {"q": "TT7: Every bought rank also gets its own rank tag, kept forever?", "s": "Yes", "why": "A lasting look for supporters that costs nothing in game.", "p": 0},
  {"q": "TT8: Holiday tags can only be won in their month?", "s": "Yes", "why": "Makes them rare and worth showing.", "p": 0},
  {"q": "TT9: Max 16 visible characters per title or tag?", "s": "Yes", "why": "Fits Bedrock chat and tab.", "p": 0},
  {"q": "AC1: Personal and island achievements in one /achievements menu with Personal, Island and History tabs?", "s": "Yes", "why": "One place to look, works on Bedrock.", "p": 1},
  {"q": "AC2: Boost points per tier 1, 2, 3, 5, 8?", "s": "Yes", "why": "Fast start, bigger rewards for the long tiers.", "p": 0},
  {"q": "AC3: Personal achievements never boost sell or money directly?", "s": "Yes", "why": "Keeps the sell cap math of the balance sheet unchanged.", "p": 1},
  {"q": "AC4: Achievement tiers give boosts and titles only, no crystals, money or keys?", "s": "Yes", "why": "Achievements should never print currency.", "p": 1},
  {"q": "AC5: Personal boost caps: skill XP 10%, tool XP 10%, PvE damage 8%, rare finds 8%, crystals 6%, AH +2 slots?", "s": "Yes", "why": "Worth chasing, far below every family cap.", "p": 1},
  {"q": "AC6: Island boost caps: production 8%, island crystals 6%, island XP 5%, fuel 10%?", "s": "Yes", "why": "About +3% income for a fully done island; fair and small.", "p": 1},
  {"q": "AC7: Island achievement titles only for members who were on the island 3+ days?", "s": "Yes", "why": "Stops joining an island just to collect its titles.", "p": 0},
  {"q": "AC8: All achievement progress and boosts reset each season, titles stay?", "s": "Yes", "why": "Matches your answer 354 and the season wipe.", "p": 1},
  {"q": "AC9: Save a season summary of achievements in a History tab?", "s": "Yes", "why": "Players see what they did; cheap to store.", "p": 0},
  {"q": "AC10: Same island achievement goals for solo and team islands, except Team Flame?", "s": "Yes", "why": "Simple; boosts are small and teams only compete with teams.", "p": 0}
]
```
