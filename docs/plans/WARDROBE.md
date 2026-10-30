# Wardrobe and Loadout Plan (PLAN-SETS)

> **Status:** plan, 2026-10-30. The owner approved building this with the suggested defaults (overnight mandate, `STATUS.md`).
> **Every rule and number in this file is a default** the owner can change later. All numbers go into typed configs (`wardrobe.yml`, `sets.yml`), all text into `messages/wardrobe.yml`, never into code. Defaults that matter most have an id (`WD1`… for the wardrobe, `LD1`… for loadouts) so the owner can answer "WD7: no" (register in §14).
> Sources: `DECISIONS_2026-10-30.md` (round 3 **SE1**, follow-ups "`/wardrobe` replaces `/sets`", loadout **L1–L4**, profiles **P2/P4/P8**, **PR3–PR5**, crates, **WR1**, **#46**, **#61**), `plans/ENCHANTS.md` (§2.1 armor slots EN12, §8.1 bonus families EN16, §8.3 PvP EN17, §9 unique items), `ECONOMY_BALANCE.md` (§1.2 REF, §2, §3, §9), `DESIGN.md` §6.2 + §11, `REVIEW.md` A3/A8/B7, `UI_STYLE.md` §1.4/§2/§4/§5, `PHASE-4-DEPTHS.md` M4.2.

---

## 0. Summary

- **`/wardrobe` replaces `/sets`.** It shows **every set** in the game (16 sets), owned or not, with its pieces, bonuses, set level and where to get it. It also **stores everything you own virtually**: your sets, plus the custom tools, enchant books and tokens that loadouts swap out (owner L3).
- **Sets come from three places** (SE1), priced by strength: **money** (tier I + two specials), **crystals** (tier III gathering/combat), **gems** (three sets, each also in a crate so no power is gem-only), **crates** (the three tier IV sets, keys are earned only). Black Market shards rotate crate sets.
- **Set level 0–50** (SE1): level 0 = **0×** bonus, level 25 = 1×, level 50 = **2×** (linear, +0.04× per level). Set XP comes from **the activity the set boosts** while you wear at least 2 pieces. Fast start: level 1 in ~5 minutes, level 25 (1×) in ~6 played hours, level 50 in ~65 hours of that activity for an active player (#61).
- **Bonuses at 2 and 4 pieces.** Wear 2 + 2 pieces of two sets for two 2-piece bonuses, or 4 of one set for its 2-piece + 4-piece bonus. Every bonus belongs to a **bonus family** with one cap (sell +300%, ENCHANTS §8.1).
- **Merging** (SE1): two **level-50** sets of different families become **one set with both sets' bonuses**, at **level 0**, for a crystal fee. One merge per set (max 2 sets in one), tier IV sets can't merge.
- **Storage model (recommended): virtual wardrobe entries + locked real armor.** The database row is the set; equipping puts **real armor items** on the player (visible on Java and Bedrock, vanilla armor points), but they are locked to the armor slots and are only a "view" of the row. A copied piece is worthless and deleted on sight. Nothing valuable ever exists only as an item (§4).
- **`/loadout`**: presets that hold **everything** (L1): the 4 armor slots (wardrobe pieces), the pet (hook until pets exist) and the hotbar's custom tools. **2 free presets**, more from ranks or store credits (L2). Swapped-out pieces and tools go **into the wardrobe** (L3). Swap anywhere **except in combat or the warzone**, **2 s cooldown** (L4). Tools move between inventory and wardrobe storage with the auction house's escrow pattern (remove → store in one transaction → give), so a swap can never duplicate a tool.
- **Per profile** (P2): owned sets, set levels, storage and loadouts belong to the profile. Account-wide: gem purchases (as a per-season claim on every profile) and bought loadout slots.
- **Milestones:** W1 wardrobe storage + set registry + `/wardrobe` GUI → W2 set levels, multipliers, merging → W3 `/loadout` → W4 balance. E5 (armor enchants) follows W2.

---

## 1. Design goals

| Goal | What it means here |
|---|---|
| **Synergy** (DESIGN rule 1) | Sets feed the same bonus families as enchants, masteries, ranks and boosters (one cap per family). Set XP comes from Depths/island activity, so wearing the right set is part of every session. Loadouts make switching between a "mining kit" and a "spawner kit" one click, which makes owning several sets and tools worth it |
| **Fast start** (#61) | Five tier I sets cost $20k (first hour). Level 1 after ~200 actions (~5 min), level 10 in ~1 h |
| **Progressively harder, with ups and downs** (#61) | XP per level ×1.10, so 25 → 50 takes ~10× as long as 0 → 25. Ups: milestone levels (10, 25, 50) unlock looks and enchant slots; a new tier III set at a new Depths tier. Downs that motivate: merging resets the level to 0 for a permanently better set |
| **Less is more** (DESIGN rule 2) | 16 sets, two bonus lines each (2 pieces, 4 pieces), whole sets only (no piece hunting, SE1 easter-egg idea was declined) |
| **No pay-to-win** (REVIEW A8) | Every set starts at **0×**: power is earned by playing in it. Gem sets are tier II–III (never the top), have the same tier gate as crystal sets and also drop from crates. Tier IV sets are earned only. Gems never buy set XP, levels or merges. Warzone uses normalized values (§3.6) |
| **Dupe safe** | Sets are database rows, not items. Tools in storage use the AH escrow pattern. Every move is one transaction + audit row (§4, §8.4) |
| **Bedrock first** | Click-only menus, no dragging into slots, names ≤ 16 characters, lore lines ≤ 36, vanilla armor + leather dye + armor trims for looks (all shown by Geyser) (§10) |

---

## 2. Sets

### 2.1 Tiers and strength (WD1)
| Tier | Rarity color (UI_STYLE §1.4) | Strength factor | Sources | Gate to buy/redeem | Armor points (attribute, not material) |
|---|---|---|---|---|---|
| I | Uncommon (green) | 1.0 | Money | none | iron-like (15 armor) |
| II | Rare (blue) | 1.5 | Money, gems, crystals | Depths tier 5 in any wing, or any skill 25 | diamond-like (20 armor, 8 toughness) |
| III | Epic (purple) | 2.0 | Crystals, money, gems (+ crate) | Depths tier 6 in that set's wing (tier 8 any wing for the sell set) | diamond-like |
| IV | Legendary (gold) | 2.5 per family, but 3+ families | Crates (earned keys), Black Market | Depths tier 7 in any wing | netherite-like (20 armor, 12 toughness, 10% knockback resist) |

"Strength factor" scales a set's base bonus values (§2.3); "priced by strength" (SE1) = the price band in §2.4 follows the tier. Armor points are set with attribute modifiers, so a leather-look set can be a tier III set. The Mythic rarity stays for enchants.

### 2.2 The 16 sets (WD2)
Names are ≤ 16 characters (Bedrock, §10) and never repeat an enchant, mastery, crate or donor-rank name (Squire, Knight, Paladin, Warlord, Monarch). Pieces are named `<short name> Helm / Cuirass / Greaves / Boots` (e.g. `Deepforge Cuirass`).

| # | Id | Set | Short name | Activity | Tier | Source | Look (vanilla base · dye · trim pattern / material) |
|---|---|---|---|---|---|---|---|
| 1 | `delver` | Delver's Garb | Delver | Mining | I | Money | Chainmail · — · `wayfinder` / copper |
| 2 | `woodsman` | Woodsman's Hide | Woodsman | Foraging | I | Money | Leather · moss green `#5E7D32` · `wild` / emerald |
| 3 | `tiller` | Tiller's Smock | Tiller | Farming | I | Money | Leather · wheat `#D9B45A` · `shaper` / gold |
| 4 | `footman` | Footman's Mail | Footman | Combat | I | Money | Iron · — · `sentry` / redstone |
| 5 | `netcaster` | Netcaster's Garb | Netcaster | Fishing | I | Money | Leather · sea blue `#3F76A8` · `coast` / lapis |
| 6 | `banneret` | Banneret Tabard | Banneret | Conquest | II | Money | Iron · — · `raiser` / redstone |
| 7 | `lorekeeper` | Lorekeeper Robes | Lorekeeper | All skills (XP) | II | Gems (+ Blaze crate) | Leather · parchment `#E8DCC0` · `host` / amethyst |
| 8 | `deepforge` | Deepforge Plate | Deepforge | Mining | III | Crystals | Netherite look · — · `rib` / gold |
| 9 | `thornward` | Thornward Mantle | Thornward | Foraging | III | Gems (+ Inferno crate) | Leather · dark oak `#4A3221` · `ward` / emerald |
| 10 | `cropwarden` | Cropwarden Robes | Cropwarden | Farming | III | Crystals | Leather · harvest gold `#E3A42B` · `flow` / copper |
| 11 | `dreadguard` | Dreadguard Mail | Dreadguard | Combat | III | Crystals | Diamond look · — · `snout` / netherite |
| 12 | `stormkeel` | Stormkeel Scale | Stormkeel | Fishing | III | Gems (+ Inferno crate) | Chainmail · — · `tide` / diamond |
| 13 | `coinmaster` | Coinmaster Silks | Coinmaster | Selling (all) | III | Money | Leather · royal purple `#5B2A86` · `eye` / gold |
| 14 | `voidforged` | Voidforged Plate | Voidforged | Gathering (mine, forest, farm) | IV | Phoenix crate | Netherite · — · `silence` / amethyst |
| 15 | `conqueror` | Conqueror Aegis | Conqueror | Conquest | IV | Conqueror crate (KOTH) | Netherite · — · `bolt` / redstone |
| 16 | `phoenix` | Phoenix Regalia | Phoenix | All activities | IV | Eclipse crate (monthly), Black Market | Gold · — · `spire` / redstone (phoenix red + flame gold) |

**Ladder per activity:** Mining I → III → IV (Voidforged/Phoenix) · Foraging I → III → IV · Farming I → III → IV · Combat I → III → IV (Phoenix) · Fishing I → III → IV (Phoenix) · Conquest II → IV. Coinmaster (sell) and Lorekeeper (skill XP) fit every playstyle and are the natural merge partners (§5).

**Conquest sets** show as locked "Arrives with the Warzone" until the conquest milestones exist (`sets.yml → <id>.enabled-from: conquest`), so nobody buys a set without a use.

### 2.3 Bonuses (WD3)
Values are at **1× (set level 25)** → **2× (set level 50)**. At level L the value is `base × L / 25` (§3.2). Bonuses only count where the activity happens (a mining bonus works in The Depths mine and on island generators). Bonus family in brackets (ENCHANTS §8.1; new families in §2.5).

| Set | 2 pieces (1× → 2×) | 4 pieces (adds, 1× → 2×) |
|---|---|---|
| Delver's Garb | Mining speed +4% → +8% [speed] | Crystals from mining +8% → +16% [crystals] |
| Woodsman's Hide | Chopping speed +4% → +8% [speed] | Extra logs +6% → +12% [extra drops] |
| Tiller's Smock | Farming skill XP +8% → +16% [skill XP] | Extra crops +6% → +12% [extra drops] |
| Footman's Mail | Damage from mobs −4% → −8% [damage reduction] | Damage to mobs +8% → +16% [mob damage] |
| Netcaster's Garb | Wait for a bite −5% → −10% [speed] | Rare-or-better fish +10% → +20% [rare finds] |
| Banneret Tabard | Envoy loot luck +10% → +20% [conquest] | KOTH capture speed +8% → +16% and island Conquest XP +10% → +20% [conquest] |
| Lorekeeper Robes | All skill XP +6% → +12% [skill XP] | Tool XP +10% → +20% [tool XP] |
| Deepforge Plate | Extra ore drops +10% → +20% [extra drops] | Crystals from mining +16% → +32% [crystals] |
| Thornward Mantle | Chopping speed +8% → +16% [speed] | Extra logs +12% → +24% [extra drops] |
| Cropwarden Robes | Extra crops +12% → +24% [extra drops] | Crops within 16 blocks of you grow +15% → +30% faster [crop growth] |
| Dreadguard Mail | Damage from mobs −8% → −16% [damage reduction] | Damage to mobs and bosses +16% → +32% [mob damage] |
| Stormkeel Scale | Wait for a bite −10% → −20% [speed] | Double catch chance +10% → +20% [extra drops] |
| Coinmaster Silks | Sell price +10% → +20% [sell] | Sell price +15% → +30% more (total **+50%** at level 50) [sell] |
| Voidforged Plate | Mining, chopping and harvest speed +6% → +12% [speed] | Extra drops +10% → +20% and crystals +10% → +20% in mining, foraging and farming |
| Conqueror Aegis | Envoy loot luck +15% → +30%, KOTH capture speed +12% → +24% [conquest] | Money lost on a warzone death −25% → −50% (WR1: 2% → 1%) and island Conquest XP +15% → +30% [conquest] |
| Phoenix Regalia | All skill XP +5% → +10% [skill XP] | Sell price, crystals and extra drops +6% → +12% everywhere |

How they compare (why the numbers): a tier III set's family value is ~2× the tier I value of the same family (strength factor), tier IV sets cover 3+ families at ~0.6× of tier III each, and the sell set at its max (+50%) is about 1/6 of the +300% cap (#46: only "the best of everything" reaches the cap). Combat sets give no PvP damage (§3.6).

### 2.4 Prices and sources (WD4)
Money prices are "hours of reference income" at the gate tier (`ECONOMY_BALANCE.md` §1.2, `REF(t)`), so they follow the economy if it is rebalanced. Crystal prices are set against an active player's crystal income (§9.1: ~800/h in week 1, ~2,500/h in month 1, ~1.1M per season).

| Set | Price | ≈ | Gate | Crate / other sources (entries for the crates plan) |
|---|---|---|---|---|
| Tier I (5 sets) | **$20,000** each | 0.4 h of REF(1); an active player earns ~$47k in the first hour | none | — |
| Banneret Tabard | **$2,500,000** | ~5 h of REF(5) | Depths tier 5 any wing | Conqueror crate 10% |
| Coinmaster Silks | **$40,000,000** | ~16 h of REF(8); active player ~week 2–3 | Depths tier 8 any wing | Black Market rotation |
| Deepforge Plate · Cropwarden Robes · Dreadguard Mail | **◆ 25,000** each | ~week 2–3 for an active player spending half their crystals here (the rest on enchants) | Tier 6 in that wing (mine / crop tier / combat) | Inferno crate 1% each |
| Lorekeeper Robes | **✦ 400** | ~4 weeks of free gems (votes + streak ≈ 115/week) | Depths tier 5 any wing, or any skill 25 | Blaze crate 1% |
| Thornward Mantle · Stormkeel Scale | **✦ 900** each | ~8 weeks of free gems | Forest tier 6 / Fishing level 40 | Inferno crate 1.5% each (the free path) |
| Voidforged Plate | crate only | — | Depths tier 7 any wing (to redeem) | Phoenix crate 2%; Black Market rotation |
| Conqueror Aegis | crate only | — | Depths tier 7 any wing | Conqueror crate 4% |
| Phoenix Regalia | crate only | — | Depths tier 7 any wing | Eclipse crate 2% in its featured month; Black Market 1 per player per season (◆ none, shards only, price in the Black Market plan) |

- **Buying** happens in `/wardrobe` (set detail → Buy → 3-row confirm; gem purchases always confirm, UI_STYLE §5.2). The set goes straight into the wardrobe, at level 0.
- **Crate wins** come as a **Set Token** item (`<rarity>Token: Deepforge Plate`, footer `EPIC SET PIECE`): a unique, database-backed item like enchant books (ENCHANTS §9), **tradeable and listable on the AH** until redeemed (right-click or Wardrobe → Redeem). That keeps a market for crate sets (like tag items). Redeeming needs the gate and a profile that doesn't own that set yet.
- **Gem purchases are account-wide claims** (WD5): gems are account-wide (P2), so buying a gem set buys a **Sigil** for the account. Each profile can **claim** that set once per season for free (it appears in the wardrobe at level 0). Sigils survive the season reset like gem-bought profile slots (P8). A claimed copy is a normal profile set (it can be merged; a merged claim can't be claimed again that season on that profile).
- **One copy per set per profile** (WD6). A merged set is a separate entry, so after merging Deepforge you may buy or redeem Deepforge again.
- **Not tradeable once in the wardrobe** (WD7): sets bought with money, crystals or gems, claimed or merged sets are bound to the profile (no `/trade`, no AH). Only unredeemed Set Tokens trade. Reason: gem sets must never become money, and bound sets can't hop between own profiles (P4).
- **No refunds and no selling back** (WD8). Staff can remove a set (audited).

### 2.5 Bonus families: new entries for `BonusService` (WD9)
ENCHANTS §8.1 defines the families and caps. Sets use those, plus two new families:

| Family | Sources | Cap |
|---|---|---|
| **Crop growth** (new) | Cropwarden Robes, Crop Growth production upgrade (if it reports as a bonus), events | +100% |
| **Conquest** (new) | Banneret Tabard, Conqueror Aegis, future conquest perks | +60% per stat (envoy luck, capture speed, Conquest XP); warzone money-loss reduction max −50% |

All other set bonuses go into the existing families: speed (hard cap 6 blocks/s), crystals +200%, extra drops +200%, sell **+300%**, skill XP +300%, tool XP +100%, rare finds +150%, mob damage +200%, damage reduction 60%. The `/wardrobe` stat screen shows a family in gold "capped" when it is full, like the Enchanter.

---

## 3. Set levels

### 3.1 Set XP (WD10)
A set earns XP while you **wear at least 2 of its pieces**: 2–3 pieces earn **50%**, 4 pieces **100%** (WD11). XP comes only from the activity the set boosts, so every set is levelled by playing the way it is meant to be played.

| Action | Set XP | Earns for |
|---|---|---|
| Depths ore block (own block, M4.3) | 1 | Mining sets, Voidforged, Lorekeeper (75%), Phoenix |
| Island generator block | 0.25 | same as above |
| Depths log | 1 | Foraging sets, Voidforged, Lorekeeper (75%), Phoenix |
| Island tree log | 0.25 | same |
| Mature crop harvested (any hoe, also each crop of a harvester area) | 0.5 | Farming sets, Voidforged, Lorekeeper (75%), Phoenix |
| Depths combat kill (shared mobs) | 8 | Combat sets, Lorekeeper (75%), Phoenix |
| Mini-boss / world boss with ≥ 5% damage | 300 / 1,500 | Combat sets, Phoenix |
| Fishing catch (Phase 7) | 25 | Fishing sets, Lorekeeper (75%), Phoenix |
| Item sold (`/sell`, satchel, sell wand; island items 0.5) | 1 | Coinmaster (max 8,000 set XP per hour) |
| KOTH: second on the capture point / capture | 2 / 500 | Conquest sets, Phoenix |
| Envoy crate claimed | 40 | Conquest sets, Phoenix |
| Warzone player kill (same victim max once per 10 min, victim ≥ 30 min played) | 50 | Conquest sets |

- Values are per action and balanced for **~3,000–4,000 set XP per played hour in any activity** at an active player's mid-season pace, the same parity rule as income and books (players pick the wing they enjoy). Kills are worth 8 because an active player makes ~400 kills vs ~3,500 blocks per hour.
- **Stacked island mobs give no set XP** (WD12): a Slaughter X sword kills ~77 mobs per second, which would level a set in minutes. Island spawner farming has its own rewards (enchant books, drops).
- Area enchants (Vein Rend, Treefeller …) count their extra blocks at their yield (50% / 35% / 25%), like enchant book drops (ENCHANTS §3.2).
- **Wardrobe Tome** (WD13): a unique consumable from crates (Ember+), quests and events, never from gems. Use it in the wardrobe on a set: +2,500 set XP (Inferno/Phoenix crate version +10,000). It can't raise a set past level 40, so the last ten levels are always played.

### 3.2 Level curve and multiplier (WD14)
- **XP for level L → L+1 = 200 × 1.10^L** (level 1 costs 200, level 50 costs 21,344). Total to level 50: **232,782** set XP. Max level 50. (`wardrobe.yml → levels.base-xp: 200`, `levels.growth: 1.10`, `levels.max: 50`.)
- **Multiplier = 2 × level / 50** (`levels.max-multiplier: 2.0`): 0× at level 0, 1× at 25, 2× at 50. Linear on purpose: easy to read ("Lv 30 = 1.2×") while the XP curve makes each step harder.
- **Merged sets** need **1.5× the XP** per level (WD15): 349,173 to level 50.

| Set level | Multiplier | XP for this level | Total XP | Hours of that activity at 2,000 XP/h (casual / early) | 3,500 XP/h (active, weeks 1–2) | 5,500 XP/h (active, months 1–2) | 7,500 XP/h (top, late) | Merged set at 3,500 XP/h |
|---|---|---|---|---|---|---|---|---|
| 1 | 0.04× | 200 | 200 | 6 min | 3 min | 2 min | 2 min | 5 min |
| 5 | 0.20× | 293 | 1,221 | 0.6 h | 0.3 h | 0.2 h | 0.2 h | 0.5 h |
| 10 | 0.40× | 472 | 3,187 | 1.6 h | 0.9 h | 0.6 h | 0.4 h | 1.4 h |
| 15 | 0.60× | 759 | 6,354 | 3.2 h | 1.8 h | 1.2 h | 0.8 h | 2.7 h |
| 20 | 0.80× | 1,223 | 11,455 | 5.7 h | 3.3 h | 2.1 h | 1.5 h | 4.9 h |
| 25 | 1.00× | 1,970 | 19,669 | 9.8 h | 5.6 h | 3.6 h | 2.6 h | 8.4 h |
| 30 | 1.20× | 3,173 | 32,899 | 16 h | 9.4 h | 6.0 h | 4.4 h | 14 h |
| 35 | 1.40× | 5,110 | 54,205 | 27 h | 15 h | 9.9 h | 7.2 h | 23 h |
| 40 | 1.60× | 8,229 | 88,519 | 44 h | 25 h | 16 h | 12 h | 38 h |
| 45 | 1.80× | 13,253 | 143,781 | 72 h | 41 h | 26 h | 19 h | 62 h |
| 50 | 2.00× | 21,344 | 232,782 | 116 h | 67 h | 42 h | 31 h | 100 h |

Read ("hours" = played hours in that activity, the XP rates already include walking and menus like the balance sheet's blocks per played hour): an active player (4 h/day) who sticks to one set has it at 1× on day 2, at 1.6× in week 1–2 and at level 50 around week 2–3, so a first merge lands around week 4–6 (two sets); a casual player (1.5 h/day) reaches 1× in week 1 and level 50 around month 2; a top player maxes a set in about 5 days and can merge in week 2. Spreading play over several sets slows each one down, which is the intended choice. **W4 (TUNE) re-checked these** with the balance model's real block rates (`ECONOMY_BALANCE.md` §9.6): a mining set reaches 1× after 4–9 played hours and 2× after 50–110 h (casual 2,100, regular 3,400, grinder 4,700 set XP/h in week 1); Coinmaster maxes in ~29 h at the 8,000 XP/h cap. No level or XP value changed.

### 3.3 Milestones (the "ups", WD16)
| Level | Unlock |
|---|---|
| 1 | Bonuses start (0.04×) |
| 10 | Upgraded trim material on the look (e.g. copper → gold), level shown in the piece name color |
| 25 | **1×**; a 2nd enchant slot on every piece (EN12, used by E5) |
| 40 | Max level a Wardrobe Tome can reach |
| 50 | **2×**; the chestplate's **mythic enchant slot** (EN12); enchant glint on all pieces; personal particle trail toggle (off in the warzone); the set can be **merged** |

Level-ups: sound `level_up`, action bar "Deepforge Plate Lv 26 · 1.04×"; milestone levels also show a title on screen (UI_STYLE §6.5). No server-wide announcements.

### 3.4 How a bonus is computed (WD17)
1. Count worn pieces per set id. A merged entry counts for **both** of its sets.
2. A set with 2+ pieces gives its 2-piece bonus; with 4 pieces also its 4-piece bonus.
3. The multiplier is that of the **lowest-level entry** that contributes pieces to that set (wearing 2 merged pieces at level 3 with 2 pure Deepforge pieces at level 50 gives Deepforge 4-piece at 0.12×). Simple to read and impossible to game by mixing.
4. Values go into their families in `BonusService` (source `wardrobe`), summed with enchants, masteries, ranks and boosters, capped once per family.
5. Bonuses update on equip, unequip, level-up, loadout swap and profile switch; cached per player (no per-block lookups of the database).

### 3.5 Season and profiles
- Set levels belong to the **profile** and to the **season**. At the season reset the wardrobe (sets, levels, storage, loadouts) is wiped with everything seasonal (DESIGN §1); **Sigils** (gem purchases) and **bought loadout slots** are kept (P8).
- Leaderboards: none for set levels (default; collection/achievements can count "sets at level 50" later, F3).

### 3.6 Warzone and duels (PvP normalization, WD18, follows EN17)
- In the warzone and duels every set counts as **1×** (level-normalized) and only these work: armor points normalized to **diamond-like** for every set piece, damage reduction at 50% of its 1× value (players and mobs alike), and conquest bonuses. Mob damage, gathering, sell and XP bonuses do nothing there. Particle trails are off.
- Gem sets are not combat sets anyway (Lorekeeper, Thornward, Stormkeel), and the combat sets give no damage against players.

---

## 4. How sets are stored: virtual entries + locked real armor

### 4.1 The options (WD19)
| | A. Unique real items (like tools) | **B. Virtual entries + locked real items (recommended)** | C. Fully virtual (no items, packet armor) |
|---|---|---|---|
| What the player owns | The item stacks (4 per set) | A database row per set; worn pieces are a **view** of that row | A database row |
| Dupe risk | Real: every piece can be duped like a tool and must be caught by `DupeDetector` after the fact | **None for value**: a copied piece has no owner row/nonce, gives no stats and is deleted on sight | None |
| Loss risk (bugs, crashes, death, full inventory) | Pieces can be lost, dropped, stolen from chests | None: the row stays even if the item vanishes; the wardrobe re-creates it | None |
| Looks (Java + Bedrock) | Real armor | **Real armor**: trims, dye, enchant glint, all native on both clients | Packet-faked armor: other players' views desync, Bedrock/Geyser support is fragile |
| Vanilla armor points, toughness, knockback | Native | **Native** (attribute modifiers on the item) | Must be faked with player attributes |
| Lore / hover | Native | **Native** (shows set, level, bonuses, enchants) | None |
| Merging, levels, enchants | Stored on 4 items, all 4 must be found | **On one row** | On one row |
| Fits L3 ("swapped pieces go into the wardrobe") | Needs inventory/storage moves for armor too | **Free**: unequipping is just "stop showing" | Free |

**Recommendation: B.** It is as safe as C and looks and plays like A. The only extra work is the lock (§4.3), which the menu framework already does for GUIs (all click types blocked).

### 4.2 What a worn piece carries
PDC on the item: `wardrobe:entry` (entry UUID), `wardrobe:piece` (HEAD/CHEST/LEGS/FEET), `wardrobe:profile` (profile key), `wardrobe:nonce` (random per equip). The `wardrobe_equipped` row (profile, slot → entry, nonce) is the truth. A piece is **valid** only if it sits in its owner's matching armor slot and its nonce equals the row's nonce.

### 4.3 The lock (WD20)
- **Equip and unequip only through `/wardrobe` or `/loadout`** (buttons). Worn pieces can't be clicked, dragged, number-keyed, shift-clicked, swapped to the off-hand, dropped, put in a chest, an armor stand, an item frame or a dispenser: `InventoryClickEvent` / `InventoryDragEvent` / `PlayerDropItemEvent` / `PlayerSwapHandItemsEvent` / `PlayerArmorStandManipulateEvent` / `BlockDispenseArmorEvent` are cancelled for tagged items, with a rate-limited hint "Use /wardrobe to take this off." The inventory is re-sent one tick later (`updateInventory`) so Bedrock (Geyser) never shows a ghost item.
- **Death:** inventory is kept everywhere (B7, WR1); death drops are filtered anyway (a tagged item is never dropped).
- **Profile switch:** the wardrobe strips its pieces before the profiles module saves the old profile's armor and re-equips the new profile's pieces from the database after the load (listens to the PROF switch events), so saved inventories never contain pieces.
- **Reconcile** on join, after a profile switch, on every `DupeDetector` scan (a `DupeReport` handler) and when a menu opens: any tagged item that is not valid (wrong place, stale nonce, other player or profile, entry gone) is **deleted** and audited (`wardrobe_stale_piece`); staff are alerted only if it was found outside the owner's armor (possible exploit). A row that says "equipped" whose item is missing re-creates the item (or marks the slot empty if vanilla armor sits there now).
- **Vanilla armor** stays allowed (WORLD §6). Equipping a set piece over vanilla armor moves the vanilla piece into the inventory; if the inventory is full the equip is refused ("Make room for your Iron Helmet"). Never dropped on the ground.
- Creative mode (staff) can pick up items freely: staff in creative get the hint but the reconcile still deletes copies, so nothing leaks.

### 4.4 Wardrobe storage (unique items, L3, WD21)
Besides sets, the wardrobe has a **Storage** (shown as "Wardrobe Chest") for **unique, database-backed items only**: custom tools (M4.2), enchant books and Disenchant Scrolls (ENCHANTS §9), Set Tokens and Wardrobe Tomes. Plain items (blocks, food, vanilla gear) never go in: they can't be tracked one by one, so they can't be moved safely without a dupe or loss window.
- Capacity: **27** items (`storage.size`), +9 per `skyblock.wardrobe.storage.<n>` rank grant (rank perks decided with the store plan).
- Per profile. Items in storage are serialized (like AH listings) and are "out of the world": tools keep their live data on the item, the `tools` row gets location `WARDROBE`.
- Store/take in the Storage menu (pick an item from a button list of your unique items; take = button) or automatically by loadouts.
- Escrow (same rules as `AuctionEngine`): **store** = remove the item on the main thread → insert the serialized item in one transaction (`STORED`) → only "not stored" gives it back. **Take** = `STORED → RELEASING` (with a plan id) in one transaction → give on the main thread → `RELEASED`. Recovery on join/startup: a `RELEASING` row whose item UUID is not in the player's inventory is given again; a tool in the inventory whose row says `STORED` is a stale copy (crash before the player file was saved) and is removed (the stored copy wins). Item UUIDs are unique, so a double is always visible.

---

## 5. Merging (SE1, WD22)

### 5.1 Rules
| Rule | Default | Why |
|---|---|---|
| Inputs | Two **different** owned sets, both at **level 50** | Merging resets to level 0, so it must be a choice made after "finishing" a set; merging two fresh sets would cost nothing |
| Families | The two sets may **not share a bonus family** (Delver + Deepforge both boost crystals → refused; Deepforge + Coinmaster → allowed) | A merge must add something new, not stack one family past the design |
| Tiers | Tier I–III only; **tier IV sets can't merge** (they already cover 3+ families) | Keeps the top sets special and caps total power |
| Max merges | **1 per set**: a merged set can't be merged again (max 2 sets in one) | Simple, readable, bounded |
| Result | **One set with both sets' 2-piece and 4-piece bonuses**, at **level 0**, XP curve ×1.5 (§3.2) | Owner SE1 |
| XP sources | Both sets' activities at 100% (Deepforge · Coinmaster levels from mining **and** selling) | Fair for the doubled curve |
| Look | The player picks which set's look the merged pieces use | Cosmetic choice |
| Name | `Deepforge · Coinmaster` in menus; pieces keep the chosen look's short name + lore line `Merged with Coinmaster` | ≤ 36 characters per lore line |
| Enchants (E5) | Each piece keeps the enchants of the chosen look's set; the other set's enchants come back as **books** into wardrobe storage (or the AH collection bin when storage is full) at their level. Enchants above the merged set's level gate stay on but **dormant** (gray, no effect) until the level catches up (EN4 gate by set level) | Nothing is lost; no free power at level 0 |
| Equipped / loadouts | Both inputs are unequipped; loadouts that used them now point at the merged set | No broken presets |
| Undo | **No unmerge** | Merges are irreversible (confirm dialog) |

### 5.2 Fee (crystals, a sink; WD23)
Fee = **◆ 5,000 × (tier of set A + tier of set B − 1)**: I+I ◆ 5,000 · I+II ◆ 10,000 · II+II / I+III ◆ 15,000 · II+III ◆ 20,000 · III+III ◆ 25,000. Never gems (no paid merges).

### 5.3 Flow
Wardrobe → set detail (level 50) → **Merge** → pick the second set (only valid partners are clickable; the others say why in red: `✘ Needs Lv 50`, `✘ Both boost crystals`, `✘ Tier IV can't merge`, `✘ Already merged`) → pick the look → 3-row confirm ("This will: combine Deepforge Plate Lv 50 and Coinmaster Silks Lv 50 into one set at Lv 0 · cost ◆ 25,000 · can't be undone") → **one database transaction**: both entries `ACTIVE → MERGED`, new entry inserted (`origin merge`, references both), crystals debited (idempotency key `wardrobe_merge:<new id>`), enchant books created, loadout references updated, audit row. Any failure: nothing changes.

**Good merges** (examples): Deepforge · Coinmaster (money miner), Dreadguard · Lorekeeper (boss hunter who levels fast), Thornward · Cropwarden (gatherer), Stormkeel · Coinmaster (rich angler).

---

## 6. `/wardrobe` (GUI)

Opened by `/wardrobe` (replaces `/sets`; `/sets` stays as an alias, WD24), the `/menu` Gear category (UI_STYLE icon for sets: leather chestplate dyed Phoenix Red) and `/help wardrobe`. All menus use the 6-row standard frame (UI_STYLE §5.1) and are click-only.

### 6.1 Main: all sets
```
Content (28 slots)   all 16 sets, grouped by activity (owned first in each group), merged sets after their first set
                     owned:   set icon (chestplate in the set's look) · "Lv 23 · 0.92×" · pieces worn 2/4 · ▸ Click to open
                     buyable: price + gate ✔ · ▸ Click to see and buy
                     locked:  ✘ Locked + requirement and progress ("Mining tier 6 ▸ 4/6") or source ("Phoenix crate")
Slot 46  Filter: All / Owned / Mining / Foraging / Farming / Combat / Fishing / Conquest / Other (one click cycles)
Slot 47  Worn now: active set bonuses with their real values, families "capped" in gold
Slot 51  Wardrobe Chest (storage, §4.4) · 18/27
Slot 52  Loadouts (opens /loadout)
45/48/49/50/53  standard navigation (Back · Info · Close · Help · Next)
```

### 6.2 Set detail
```
Row 2   [Helm] [Cuirass] [Greaves] [Boots]   each: ✔ Worn / ▸ Click to wear / ▸ Click to take off
        [Wear all] [Take all off]
Row 3   [Bonuses]  2 pieces / 4 pieces: value now · at Lv 50 · family
        [Level]    Lv 23 ●●●●○○○○○○ 46% · 0.92× · next: 1,612 XP · earns from: Mining
        [Enchants] slots per piece (E5; "Coming soon" until then)
Row 4   [Merge] (Lv 50 only)   [Use a Wardrobe Tome]   [Buy] or [Redeem token] if not owned
```
Wearing a single piece only works if you own the set (whole sets only). Wearing a piece of another set in that slot swaps it (virtual, instant).

### 6.3 Rules
- Equip/unequip/merge/buy follow the same place rules as loadouts: **not in combat, not in the warzone, not frozen** (LD6), 2 s cooldown between equip actions (shared with loadouts).
- Gem purchases and merges use the confirm dialog (UI_STYLE §5.2). Money purchases above the `/pay` confirm threshold ($1M) too.
- Bedrock: everything is a button; no item ever has to be dragged; shift-click is never needed.

---

## 7. `/loadout` (L1–L4)

### 7.1 What a loadout holds (L1, LD1)
| Part | Saved as | Applying it |
|---|---|---|
| Armor (4 slots) | Wardrobe entry + piece per slot, or "empty", or "keep" | Wears those pieces (virtual); pieces not in the loadout are taken off (stay in the wardrobe) |
| Pet | Pet id, or "keep" | **Hook**: `common/pet/PetHook` (no-op until pets exist); the slot shows "Pets are coming" |
| Hotbar (slots 1–9) | Per slot: a **unique item UUID** (custom tool, sell wand later), or "keep" | Puts that item in that hotbar slot, from the inventory or from wardrobe storage. The unique item that was in the slot goes into **wardrobe storage** (L3); a plain item in the slot moves to a free inventory slot |

"Save current" captures the worn wardrobe pieces, the active pet and every hotbar slot that holds a unique item (plain items are saved as "keep"). Vanilla armor is never saved (it is not a wardrobe item).

### 7.2 Presets (L2, LD2)
- **2 free** per profile. More from ranks (`skyblock.loadout.slots.<n>`; default grants in `server/permissions/wardrobe.txt` when rank perks are planned: Knight 3, Warlord 4, Monarch 5) and **bought with store credits**: +1 per purchase, max +3 (account-wide, kept across seasons, works on every profile like rank perks PR3). Max total **8**.
- Store credits don't exist in-game yet: bought slots are an account table (`loadout_slots_bought`) that the future store module writes; until then only free + rank slots exist.
- Each preset has a name (≤ 16 characters, chat filter), an icon (pick from its items) and a position.

### 7.3 Where and how often (L4, LD3)
- **Anywhere except in combat or the warzone**: refused while combat-tagged (dealt or took damage from a player or hostile mob in the last **10 s**, `loadout.combat-seconds`), in any world listed in `loadout.blocked-worlds: [warzone]`, while frozen, while a profile switch or another wardrobe action is running.
- **2 s cooldown** per player between swaps (`loadout.cooldown-seconds: 2`), shared with wardrobe equip actions.
- Combat tag: uses a shared `CombatTagService` in common if one exists by then (PROF's "not in combat" rule needs the same); otherwise W3 adds it (small: a map of last-combat timestamps fed by damage events).

### 7.4 Applying a loadout atomically (dupe safety, LD4)
1. **Plan** (main thread, pure and unit-tested): from the inventory snapshot, the storage rows and the preset, decide every move: which pieces to wear, which unique items come from the inventory (just a slot move), which come from storage (`take`), which leave the hotbar into storage (`store`), where displaced plain items go. If a plain item has nowhere to go, that hotbar slot is skipped (never dropped) and the message says which. If a tool in the preset is gone (sold, traded, merged away), that slot is skipped and the preset shows it in red.
2. **Lock** the player for the swap (inventory clicks blocked, menu closed; typically one round trip to the database, < 100 ms).
3. **Remove** the outgoing unique items from the inventory (kept in memory with the plan id).
4. **One database transaction**: store the outgoing items (`STORED`), mark incoming storage items `RELEASING` (plan id), write the new `wardrobe_equipped` rows (new nonces), set the active pet id, audit row `loadout_apply`. Commit.
5. **Main thread**: on success put incoming items in their slots, re-render armor from the new rows, mark `RELEASING → RELEASED`, refresh bonuses; on failure give the outgoing items back to the exact slots they came from (nothing was stored). An unknown outcome (database timeout) is re-read before anything is given back; never given back blindly.
6. **Crash or quit mid-way**: the recovery rules of §4.4 apply (missing `RELEASING` items are given again on join, stale inventory copies of `STORED` items are removed). Wardrobe pieces need no recovery: they are rendered from the rows.

A swap moves only unique, tracked items, so at every moment each tool exists in exactly one place: in the inventory, in storage, or "in flight" with a journal row.

### 7.5 `/loadout` menu and command (LD5)
- `/loadout` opens the menu: presets in row 2 (click = **apply**, the fast path), an `✎ Edit` button under each in row 3 (opens: Save current · Rename · Icon · Preview contents · Delete with confirm), empty slots show "▸ Click to save your current gear" or "✘ Locked · Knight rank or store".
- `/loadout <name>` applies a preset by name (tab completion lists the player's preset names; not a new command, an argument of the approved one). Useful for Java players who bind it; Bedrock players use the menu.
- Feedback: sound + action bar "Loadout Mining · 4 pieces, 3 tools". Skipped slots are listed in chat in one line.

### 7.6 Pets (hook)
`PetHook` interface in common: `activePet(profileKey)`, `summon(player, petId)`, `ownedPets(profileKey)`. W3 ships the no-op version and the preset field; the pets phase implements it. Until then the pet slot is shown, saved as "keep" and does nothing.

---

## 8. Data, services and config

### 8.1 Module and common API
- **Module:** `modules/wardrobe` (new; both `/wardrobe` and `/loadout`, since loadouts write the same storage). Permissions in `modules/wardrobe/src/main/resources/permissions/wardrobe.yml`, LuckPerms grants in `server/permissions/wardrobe.txt`, command docs in `docs/commands/wardrobe.md`, `/help` topics `wardrobe` and `loadout`.
- **Common:** `common/wardrobe/WardrobeService` (worn set bonuses, set level of a set id for a profile, `setXp(player, activity, amount)` entry point for other modules, enchant slot count per piece for E5), `common/bonus/BonusService` + `BonusFamily` (shared with E1; whichever of E1/W1 is built first creates it, the two never run at the same time), `common/pet/PetHook` (W3), `common/combat/CombatTagService` (W3, if nobody added it before).
- **Profiles:** every table keys per-profile data by the **profile key** (`ProfileService.profileKey`); account data (Sigils, bought loadout slots) by the player UUID. The P4 rule holds: nothing moves between own profiles (tokens and books are traded with `ProfileService.blockOwnTransfer` checks in trade/AH, as for everything else).

### 8.2 Tables (migrations via the core runner)
| Table | Key columns | Notes |
|---|---|---|
| `wardrobe_sets` | entry_id (PK), profile_key, set_id, merged_set_id (null), look_set_id, level, xp, state ACTIVE/MERGED/REMOVED, origin (money/crystals/gems_claim/token/merge/staff), origin_ref, created_at, version | One ACTIVE unmerged row per (profile, set_id) |
| `wardrobe_pieces` | entry_id, piece, enchants JSON | Enchant data for E5 (the item only shows it) |
| `wardrobe_equipped` | profile_key, slot (PK pair), entry_id, nonce | The truth for worn pieces |
| `wardrobe_storage` | item_uuid (PK), profile_key, kind (TOOL/BOOK/SCROLL/TOKEN/TOME), item blob, state STORED/RELEASING/RELEASED, plan_id, updated_at | Escrow rows (§4.4) |
| `wardrobe_sigils` | player_uuid, set_id (PK pair), gems, bought_at | Account-wide gem purchases (kept across seasons) |
| `wardrobe_claims` | profile_key, set_id, season | One claim per profile per season |
| `set_tokens` | token_uuid (PK), set_id, state ACTIVE/REDEEMED/CONFISCATED, origin, created_at, redeemed_profile | Unique items like enchant books |
| `loadouts` | loadout_id (PK), profile_key, position, name, icon, armor JSON, pet_id, hotbar JSON, updated_at | Per profile |
| `loadout_slots_bought` | player_uuid (PK), count | Written by the store module later |

### 8.3 Config
- `sets.yml` (typed, validated by a unit test): per set id: enabled / enabled-from, name, short name, activity, tier, rarity, look (material, dye, trim pattern, trim material, level-10 trim material), armor points, source (type, price, currency, gate, crate entries), bonuses (pieces 2/4 → list of family, scope, base value), XP sources.
- `wardrobe.yml`: levels (base XP, growth, max, max multiplier, merged XP factor, piece XP shares), XP per action table, stacked-mob XP (0), Coinmaster hourly cap, tome amounts and max level, merge rules + fee per tier step, storage size, equip cooldown, blocked worlds, combat seconds, warzone normalization values, loadout free slots / max / bought max, reconcile alerts.
- `messages/wardrobe.yml`: every name, lore line, menu text and message.

### 8.4 Audit (every data change)
Set bought / claimed / token redeemed / removed by staff; Sigil bought; equip and unequip (category `wardrobe`, short retention); level-up and tome used; merge (inputs, output, fee, returned books); storage store/take/recover; stale piece deleted; token confiscated; loadout save/apply/rename/delete; staff actions.

### 8.5 Commands and permissions
| Command | Who | Permission |
|---|---|---|
| `/wardrobe` (alias `/sets`) | everyone | `skyblock.wardrobe` (default true) |
| `/loadout [name]` | everyone | `skyblock.loadout` (default true); slots `skyblock.loadout.slots.<n>` |
| Storage size | ranks | `skyblock.wardrobe.storage.<n>` |
| Staff | admin + owner | No new staff command (WD25, open question): staff get a **Wardrobe** button in `/inspect <player>` (view sets, storage and loadouts; admins: give a set, set a level, remove a set, give a token; all audited). Staff test commands like `/wardrobe admin give` need owner approval first |

---

## 9. Anti-abuse

| Risk | Default protection |
|---|---|
| Duping armor | Pieces are views of a row (§4); copies have no valid nonce and are deleted; stats only come from valid pieces in armor slots |
| Duping tools through loadouts or storage | AH escrow pattern, unique UUIDs, journal + recovery, stale copies removed (§4.4, §7.4) |
| Duping tokens | Unique token rows; a copy of a redeemed token is confiscated (like books and notes) |
| Profile hopping (P4) | Sets bound to the profile; tokens/books trade only through `/trade`/AH with the same-account block; Sigil claims are per profile, not transfers |
| Alts farming Sigils | Gems are account-wide; a Sigil only gives claims to the same account's profiles |
| Pay-to-win | 0× at level 0; gem sets tier II–III with gates and crate alternatives; tier IV earned only; no gems for XP/merges; PvP normalized |
| XP farming | Stacked mobs give no set XP; sell XP capped per hour; warzone kill XP has a per-victim cooldown and a playtime floor; Depths actions already pass the per-player block engine's anti-fast-break check |
| Swap spam / lag | 2 s cooldown, one swap in flight per player, all database work async, bonuses cached |
| Combat escape | No swaps in combat or the warzone (L4) |
| Exploits found later | Every number in config; `sets.yml → <id>.enabled: false` disables a set (owned copies stay, shown gray, no bonus) |

---

## 10. Bedrock notes
- **Looks:** vanilla armor materials, leather dye and armor trims only, which Geyser shows natively; per-set custom models come with the resource pack plan (`docs/plans/RESOURCE_PACK.md`).
- **Lock:** cancelled armor-slot clicks are followed by an inventory resend next tick so Geyser never shows a piece "in the cursor".
- **Menus:** click-only; no dragging items into slots; Previous/Next at 45/53; confirmations in the 3-row dialog; the apply button is the preset itself (no right-click).
- **Text:** set names ≤ 16 characters, piece names ≤ 24, lore ≤ 36 characters per line (unit test over `messages/wardrobe.yml`, `sets.yml` and generated lore); levels as `Lv 23 · 0.92×`; only UI_STYLE §2.1 symbols (`▸ ✔ ✘ ★ ◆ ✦ ●`).
- **Stats:** armor, toughness, speed and health via attribute modifiers; growth/drop/sell bonuses are server-side; nothing needs client mods.

---

## 11. Synergy with other plans
| System | Link |
|---|---|
| Enchants (`plans/ENCHANTS.md`) | Armor enchants live on wardrobe pieces (E5): 1 slot per piece, a 2nd at set level 25, chestplate mythic slot at 50 (EN12, confirmed here); enchant level gate by set level (EN4); merges return extra enchants as books |
| Bonus families | All set bonuses go through `BonusService`; new families crop growth and conquest (§2.5) |
| Tools (M4.2) | Loadouts move custom tools; storage keeps their live item data; `tools` row location `WARDROBE` |
| Profiles (PROF) | Per-profile tables; switch hooks strip/re-equip pieces; Sigils account-wide |
| Crates (later) | Set Tokens and Wardrobe Tomes are crate entries (§2.4, §3.1) |
| Economy | Money sets and crystal sets/merges are sinks; Coinmaster feeds the sell family (`MultiplierCap`, +300%) |
| Masteries | Crop Growth / Harvest Yield / Crystal Rush / Market Contacts add up with sets inside the families |
| Conquest (later) | Banneret / Conqueror bonuses hook into KOTH capture, envoys and the warzone death penalty (WR1) |
| Pets (later) | Loadout pet slot through `PetHook` |

---

## 12. Implementation milestones

### 12.1 Phase W
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| W1 | Wardrobe core: `sets.yml` registry (16 typed sets + validation test), per-profile wardrobe tables + migrations, virtual entries + locked real armor (PDC/nonce, lock listeners, reconcile, `DupeReport` handler, profile-switch strip/re-equip), wardrobe storage with escrow + recovery, buying with money/crystals/gems (Sigils + claims), Set Tokens (unique items, redeem), `/wardrobe` GUI (all sets, filters, set detail, worn bonuses, storage), set bonuses through `BonusService` at a fixed 1× (`levels.enabled: false`), staff Wardrobe button in `/inspect`, `/menu` + `/help` entries | PROF, CRY | new modules/wardrobe, common/wardrobe, common/bonus (not in parallel with E1), modules/staff (inspect button), modules/menus (entry) | ⬜ |
| W2 | Set levels: XP sources (Depths block/log hooks, crops, combat kills, sell, conquest stubs), piece XP shares, curve + multiplier, level-ups + milestone unlocks (trim upgrade, glint, trail), Wardrobe Tomes, lowest-level rule; merging (rules, fee, look pick, returned books hook, loadout re-pointing, one transaction); warzone normalization | W1, M4.5 (skills events) | modules/wardrobe (+ hook events in modules/depths if missing) | ⬜ |
| W3 | `/loadout`: presets per profile (2 free, rank slots, bought-slot table), save/apply/edit menu, `/loadout <name>`, atomic apply planner + escrow moves (§7.4), combat tag + warzone + freeze guards, 2 s cooldown, `PetHook` no-op, tools location `WARDROBE` | W1, M4.2 | modules/wardrobe, common/pet, common/combat (if missing), modules/tools (location hook) | ⬜ |
| W4 | Balance: set factors in `tools/economy_model.py` + `ECONOMY_BALANCE.md` (sell proxy split into upgrade/set/booster, crystals and drop families, set XP per hour per activity), family caps verified in `BonusService` tests, prices and XP values tuned to measured actions per hour, dashboard rows (sets bought per source, merges, crystals spent) for M3.8 | W2, W3, BAL2 | docs, tools/economy_model.py, wardrobe configs | ✅ (TUNE: model + doc, no set value changed; dashboard rows left for M3.8 follow-up) |
| E5 | Armor enchants on wardrobe pieces (already in the enchant queue) | E1, W2 | modules/enchants + WardrobeService | ⬜ |

- W1 and E1 both create or use `common/bonus`: never at the same time; the second one adopts what the first built.
- W2 and W3 both live in `modules/wardrobe`: one after the other (W3 may start once W2 has merged, or W3 first if M4.2 lands earlier; they share no new tables except `wardrobe_storage`, which W1 creates).
- Conquest and fishing XP sources are stubs until those phases exist; the sets show "Arrives with …" until then.

### 12.2 Unit tests (required per milestone)
- `sets.yml` validation: ids unique, names ≤ 16, piece names ≤ 24, lore ≤ 36, prices > 0, gates valid, every bonus family known, tier IV not mergeable.
- Level curve (every row of §3.2), multiplier, merged ×1.5, tome cap at 40, piece XP shares.
- Bonus computation: 2+2 mixes, 4-piece, merged entries counting for both sets, lowest-level rule, family caps (sell uses the economy cap), warzone normalization.
- Merge validation (level 50, family overlap, tier IV, already merged) and fee table; merge transaction rolls back fully on failure (in-memory H2).
- Storage state machine: STORED / RELEASING / RELEASED, recovery of RELEASING, stale-copy removal (H2).
- Reconcile rules: valid/invalid piece decisions for every case in §4.3.
- Loadout planner: hotbar moves from inventory and storage, displaced plain items, full inventory, missing tool, keep slots, armor over vanilla armor.
- Purchase idempotency (same key twice charges once); Sigil claim once per profile per season.

### 12.3 Owner test checklists (Java + Bedrock)
| Milestone | You check |
|---|---|
| W1 | `/wardrobe` opens on both clients and shows all 16 sets with locks/prices; buy a tier I set with money; wear 2 then 4 pieces from the set menu; try to take a piece off by clicking, dragging, number keys, dropping (Java) and tapping (Bedrock): it stays and the hint shows; die (keep inventory, pieces stay); switch profile and back: the right set is worn; buy a gem set (confirm dialog), claim it on a second profile; staff gives a Set Token via `/inspect`, trade it, redeem it; store a tool in the Wardrobe Chest and take it back; bonuses show in "Worn now" |
| W2 | Mine with 2 then 4 pieces: set XP in the action bar, level 1 within minutes; level-up sound/title at 10 and 25; a tome works and stops at 40; staff sets two sets to 50 and you merge them (look pick, confirm, level 0, both bonuses listed); a refused merge shows why; in the warzone the stat screen shows 1× |
| W3 | Save "Mining" and "Combat" presets; `/loadout Mining` swaps armor and hotbar tools, old tools land in the Wardrobe Chest; full inventory skips a slot without dropping anything; swap is refused right after hitting a mob and in the warzone; 2 s cooldown; works from the menu on Bedrock; relog during a swap loses and duplicates nothing |
| W4 | Balance sheet regenerated with set factors; set XP per hour in each activity close to the §3.2 table |

### 12.4 CHANGELOG line (for the main session)
`docs: wardrobe + loadout plan (docs/plans/WARDROBE.md): 16 sets (money/crystals/gems/crates), set levels 0–50 (0×→2×), merging, virtual wardrobe with locked armor, /loadout presets, milestones W1–W4`

---

## 13. Open questions (defaults apply; also in `OPEN_QUESTIONS.md`)
1. Virtual wardrobe entries + locked real armor (recommended) instead of tradeable armor items? (default: yes, WD19)
2. The 16 sets, names, activities and sources (§2.2)? (default: yes, WD2)
3. Bonus values (§2.3), new families crop growth +100% and conquest +60% (§2.5)? (default: yes, WD3/WD9)
4. Prices: tier I $20k, Banneret $2.5M, Coinmaster $40M, tier III crystal sets ◆ 25,000, gem sets ✦ 400 / ✦ 900 (also in crates), tier IV crate-only (§2.4)? (default: yes, WD4)
5. Gem sets as account-wide Sigils with one free claim per profile per season, kept across seasons? (default: yes, WD5)
6. Sets bound to the profile (not tradeable); only unredeemed Set Tokens trade and go on the AH? (default: yes, WD7)
7. Set XP from the boosted activity, 50% with 2–3 pieces and 100% with 4; no XP from stacked mobs (§3.1)? (default: yes, WD10–WD12)
8. Level curve 200 × 1.10^L, multiplier 2 × level / 50 (linear), merged sets need 1.5× XP (§3.2)? (default: yes, WD14/WD15)
9. Merge rules: both level 50, no shared family, tier I–III only, one merge per set, fee ◆ 5,000–25,000, no unmerge (§5)? (default: yes, WD22/WD23)
10. Wardrobe Tomes (+2,500 / +10,000 set XP, not past level 40, never for gems)? (default: yes, WD13)
11. Warzone/duels: every set at 1×, armor normalized to diamond-like, damage reduction at 50%, only conquest bonuses (§3.6)? (default: yes, WD18)
12. Wardrobe Chest holds unique items only (tools, books, scrolls, tokens, tomes), 27 slots + rank grants? (default: yes, WD21)
13. Loadout hotbar moves unique items only; plain items are never stored (§7.1)? (default: yes, LD1)
14. Loadout slots: Knight 3, Warlord 4, Monarch 5; +1 per store purchase, max +3, max 8 total (§7.2)? (default: yes, LD2; credit price set with the store plan)
15. Combat tag 10 s for swaps (and wardrobe equips) (§7.3)? (default: yes, LD3)
16. `/loadout <name>` applies a preset by name; `/sets` stays as an alias of `/wardrobe`? (default: yes, LD5/WD24)
17. Staff tools: a Wardrobe button in `/inspect` instead of a new staff command (§8.5)? (default: yes, WD25)

---

## 14. Defaults register
| Id | Default | Section |
|---|---|---|
| WD1 | Tiers I–IV with strength factors 1 / 1.5 / 2 / 2.5, gates, armor points by tier | §2.1 |
| WD2 | 16 sets, names, activities, sources, looks | §2.2 |
| WD3 | Bonus values at 1× (level 25) and 2× (level 50) | §2.3 |
| WD4 | Prices and crate entries | §2.4 |
| WD5 | Gem sets as account Sigils, one claim per profile per season, kept across seasons | §2.4 |
| WD6 | One copy per set per profile; merged sets are separate entries | §2.4 |
| WD7 | Sets bound to the profile; Set Tokens tradeable until redeemed | §2.4 |
| WD8 | No refunds or selling back | §2.4 |
| WD9 | New bonus families crop growth (+100%) and conquest (+60%) | §2.5 |
| WD10 | Set XP per action table, ~3,000–4,000 XP per played hour in every activity | §3.1 |
| WD11 | 2–3 pieces earn 50% XP, 4 pieces 100% | §3.1 |
| WD12 | Stacked island mobs give no set XP; sell XP capped at 8,000/h | §3.1 |
| WD13 | Wardrobe Tomes +2,500 / +10,000, max level 40, never for gems | §3.1 |
| WD14 | XP per level 200 × 1.10^L, max 50, multiplier 2 × L / 50 | §3.2 |
| WD15 | Merged sets need 1.5× XP | §3.2 |
| WD16 | Milestones at 10 / 25 / 40 / 50 | §3.3 |
| WD17 | Bonus counting: merged entries count for both sets; lowest-level entry sets the multiplier | §3.4 |
| WD18 | Warzone/duels normalization | §3.6 |
| WD19 | Virtual entries + locked real armor (option B) | §4.1 |
| WD20 | Lock rules, reconcile, vanilla armor handling | §4.3 |
| WD21 | Wardrobe Chest: unique items only, 27 slots + rank grants, AH escrow pattern | §4.4 |
| WD22 | Merge rules | §5.1 |
| WD23 | Merge fee ◆ 5,000 × (tier A + tier B − 1), never gems | §5.2 |
| WD24 | `/sets` alias of `/wardrobe` | §6 |
| WD25 | Staff via a Wardrobe button in `/inspect`; staff commands only after approval | §8.5 |
| LD1 | Loadout = 4 armor slots + pet + hotbar unique items; plain items never stored | §7.1 |
| LD2 | 2 free presets, rank slots (Knight 3, Warlord 4, Monarch 5), +1 per credit purchase (max +3), max 8 | §7.2 |
| LD3 | Not in combat (10 s tag), not in the warzone, not frozen; 2 s cooldown | §7.3 |
| LD4 | Atomic apply: plan → remove → one transaction → give; recovery on join | §7.4 |
| LD5 | Click a preset to apply; `/loadout <name>` | §7.5 |
| LD6 | Wardrobe equips follow the loadout place rules and share the cooldown | §6.3 |
