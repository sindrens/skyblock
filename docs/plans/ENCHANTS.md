# Custom Enchants Plan (PLAN-ENCH)

> **Status:** plan, 2026-10-30. The owner approved building this with the suggested defaults (overnight mandate, `STATUS.md`).
> **Every rule and number in this file is a default** the owner can change later. All numbers go into typed configs (`enchants.yml`, `enchanter.yml`), all text into `messages/enchants.yml`, never into code. Defaults that matter most have an id (`EN1`…) so the owner can answer "EN7: no" (list in §15).
> Sources: `DESIGN.md` §5.1 + §6.3, `phases/PHASE-4-DEPTHS.md` M4.2 (living tools), `DECISIONS_2026-10-30.md` (stacking S4, V6 harvester hoe, crystals CR1–CR4, masteries MA1–MA12, wardrobe/loadout, #46 +300% cap, #61 pacing), `ECONOMY_BALANCE.md` §2.4 + §9.1, `WORLD.md` §4–5, `UI_STYLE.md` §1.4 + §4, `REVIEW.md` A3 + A8.

---

## 0. Summary

- **48 custom enchants** in 7 groups: shared gathering (9), pickaxe (4), axe (4), hoe (5), sword (12, of which 4 for stacked mobs), rod (5), armor (9). Rarities Common → Mythic (UI_STYLE §1.4 colors). Six Mythics, one per tool type plus armor.
- **Books unlock, crystals level up.** You find an **enchant book** (by mining, chopping, farming, killing, fishing, bosses, crates, or a crystal "roll" at the Enchanter). At **`/enchanter`** you put it into a free **perk slot** of a custom tool (slots at tool level 5 / 15 / 30 / 45 + prestige) and pay crystals. After that you raise its level with **crystals** (×1.8 per step, `ECONOMY_BALANCE.md` §9.1) or by **combining two equal books** (cheaper, needs a second book).
- **Never a fail chance** (DESIGN §6.3). Removing keeps the book with a **Disenchant Scroll**; without a scroll the enchant shatters for a 20% crystal refund.
- **Books are unique, tradeable items** (trade + auction house), one database row each, so a copy is caught like a money note.
- **Stacked mobs:** without an enchant a hit kills one mob of a stack (owner decision S4). **Slaughter** (I–X) kills 2 → 48 mobs per hit; **Reaper's Sweep**, **Bloodlust** and **Soul Reaper** build on it.
- **Synergy, no pay-to-win:** every bonus belongs to a **bonus family** with one cap (sell +300%, the owner's #46). Masteries, sets, ranks, boosters and enchants add up inside the family, then the cap applies once. Books and crystals are never sold for gems or real money.
- **Pacing (#61):** the first book arrives within minutes (a guaranteed first find at the 100th action), then about one per played hour, level I of anything costs 50–250 crystals, then each level is harder. New Depths tiers unlock new enchants and better rarity odds (the "ups"); costs ×1.8 per step (the "climb").
- **Milestones:** E1 registry + books + `/enchanter` → E2 drop hooks + gathering/combat effects → E3 stacked-mob enchants → E4 tuning; E5 armor (after the wardrobe), E6 hoe + rod (after Phase 7). See §13.

---

## 1. Design goals

| Goal | What it means here |
|---|---|
| **Synergy** (DESIGN rule 1) | Enchants multiply what masteries, sets, skills and tool levels already do, inside shared caps. A Timber mastery island with a Forager set and a Prosperity axe is better than any one of them, but never above the cap. Enchants also feed other systems: crystals (Crystal Seeker), blueprints (Treasure Hunter), tool XP (Tempering), island stacking (Slaughter) |
| **Fast start** (#61) | First book guaranteed after 100 actions in each activity (EN5). Level I costs 50–250 crystals (an active player earns ~100 in the first hour). Slot 1 at tool level 5 (~1 h) gets an enchant on day 1 |
| **Progressively harder, with ups and downs** (#61) | Costs ×1.8 per level step. Each Depths tier band (1–3, 4–6, 7–9, 10–12) improves rarity odds and unlocks new enchants: a felt "up" every few tiers. Proc enchants (Momentum, Haste, Harvest Moon, Bloodlust) add moment-to-moment highs |
| **Less is more** (DESIGN rule 2) | One effect, one name across tools (Swiftness works on pickaxe and axe; Crystal Seeker on every tool). Effects are one line each. 48 enchants, not 150 |
| **No pay-to-win** (REVIEW A8) | Books, scrolls and crystals are never sold for gems or store credits. Crate keys are earned only (decided 2026-10-29). PvP uses normalized values (§8.3) |
| **Choices matter** | 4–5 slots per tool and conflict groups (one area enchant, one burst enchant per tool) force builds: a "money" pickaxe, a "crystal" pickaxe, a "speed" pickaxe |
| **Fair for traders** | Books and enchanted tools are tradeable, so a casual player can buy power with money they earned in-game; crystals themselves stay untradeable (CR3) |
| **Bedrock first** | Every flow is clicks only; names ≤ 16 characters; Roman numerals; no special symbols (§11) |

---

## 2. Core rules

### 2.1 Where enchants go (EN1)
- **Custom tools only** (pickaxe, axe, hoe, sword, rod from the Quartermaster, the Forge or blueprints), in their **perk slots** (M4.2): slot 1 at tool level 5, slot 2 at 15, slot 3 at 30, slot 4 at 45, **slot 5 = prestige slot** (after the tool's prestige).
- **Armor enchants** go on **wardrobe set pieces** (REVIEW A3; `/wardrobe` replaced `/sets`). Default slots per piece: 1 slot, a 2nd at set level 25, and the **chestplate** gets a mythic slot at set level 50 (EN12; final numbers in `docs/plans/SETS.md`).
- Vanilla tools and armor keep vanilla enchanting only (WORLD §6). Custom tools can't be enchanted at a vanilla table or anvil.
- Enchants stay on the item: upgrading the tool at the Forge, prestiging, trading it, swapping it with a `/loadout` or moving it to the wardrobe keeps them.

### 2.2 Slot cost
- Every enchant costs **1 slot**, except:
  - **Dragonfire Blast** and **Wildfire** (Legendary area enchants) cost **2 slots** (EN3).
  - **Mythic** enchants fit **only in the prestige slot** (tools) or the mythic armor slot (chestplate) (EN2). So a tool holds at most one Mythic.
- A tool never holds the same enchant twice.

### 2.3 Rarities (UI_STYLE §1.4)
| Rarity | Color | Book footer | Count | Crystal factor (§5) | Typical source |
|---|---|---|---|---|---|
| Common | `#D9D9D9` white | `COMMON BOOK` | 7 | ×0.5 | Every activity, tier 1+ |
| Uncommon | `#6BDB7A` green | `UNCOMMON BOOK` | 10 | ×0.75 | Every activity, tier 1+ |
| Rare | `#4F9DFF` blue | `RARE BOOK` | 14 | ×1.0 | Tier 1+ (rarely), common from tier 4 |
| Epic | `#B266FF` purple | `EPIC BOOK` | 8 | ×1.5 | Tier 4+ |
| Legendary | `#FFAA00` gold | `LEGENDARY BOOK` | 3 | ×2.5 | Tier 7+, mini-bosses, bosses, Phoenix crate |
| Mythic | `#FF55D4` pink | `MYTHIC BOOK` | 6 | ×4.0 | Eclipse crate, world bosses, Black Market only (UI_STYLE: "monthly/seasonal only") |

(The Special rarity stays for event cosmetics; no enchant uses it.)

### 2.4 Levels and the tool-level gate (EN4)
- Max level differs per enchant (I to X, §6). Levels show as Roman numerals.
- **Gate:** an enchant on a tool can be at most level `ceil(max × toolLevel / 50)`. A prestiged tool counts as level 50. Armor uses the set level the same way.

| Tool level | Max-III enchant | Max-IV | Max-V | Max-X |
|---|---|---|---|---|
| 5 (slot 1) | I | I | I | I |
| 15 (slot 2) | I | II | II | III |
| 30 (slot 3) | II | III | III | VI |
| 45 (slot 4) | III | IV | V | IX |
| 50 / prestiged | III | IV | V | X |

Why: enchant power grows with the living tool (synergy with M4.2), and a freshly forged alt tool can't carry a bought max book at once. The GUI shows "Needs tool Lv 34" instead of hiding the option.

### 2.5 Conflict groups (one per tool from each group)
| Group | Members | Why |
|---|---|---|
| Mine area | Vein Rend, Dragonfire Blast | One area enchant per pickaxe |
| Forest area | Treefeller, Wildfire | One area enchant per axe |
| Burst | Ember Momentum, Phoenix Haste | Both are speed bursts; pick a rhythm |
| Hoe burst | Harvest Moon, Sunfire Harvest | Both double drops in bursts |
| Mythic | all Mythics | One Mythic per item (follows from the prestige slot) |

Everything else combines freely. Mythics may join an area enchant (e.g. Worldbreaker + Vein Rend), because their trigger is a counter, not a chance.

### 2.6 Enchants work everywhere they make sense
- Gathering enchants work in The Depths and on islands/outposts (island generator ores, island trees, crops) unless the effect is Depths-only (area enchants break **your per-player blocks** only; on islands they break real blocks only where the player may break and never protected/other-island blocks).
- Sword enchants work in Depths combat zones, on island mobs and in PvP with PvP values (§8.3). Stacked-mob enchants only affect stacks (islands; stacks never exist in The Depths, warzone or boss fights, owner decision S1).

---

## 3. How you get books

### 3.1 Activity pools (EN6)
Every activity has a **pool**: all non-Mythic enchants usable on that activity's tool. A book drop first picks a **rarity** by the tier band (§3.3), then an enchant of that rarity from the pool (equal weights, configurable per enchant).

| Pool | Activity that drops it | Tool tier band from |
|---|---|---|
| Mining | Breaking ore in The Depths mine with a custom pickaxe (island generator ores: 25% of the chance) | Depths mine tier |
| Foraging | Chopping logs in The Depths forest (island trees: 25%) | Depths forest tier |
| Farming | Harvesting mature crops with a custom hoe (islands, Greenhold outpost) | Crop tier (WORLD §5.1) |
| Combat | Killing mobs in the Depths combat zones with a custom sword; mini-bosses; world bosses. Holds sword **and armor** books (armor weight 30%) | Depths combat tier |
| Fishing | Catches with a custom rod (Phase 7) | Fishing skill band (level 0–19 = A, 20–39 = B, 40–59 = C, 60+ = D) |
| Island stacks | Killing stacked mobs on islands with a custom sword (§7.5) | Only Slaughter, Soul Reaper, Bloodlust and Plunder |

### 3.2 Drop chance per action (EN7)
Target: **~1–1.4 books per played hour in every activity at every stage**, so players pick the wing they enjoy (same parity rule as income, ECONOMY_BALANCE §1.2). Higher tiers give more actions per hour, so the chance per action goes down while the rarity goes up.

| Source | Tier 1–3 | Tier 4–6 | Tier 7–9 | Tier 10–12 |
|---|---|---|---|---|
| Depths ore block | 1 in 2,000 | 1 in 3,000 | 1 in 4,000 | 1 in 5,000 |
| Depths log | 1 in 2,000 | 1 in 3,000 | 1 in 4,000 | 1 in 5,000 |
| Mature crop (custom hoe) | 1 in 4,000 | 1 in 5,000 | 1 in 6,000 | 1 in 7,000 |
| Depths combat kill | 1 in 150 | 1 in 200 | 1 in 250 | 1 in 300 |
| Fishing catch | 1 in 60 | 1 in 70 | 1 in 80 | 1 in 90 |
| Island stacked mob killed | 1 in 25,000 (max 5 books per day per profile) | | | |

Area-enchant blocks, Treefeller logs and harvester-area crops count as actions at the same yield as their drops (50% / 35% / 25%), so area enchants don't multiply book drops beyond their throughput. Crop and combat numbers are first guesses; E4 tunes them against measured actions per hour to hit the same books per hour as mining.

**Bosses** (Phase 8 hooks, stubs until then):
| Source | Roll |
|---|---|
| Mini-boss (each tier, every X min) | Every player with ≥ 10% damage: 30% chance of a Combat-pool book, rarity band one step better than the tier (band D stays D) |
| World boss | #1 damage: a guaranteed Legendary book (3% chance it is a Mythic instead); #2–#3: a guaranteed Epic; everyone else with ≥ 1% damage: 20% chance of a Rare-or-better book |
| Future dungeon runs (Phase 8) | Completion chest: 1 book of the dungeon's band |

**Crates** (keys are earned only): Ember = Common book, Cinder = Uncommon, Blaze = Rare, Inferno = Epic, Phoenix = Legendary, Eclipse (monthly) = 2% Mythic per opening among its rewards, Conqueror (KOTH) = Combat-pool Rare+ book or a Disenchant Scroll. Exact crate tables belong to the crates plan; these are the enchant entries.

**Black Market** (shards): one rotating Mythic or Legendary book per week, limited stock (1 per player).

### 3.3 Rarity odds by tier band (EN8)
| Band | Common | Uncommon | Rare | Epic | Legendary |
|---|---|---|---|---|---|
| A (tier 1–3) | 70% | 26% | 4% | 0 | 0 |
| B (tier 4–6) | 45% | 35% | 16% | 4% | 0 |
| C (tier 7–9) | 25% | 33% | 27% | 12% | 3% |
| D (tier 10–12) | 12% | 26% | 32% | 22% | 8% |

Each enchant also has a **minimum tier** (§6 column "From"): it only drops from that tier on. That turns every new tier into a chance at enchants you have never seen. If the rolled rarity has no enchant the player's tier allows in that pool, the drop falls back one rarity (Epic → Rare → …).

**Book level on drop:** band A–B: I 85%, II 12%, III 3%; band C–D: I 70%, II 22%, III 8% (never above the enchant's max).

### 3.4 Fast start and bad-luck protection (EN5)
- **First finds:** in each activity, your 100th action gives a guaranteed **Common** book and your 1,000th a guaranteed **Uncommon** (once per **account** per season, so new profiles can't farm it).
- **Pity:** after 2.5× the tier's expected gap without a book (5,000 / 7,500 / 10,000 / 12,500 blocks or logs by tier band; kills 375–750; catches 150–225), the next action guarantees one (counter per profile and activity; resets on any book from that activity).
- Both show in the Enchanter guide ("Next guaranteed book in 1,240 blocks").

### 3.5 Enchanter rolls (crystal sink, EN9)
At `/enchanter → Book shop`, a player picks a pool and pays crystals for a random book of that pool:

| Roll | Cost | Odds | Needs | Limit |
|---|---|---|---|---|
| Apprentice Roll | ◆ 500 | Common 70%, Uncommon 30% | — | 5 per day |
| Adept Roll | ◆ 4,000 | Uncommon 40%, Rare 50%, Epic 10% | Tier 4 in that wing | 5 per day |
| Master Roll | ◆ 25,000 | Rare 40%, Epic 50%, Legendary 10% | Tier 7 in that wing | 3 per day |

Rolls never give Mythics and respect each enchant's minimum tier. They give crystals a long-term use (an active player earns ~1.1M crystals a season, ECONOMY_BALANCE §9.1), while drops stay the main source.

---

## 4. The Enchanter (`/enchanter`, Enchanter NPC in the Forge Quarter)

`/enchanter` is already in the plan (DESIGN §6.3, `docs/commands/planned.md`). It opens the same menu as the NPC.

### 4.1 Main menu (6-row standard frame)
```
Row 2   [Apply a book]   [Upgrade]   [Combine books]   [Remove]
Row 3   [Book shop]      [Enchant guide]
Row 4   your crystals ◆ 12,480 · next guaranteed book: Mining 1,240 blocks
Row 6   standard navigation (Back · Info · Close · Help)
```

### 4.2 Apply a book (no fail chance, EN10)
1. **Pick the tool:** the menu lists every custom tool/set piece in your inventory and hotbar as buttons (no dragging items into slots; Bedrock-safe).
2. **Pick a slot:** the tool's slots as buttons: filled (enchant + level), empty (`▸ Empty slot`), locked (`Unlocks at Lv 30`), prestige (`★ Prestige slot`).
3. **Pick a book:** only books that fit are clickable; the others show why in red (`✘ Conflicts with Vein Rend`, `✘ Needs tool Lv 34`, `✘ Pickaxe only`, `✘ Needs 2 free slots`, `✘ Prestige slot only`).
4. **Confirm** (3-row confirmation, UI_STYLE §5.2): "This will: put Vein Rend II on Iron Pickaxe (slot 3) · use the book · cost ◆ 709".
5. **One database transaction:** book row `active → consumed`, crystals debited, tool perks updated, audit row. If any part fails, nothing changes. Sound `enchant`, the tool lore refreshes.

**Apply fee:** the crystal cost of level I of that enchant, or 25% of the crystal cost of the book's level if that is higher (so a high book saves crystals but isn't free).

### 4.3 Upgrade
Pick a tool → pick an enchant → the next level, its effect and its crystal cost (§5) → confirm. Blocked at max level or at the tool-level gate (shows the gate).

### 4.4 Combine books (EN11)
- **Two equal books** (same enchant, same level) → one book one level higher. Fee: 25% of the crystal cost of the new level.
- **Book + applied enchant** of the same level → the applied enchant goes up one level for the same 25% fee.
- Never above max. Both books are consumed in one transaction; the new book is a new row.

### 4.5 Remove (EN13)
- **With a Disenchant Scroll:** the enchant comes off as a book at its current level (crystal-bought levels included), so enchants can be **moved** to a new tool (DESIGN §6.3 "removed and moved"). The scroll is consumed.
- **Without a scroll ("Shatter"):** the enchant is destroyed and you get back 20% of the crystals put into it (apply fee + upgrades, tracked per slot). Confirm dialog, red warning.
- **Disenchant Scroll:** ◆ 2,500 in the Book shop (no daily limit), also from Cinder+ crates, the Conqueror crate and the Black Market. Tradeable, unique item like books.

### 4.6 Book shop and guide
- **Book shop:** the three rolls (§3.5) and Disenchant Scrolls.
- **Enchant guide:** every enchant by tool tab: name, rarity, max level, effect per level, slot cost, conflicts, where it drops (pool + minimum tier), and whether you have found it this season (collection feel). Mythics show as "???" until you have seen one (secret feel, no wall of text).

---

## 5. Crystal costs (EN14)

The level steps follow `ECONOMY_BALANCE.md` §9.1: step `s` costs `100 × 1.8^(s−1)` crystals (100, 180, 324 … 19,836 at step 10). An enchant's levels are spread over the 10 steps so **level I is always step 1 (cheap)** and **the max level is always step 10**:

- step of level `n` for an enchant with max `M` > 1: `round(1 + (n − 1) × 9 / (M − 1))`
- max-I enchants: step 6 (Mythics: step 10)
- **cost = step cost × rarity factor** (Common 0.5, Uncommon 0.75, Rare 1.0, Epic 1.5, Legendary 2.5, Mythic 4.0)

| Rarity · max | Level costs (◆) | Total to max |
|---|---|---|
| Common · V | 50 / 162 / 945 / 3,061 / 9,918 | 14,136 |
| Common · IV | 50 / 292 / 1,701 / 9,918 | 11,961 |
| Common · I | 945 | 945 |
| Uncommon · V | 75 / 243 / 1,417 / 4,592 / 14,877 | 21,204 |
| Uncommon · IV | 75 / 437 / 2,551 / 14,877 | 17,940 |
| Uncommon · III | 75 / 1,417 / 14,877 | 16,369 |
| Rare · X (Slaughter) | 100 / 180 / 324 / 583 / 1,050 / 1,890 / 3,401 / 6,122 / 11,020 / 19,836 | 44,506 |
| Rare · V | 100 / 324 / 1,890 / 6,122 / 19,836 | 28,272 |
| Rare · IV | 100 / 583 / 3,401 / 19,836 | 23,920 |
| Rare · III | 100 / 1,890 / 19,836 | 21,826 |
| Rare · I | 1,890 | 1,890 |
| Epic · III | 150 / 2,834 / 29,754 | 32,738 |
| Epic · II | 150 / 29,754 | 29,904 |
| Legendary · III | 250 / 4,724 / 49,590 | 54,564 |
| Mythic · I | 79,344 | 79,344 |

**Season sink check:** a fully kitted player has 5 tools × 5 slots + 4 armor pieces × 2 slots ≈ 33 enchants × ~30k ≈ **1M crystals to max everything**, about what an active player earns in a season (~1.1M). Rolls, scrolls, the island shop and Forge extras take the rest. Casual players max a handful.

---

## 6. The enchant list

Columns: **Max** = max level · **Effect** = per level (I / II / III …) · **Slot** = 1, 2 or P (prestige/mythic slot) · **From** = pool and minimum tier · **PvP** = value in warzone/duels (§8.3), "—" = no PvP effect. Ids are `snake_case` keys in `enchants.yml`. Bonus families (§8.1) in brackets.

### 6.1 Shared gathering enchants (9)
| Id | Name | Rarity | Max | Effect per level | Tools | Slot | From | Conflicts |
|---|---|---|---|---|---|---|---|---|
| `swiftness` | Swiftness | Common | V | +6% break/chop speed (+6 / 12 / 18 / 24 / 30%) [speed] | Pickaxe, axe | 1 | Mining, Foraging T1 | — |
| `scholars_insight` | Scholar's Insight | Common | V | +5% skill XP of the tool's skill (+5 … +25%) [skill XP] | All tools | 1 | All pools T1 | — |
| `prosperity` | Prosperity | Uncommon | V | +4% chance for +1 drop (4 / 8 / 12 / 16 / 20%) [extra drops] | Pickaxe, axe, hoe | 1 | Mining, Foraging, Farming T1 | — |
| `crystal_seeker` | Crystal Seeker | Uncommon | V | +8% crystals from this tool's actions (+8 … +40%) [crystals] | All tools | 1 | All pools T1 | — |
| `tempering` | Tempering | Uncommon | V | +8% tool XP (+8 … +40%) [tool XP] | All tools | 1 | All pools T1 | — |
| `gilded_touch` | Gilded Touch | Rare | V | +3% chance a drop is **Gilded** (sells for 2×) (3 / 6 / 9 / 12 / 15%) [sell] | Pickaxe, axe, hoe, rod | 1 | Mining, Foraging, Farming, Fishing T3 | — |
| `ember_momentum` | Ember Momentum | Rare | III | Every 40 actions without a 4 s pause: +1 stack (max 3) of +5 / 8 / 12% speed per stack; all stacks lost after 4 s idle [speed] | Pickaxe, axe, hoe | 1 | Mining, Foraging, Farming T4 | Burst |
| `phoenix_haste` | Phoenix Haste | Epic | III | 1 / 1.5 / 2% chance per action: +40% speed for 6 / 8 / 10 s (20 s cooldown) [speed] | Pickaxe, axe | 1 | Mining, Foraging T4 | Burst |
| `treasure_hunter` | Treasure Hunter | Epic | III | +15 / 30 / 45% chance for blueprints, key fragments and geodes [rare finds] | All tools | 1 | All pools T6 | — |

### 6.2 Pickaxe (4)
| Id | Name | Rarity | Max | Effect per level | Slot | From | Conflicts |
|---|---|---|---|---|---|---|---|
| `geode_sense` | Geode Sense | Rare | IV | +0.1% chance per block (0.1 / 0.2 / 0.3 / 0.4%) for a **Crystal Geode** worth 40× the tier's average crystals per block (≈ +4 / 8 / 12 / 16% crystals) [crystals] | 1 | Mining T1 | — |
| `vein_rend` | Vein Rend | Epic | III | 8 / 12 / 16% chance per ore to also break 2 / 4 / 6 of your nearest same ores within 2 blocks, at 50% yield (≈ +8 / 24 / 48% throughput) [area] | 1 | Mining T5 | Mine area |
| `dragonfire_blast` | Dragonfire Blast | Legendary | III | 4 / 6 / 8% chance per ore to blast your blocks in 3×3×1 / 3×3×2 / 3×3×3 around it, at 25% yield (≈ +8 / 25 / 52% throughput) [area] | **2** | Mining T7 | Mine area |
| `worldbreaker` | Worldbreaker | Mythic | I | Every 100th block: a 5×5×3 blast of your blocks at 50% yield and one guaranteed Crystal Geode (≈ +15% throughput) [area] | P | Mythic sources | Mythic |

### 6.3 Axe (4)
| Id | Name | Rarity | Max | Effect per level | Slot | From | Conflicts |
|---|---|---|---|---|---|---|---|
| `amber_sap` | Amber Sap | Rare | IV | +0.1% chance per log (0.1 / 0.2 / 0.3 / 0.4%) for **Amber Resin** worth 40× the tier's average crystals per log (≈ +4 … +16% crystals) [crystals] | 1 | Foraging T1 | — |
| `treefeller` | Treefeller | Epic | III | Chopping a log also fells 3 / 6 / 9 more logs of **your** tree at 35% yield; cooldown 4 / 3.5 / 3 s (≈ +13 / 30 / 52% throughput) [area] | 1 | Foraging T5 | Forest area |
| `wildfire` | Wildfire | Legendary | III | 3 / 4.5 / 6% chance per log: your tree and your 1 / 2 / 3 nearest trees within 8 blocks burn down at 25% yield (≈ +12 / 27 / 48% throughput) [area] | **2** | Foraging T7 | Forest area |
| `ashen_grove` | Ashen Grove | Mythic | I | Every 60th log: all your trees within 8 blocks fall at 50% yield (≈ +15% throughput) [area] | P | Mythic sources | Mythic |

### 6.4 Hoe (5), synergy with the harvester hoe (V6)
The harvester hoe (Forge) harvests 3×3 / 5×5 / 7×7 by its level and auto-replants (decided V6). These enchants build on it.

| Id | Name | Rarity | Max | Effect per level | Slot | From | Conflicts |
|---|---|---|---|---|---|---|---|
| `green_thumb` | Green Thumb | Rare | IV | Replanted crops start 1 / 2 / 3 / 4 growth stages ahead (scaled to each crop's stage count, never fully grown) | 1 | Farming T3 | — |
| `golden_seed` | Golden Seed | Rare | III | 0.05 / 0.1 / 0.15% chance per mature crop for a **Golden Seed**: plant it on your island for one Golden crop that sells for 50× that crop | 1 | Farming T4 | — |
| `wide_reap` | Wide Reap | Epic | II | +1 / +2 rings to the harvester area (max 9×9) | 1 | Farming T5 | — |
| `harvest_moon` | Harvest Moon | Legendary | III | 1 / 1.5 / 2% chance per harvest swing: ×2 crop drops for 10 s (60 s cooldown; ≤ +14%) [extra drops] | 1 | Farming T7 | Hoe burst |
| `sunfire_harvest` | Sunfire Harvest | Mythic | I | Every 80th harvest swing: every mature crop in 9×9 around you is harvested with ×2 drops and replanted [extra drops] | P | Mythic sources | Hoe burst, Mythic |

### 6.5 Sword: Depths combat and bosses (8)
| Id | Name | Rarity | Max | Effect per level | Slot | From | PvP | Conflicts |
|---|---|---|---|---|---|---|---|---|
| `phoenix_edge` | Phoenix Edge | Common | V | +5% damage to mobs (+5 … +25%) [mob damage] | 1 | Combat T1 | +2% per level | — |
| `bloodthirst` | Bloodthirst | Uncommon | IV | Heal 2 / 3 / 4 / 5% of damage dealt | 1 | Combat T1 | half | — |
| `ember_strike` | Ember Strike | Uncommon | III | 10 / 15 / 20% chance to set the target on fire for 3 s (vanilla fire, 1 heart/s) | 1 | Combat T2 | 5 / 7 / 10% | — |
| `plunder` | Plunder | Uncommon | IV | +5% chance per mob for one extra loot roll (5 … 20%), also per mob of a stack [extra drops] | 1 | Combat T2, Island stacks | — | — |
| `cleave` | Cleave | Rare | III | 25 / 35 / 45% of the hit's damage to other mobs within 2 / 2.5 / 3 blocks | 1 | Combat T3 | — (never hits players) | — |
| `giant_slayer` | Giant Slayer | Rare | IV | +8% damage to mini-bosses and world bosses (+8 … +32%) [mob damage] | 1 | Combat T4 | — | — |
| `executioner` | Executioner | Epic | III | Non-boss mobs below 10 / 15 / 20% health die on hit | 1 | Combat T6 | — | — |
| `phoenix_wrath` | Phoenix Wrath | Mythic | I | Every 20th hit: a fire nova dealing 300% weapon damage to mobs within 5 blocks; on stacks it kills the full Slaughter count from every stack in 5 blocks | P | Mythic sources | — | Mythic |

### 6.6 Sword: stacked mobs (4), details in §7
| Id | Name | Rarity | Max | Effect per level | Slot | From |
|---|---|---|---|---|---|---|
| `slaughter` | Slaughter | Rare | X | A killing hit on a stack kills 2 / 3 / 4 / 6 / 8 / 12 / 16 / 24 / 32 / 48 mobs | 1 | Combat T2, Island stacks |
| `soul_reaper` | Soul Reaper | Uncommon | V | +10% vanilla XP (+10 … +50%) and +6% Combat skill XP (+6 … +30%) from stacked kills [skill XP] | 1 | Combat T1, Island stacks |
| `bloodlust` | Bloodlust | Rare | V | Each killing stack hit within 2 s of the last adds +10% to the Slaughter count (min +1 mob), up to +10 / 20 / 30 / 40 / 50%; resets after 2 s | 1 | Combat T3, Island stacks |
| `reapers_sweep` | Reaper's Sweep | Epic | III | A killing stack hit also kills 25 / 50 / 75% of the Slaughter count (min 1) from up to 2 / 4 / 6 other stacks within 4 blocks | 1 | Combat T5 |

None of these work on players.

### 6.7 Rod (5), active with fishing (Phase 7)
| Id | Name | Rarity | Max | Effect per level | Slot | From | Conflicts |
|---|---|---|---|---|---|---|---|
| `quick_bite` | Quick Bite | Common | V | −6% wait for a bite (−6 … −30%) [speed] | 1 | Fishing A | — |
| `double_catch` | Double Catch | Uncommon | V | +4% chance to catch two (4 … 20%) [extra drops] | 1 | Fishing A | — |
| `deep_line` | Deep Line | Rare | IV | +10% weight for rare-or-better fish (+10 … +40%) [rare finds] | 1 | Fishing B | — |
| `tidecaller` | Tidecaller | Epic | III | 3 / 4 / 5% chance per catch for a **Tide Chest** (money, crystals, small chance of a key fragment, by Fishing band) [rare finds] | 1 | Fishing B | — |
| `leviathans_hook` | Leviathan's Hook | Mythic | I | Every 25th catch is rare-or-better and brings a Tide Chest | P | Mythic sources | Mythic |

### 6.8 Armor on wardrobe set pieces (9), active with the wardrobe
| Id | Name | Rarity | Max | Effect per level | Pieces | Slot | From | PvP |
|---|---|---|---|---|---|---|---|---|
| `vitality` | Vitality | Common | V | +1 max heart (+1 … +5); all armor together max +10 hearts [health] | Any | 1 | Combat T1 | half |
| `featherfall` | Featherfall | Common | IV | −20% fall damage (−20 … −80%) | Boots | 1 | Combat T1 | same |
| `night_eye` | Night Eye | Common | I | Night vision in The Depths and on islands (vanilla effect) | Helmet | 1 | Combat T2 | — (off in warzone) |
| `fortitude` | Fortitude | Uncommon | IV | −3% damage from mobs per level (−3 … −12% per piece) [damage reduction] | Any | 1 | Combat T2 | −1% per level |
| `swiftfoot` | Swiftfoot | Uncommon | III | +5% walk speed (+5 / 10 / 15%) | Boots | 1 | Combat T3 | — (off in warzone) |
| `ember_ward` | Ember Ward | Rare | I | Immune to fire and lava damage | Any | 1 | Combat T8 | — (off in warzone) |
| `second_wind` | Second Wind | Rare | III | After 5 s without damage: regenerate 0.5 / 1 / 1.5 hearts per second | Chestplate | 1 | Combat T4 | half |
| `phoenix_thorns` | Phoenix Thorns | Rare | III | Reflect 5 / 10 / 15% of melee damage to mobs | Chestplate, leggings | 1 | Combat T5 | 2 / 4 / 6% |
| `phoenix_rebirth` | Phoenix Rebirth | Mythic | I | Once per 10 min: lethal damage leaves you at 4 hearts with Fire Resistance + Regeneration II for 5 s | Chestplate | P | Mythic sources | — (off in warzone and duels) |

**Count:** 9 + 4 + 4 + 5 + 8 + 4 + 5 + 9 = **48**.

### 6.9 Name rules
Names are ≤ 16 characters (fits `Name X` in a 36-character lore line), phoenix/medieval flavor, no symbols. Names never repeat a mastery name (Timber, Swift Hands, Warrior, Crystal Rush, Lucky Strike, Angler…) so players can't confuse the two in menus.

---

## 7. Stacked-mob kill enchants

### 7.1 Base rule (owner decision S4)
Stacked mobs (islands only: spawner mobs + natural island mobs, up to 1,024 per stack, S2) die **one at a time** per killing hit, unless the weapon is a custom sword with **Slaughter**. Fall/lava kill the whole stack (S5) and drops/XP are full per mob (S6); those stay stacking rules, not enchants. Collectors/minions don't use sword enchants.

### 7.2 How a stack hit works
1. The player hits a stack's visible mob. Normal damage applies to that mob.
2. If the hit kills it, the **kill count** = Slaughter count (1 without Slaughter) × (1 + Bloodlust bonus), capped by the stack size.
3. That many mobs leave the stack at once; drops and XP are rolled per mob (Plunder per mob, Soul Reaper on the XP), handed to the stacking module in one batch (no 48 separate events).
4. **Reaper's Sweep** then removes 25/50/75% of the kill count from up to 2/4/6 other stacks within 4 blocks.
5. **Phoenix Wrath** (every 20th hit) applies the full kill count to every stack within 5 blocks.

### 7.3 Slaughter keeps up with your spawners
Assumption until STACK is built (E3 re-checks): a spawner makes ~0.1 mobs per second on average (vanilla delay 10–40 s, a few mobs per cycle) before the Spawner Rate mastery; a sword lands ~1.6 hits per second. "Keeps up with" = the number of stacked spawners one player can clear by hand without the stack filling up.

| Slaughter | Mobs per hit | Mobs per second | Keeps up with (spawners) | Fits the stage |
|---|---|---|---|---|
| none | 1 | 1.6 | 16 | First spawners (start stack 8, S20) |
| I | 2 | 3.2 | 32 | Day 1–3 |
| III | 4 | 6.4 | 64 | Week 1 |
| V | 8 | 12.8 | 128 | Week 2 |
| VII | 16 | 25.6 | 256 | Month 1 |
| IX | 32 | 51.2 | 512 | Max stack (512, S20) |
| X | 48 | 76.8 | 768 | Max stack + Spawner Rate mastery |

Bloodlust (+50% at V while you keep hitting) and Reaper's Sweep (several stacks at once) are what let one player handle the 8 stacks per chunk (SK1) late in the season. This keeps island combat production (~35% income meta) tied to the sword's progress rather than to "just place more spawners".

### 7.4 PvP and other places
Stacked enchants do nothing to players, in The Depths (no stacks) or in boss arenas. Their slots are wasted there, which is the intended trade-off: a "farm sword" vs a "Depths sword" (a `/loadout` can swap them in one click).

### 7.5 Island stack book drops (EN15)
Killing stacked mobs with a custom sword drops a Slaughter, Soul Reaper, Bloodlust or Plunder book at 1 in 25,000 per mob killed, **max 5 books per day per profile**. Without the daily cap a big spawner island would print books.

---

## 8. Caps and synergy

### 8.1 Bonus families (EN16)
Every bonus from enchants, masteries, sets, skills, ranks, boosters and events belongs to **one family**. Inside a family the percents **add up**, then the family cap applies once (same math as `MultiplierCap` for selling). The sell family is the owner's **+300%** (#46, O11: all boosts share one cap).

| Family | Enchants | Other sources (synergy) | Cap |
|---|---|---|---|
| **Sell** | Gilded Touch (the 2× of a Gilded item counts as its bonus) | Rank, Sell Multiplier upgrade, Market Contacts mastery, Merchant set, boosters, events | **+300%** (`economy.yml → sell.multiplier-cap-percent`) |
| **Crystals** | Crystal Seeker, Geode Sense, Amber Sap | Crystal Rush mastery, Miner set, crystal boosters | +200% |
| **Extra drops** | Prosperity, Plunder, Double Catch, Harvest Moon, Sunfire Harvest | Tool tier fortune, Timber / Mine Fortune / Harvest Yield / Mob Loot / Beast Tamer masteries, Farmer / Forager sets | +200% (max 3 drops per action) |
| **Speed** | Swiftness, Ember Momentum, Phoenix Haste, Quick Bite | Tool tier + level, Swift Hands mastery, Miner / Forager sets | Hard cap **6 blocks/s** (the balance model's reach cap); fishing wait min 40% of base |
| **Skill XP** | Scholar's Insight, Soul Reaper | XP Boost mastery, rank Progress Boost 2–10%, boosters, events | +300% |
| **Tool XP** | Tempering | Tool XP boosters (if added) | +100% |
| **Rare finds** | Treasure Hunter, Deep Line, Tidecaller | Lucky Strike mastery, Angler mastery, Conqueror set | +150% |
| **Area** | Vein Rend, Dragonfire Blast, Treefeller, Wildfire, Worldbreaker, Ashen Grove | — | One area enchant + one Mythic per tool; by design ≤ +52% throughput (+15% Mythic). Area blocks count toward the speed cap's anti-fast-break check as enchant breaks (§10) |
| **Mob damage** | Phoenix Edge, Giant Slayer | Warrior mastery, Warrior set, Combat skill perks | +200% |
| **Damage reduction** | Fortitude | Warrior set, vanilla armor | max 60% from custom sources |
| **Health** | Vitality | Sets, skill perks | +10 hearts from enchants |

The tool menu and the Enchanter show "capped" in gold when a family is full, so nobody wastes a slot.

### 8.2 Synergy examples (what the systems are built for)
| Build | Pieces | Why it works |
|---|---|---|
| Crystal miner | Pickaxe: Crystal Seeker V, Geode Sense IV, Treasure Hunter III · Crystal Rush mastery · Miner set | Crystals for the Enchanter and island shop; fills the crystal family to the cap late in the season |
| Money miner | Pickaxe: Prosperity V, Gilded Touch V, Vein Rend III · Mine Fortune mastery · Merchant set | Most drops and value per hour; Deep Pockets mastery keeps the satchel from filling |
| Lumberjack | Axe: Treefeller III, Prosperity V, Swiftness V · Timber mastery · Forager set | Area + drops; Timber and Prosperity share the drop family |
| Harvester (V6) | Harvester hoe 7×7: Wide Reap II (9×9), Green Thumb IV, Harvest Moon III · Crop Growth + Harvest Yield masteries · Farmer set | Bigger area × faster regrowth × burst drops |
| Spawner farmer | Sword: Slaughter X, Bloodlust V, Reaper's Sweep III, Plunder IV · Spawner Rate + Mob Loot masteries | Clears 8 stacks per chunk by hand; Beast Tamer for animal stacks |
| Boss hunter | Sword: Phoenix Edge V, Giant Slayer IV, Cleave III, Executioner III · Warrior mastery · Warrior set | Damage share decides boss rewards |
| Tool leveller | Any tool: Tempering V, Scholar's Insight V | Reaches slot 4 and prestige sooner; skill XP unlocks tiers sooner |

### 8.3 PvP normalization (REVIEW A8, EN17)
Warzone and duels use the **PvP** column of §6 (values in `enchants.yml → <id>.pvp`). Default: damage enchants at 40% strength, healing at 50%, AoE (Cleave, Executioner, Phoenix Wrath, stacked enchants) off, armor at 50%, Phoenix Rebirth and movement/vision enchants off. Gathering enchants have no PvP effect. Gem items are never combat stats.

---

## 9. Enchant books as items (EN18)

- **Unique items:** each book (and each Disenchant Scroll) has a PDC id + UUID and a database row (`enchant_books`), like money notes (M3.2). Consuming a book (apply, combine) marks the row `consumed` inside the same transaction. A copied book whose row is already consumed is **confiscated**, staff are alerted and it is audited (dupe detection from M0.7).
- **Tradeable:** `/trade` yes, **auction house yes** (default), dropping/chests yes. AH category "Enchant books" with filters by tool and rarity.
- **Profiles (P4):** books are items in the profile's inventory, so they never move between a player's own profiles. The AH must refuse a purchase from a listing by the same **account** (any profile), or books (and everything else) could hop profiles through the AH.
- **Season reset:** books, scrolls and enchanted tools are seasonal items and are wiped (DESIGN §1).
- **Not from the store:** books, scrolls and crystals are never sold for gems or credits.
- **Lore (UI_STYLE §4.1):**
```
<rarity>Vein Rend II
<muted>Enchant Book · Pickaxe

<text>12% chance to break 4 nearby
<text>ores of yours at 50% yield.

<muted>Details
 <c:enchant>▸ <text>Max level <value>III
 <c:enchant>▸ <text>Slot cost <value>1
 <c:enchant>▸ <text>Conflicts <value>Dragonfire Blast

<action>▶ Use at /enchanter

<rarity><bold>EPIC BOOK
```
- **Tool lore** gets an enchant section generated from data (never edited by hand): `<c:enchant>Vein Rend II`, an empty slot `<muted>▸ Empty slot`, a locked slot `<muted>▸ Slot 4 at Lv 45`, the prestige slot `<c:enchant>★ Worldbreaker`. The perk slot line `Perk slots 3/5` stays from UI_STYLE §4.3.

---

## 10. Anti-abuse

| Risk | Default protection |
|---|---|
| Dupes | Unique book rows, consumed in one transaction (§9); audit on every apply/upgrade/combine/remove/shatter/roll |
| Alts farming books | First finds per account (§3.4); island stack books capped per day; 30-minute new-account limit for the AH (#40) applies to books; same-account AH purchases refused |
| Profile hopping (P4) | Books are profile items; AH same-account block (§9) |
| Area enchants as fast-break bypass | Area/feller breaks are flagged as enchant breaks in the per-player block engine, so the M4.3 anti-fast-break check skips them, but the number of extra blocks per proc is server-side and capped by the enchant |
| Area enchants on islands | Break only blocks the player may break (island protection M2.2), never containers, spawners, other islands or tile entities; generator ores yes |
| Stack farming books | 5 books/day/profile from stacks; collectors/minion kills drop nothing |
| Crystal → item leak | Crystals stay untradeable (CR3); crystal-bought levels can leave as books (accepted, ECONOMY_BALANCE §9.1: "enchanted tools stay tradeable") |
| Pay-to-win | No books/scrolls/crystals for gems or credits; keys earned only; PvP normalized |
| Macro roll spam | Daily roll limits (§3.5), one-shot confirm dialog |
| Exploits found later | Every number in config; `enchants.yml → <id>.enabled: false` turns one enchant off (it stays on items, shows "Disabled" in gray, no effect) |

---

## 11. Bedrock display notes

- **Menus:** every step is a button click; tools and books are picked from lists, never dragged into slots; shift-click is never required; 6-row standard frame with the fixed navigation slots (UI_STYLE §5.1); confirmations use the 3-row dialog.
- **Names:** ≤ 16 characters; level as letters (`Slaughter X`); lore lines ≤ 36 characters (checked by a unit test over `messages/enchants.yml` and the generated lore).
- **Symbols:** only the Bedrock-safe list (UI_STYLE §2.1): `▸` for slots, `★` for the prestige slot, `◆` for crystals, `✔`/`✘` for fits/doesn't fit. No `○`.
- **Colors:** enchant lines use `c:enchant` (`#C77DFF`, legacy fallback light purple in `style.yml`); rarity names use the rarity color, whose first color is the Bedrock color (no gradients on books).
- **Book item:** vanilla `enchanted_book` (glint on Java and Bedrock); vanilla stored enchantments hidden; per-rarity custom models come with the resource pack plan (`docs/plans/RESOURCE_PACK.md`, Geyser mappings), until then the rarity name color carries it.
- **Procs:** action bar line (`Vein Rend · +4 ore`) at most once per second, a short sound (UI_STYLE §7), particles only for the player (and others by the Depths visibility setting). No titles for procs.
- **Effects:** speed via item attribute modifiers (`block_break_speed`, M4.2), health/speed via attributes, night vision/fire resistance via vanilla potion effects: all work on Bedrock without client mods.

---

## 12. Balancing tables

Inputs: `ECONOMY_BALANCE.md` §2.4 (tier, tool level and slots per stage), §8 (drops per played hour per tier) and §9.1 (crystals per played hour). Actions per hour ≈ drops ÷ tool fortune. These are **estimates**; E4 adds the enchant factors to `tools/economy_model.py` so the sheet regenerates them.

### 12.1 Books: expected played hours to the first book of each rarity (mining pool, at that stage's tier)
**Active player** (4 h/day):
| Stage | Played h | Tier | Slots | Books/h | Common | Uncommon | Rare | Epic | Legendary | One specific Epic (e.g. Vein Rend) |
|---|---|---|---|---|---|---|---|---|---|---|
| First hour | 1 | T1 | 0 | 0.95 | 1.5 h* | 4.0 h* | 26 h | — | — | — |
| Day 1 | 5 | T2 | 1 | 1.19 | 1.2 h | 3.2 h | 21 h | — | — | — |
| Day 3 | 13 | T4 | 2 | 1.06 | 2.1 h | 2.7 h | 5.9 h | 24 h | — | — (min tier 5) |
| Week 1 | 29 | T6 | 3 | 1.27 | 1.8 h | 2.3 h | 4.9 h | 20 h | — | 59 h |
| Week 2 | 57 | T8 | 3 | 1.23 | 3.3 h | 2.5 h | 3.0 h | 6.8 h | 27 h | 20 h |
| Month 1 | 121 | T9 | 4 | 1.35 | 3.0 h | 2.2 h | 2.7 h | 6.2 h | 25 h | 19 h |
| Month 2 | 241 | T10 | 5 | 1.28 | 6.5 h | 3.0 h | 2.4 h | 3.5 h | 9.7 h | 11 h |
| Month 3 | 361 | T11 | 5 | 1.37 | 6.1 h | 2.8 h | 2.3 h | 3.3 h | 9.1 h | 10 h |

\* the first Common and Uncommon are guaranteed at the 100th / 1,000th action (~3 min / ~30 min).

**Casual** (1.5 h/day): first Rare ~week 1–2 (band A odds are low; pity and Adept rolls help), first Epic ~month 1 (T5–7), first Legendary ~month 2. **Top** (9 h/day): first Epic ~day 2 (T4–6), first Legendary in week 1–2 (T8).

The Book shop shortens the long waits: a Master Roll (◆ 25,000, tier 7) gives a Legendary 10% of the time and an Epic 50%, so a specific Epic of the mining pool (1 of 3) costs ~150k crystals on average (~120 played hours of an active player's month-1 crystals at a 50% share): a supplement, not the main way. Buying on the AH is the third way.

### 12.2 Crystals: played hours to reach a level (active player, 50% of crystal income spent on enchants)
| Enchant · level | Crystals (total) | Tool Lv needed | First hour | Day 1 | Day 3 | Week 1 | Week 2 | Month 1 | Month 2 | Month 3 |
|---|---|---|---|---|---|---|---|---|---|---|
| Common V-max · I | 50 | 5 | 1.0 h | 0.6 h | 0.3 h | 0.1 h | 0.1 h | — | — | — |
| Common · III | 1,157 | 21 | 24 h | 14 h | 6.4 h | 2.9 h | 1.6 h | 0.9 h | 0.7 h | 0.5 h |
| Common · V | 14,136 | 41 | 288 h | 175 h | 78 h | 36 h | 19 h | 11 h | 8.2 h | 6.0 h |
| Rare V-max · I | 100 | 5 | 2.0 h | 1.2 h | 0.6 h | 0.3 h | 0.1 h | 0.1 h | — | — |
| Rare · III | 2,314 | 21 | 47 h | 29 h | 13 h | 5.8 h | 3.1 h | 1.9 h | 1.3 h | 1.0 h |
| Rare · V | 28,272 | 41 | 577 h | 349 h | 157 h | 71 h | 38 h | 23 h | 16 h | 12 h |
| Epic III-max · I | 150 | 5 | 3.1 h | 1.9 h | 0.8 h | 0.4 h | 0.2 h | 0.1 h | 0.1 h | 0.1 h |
| Epic · II | 2,984 | 17 | 61 h | 37 h | 17 h | 7.5 h | 4.0 h | 2.4 h | 1.7 h | 1.3 h |
| Epic · III | 32,738 | 34 | 668 h | 404 h | 181 h | 83 h | 44 h | 27 h | 19 h | 14 h |
| Legendary III-max · I | 250 | 5 | 5.1 h | 3.1 h | 1.4 h | 0.6 h | 0.3 h | 0.2 h | 0.1 h | 0.1 h |
| Legendary · II | 4,974 | 17 | 102 h | 61 h | 28 h | 13 h | 6.7 h | 4.0 h | 2.9 h | 2.1 h |
| Legendary · III | 54,564 | 34 | 1,114 h | 674 h | 302 h | 138 h | 73 h | 44 h | 32 h | 23 h |
| Slaughter · III | 604 | 11 | 12 h | 7.5 h | 3.3 h | 1.5 h | 0.8 h | 0.5 h | 0.4 h | 0.3 h |
| Slaughter · V | 2,237 | 21 | 46 h | 28 h | 12 h | 5.6 h | 3.0 h | 1.8 h | 1.3 h | 0.9 h |
| Slaughter · VII | 7,528 | 31 | 154 h | 93 h | 42 h | 19 h | 10 h | 6.1 h | 4.4 h | 3.2 h |
| Slaughter · X | 44,506 | 46 | 908 h | 549 h | 247 h | 112 h | 60 h | 36 h | 26 h | 19 h |
| Mythic · I | 79,344 | prestige | — | — | — | — | — | 64 h | 46 h | 34 h |

Read: "at that stage's crystal income". The tool-level gate keeps the early columns honest: on day 1 (tool Lv 12) the cap is level II of a max-V enchant anyway.

**What it feels like (active player):** day 1 = two level-I enchants; week 1 = three slots at levels II–III; week 2 = first Epic and Legendary books arrive, levels III–IV; month 1 = four slots with one maxed; month 2 = prestige slot, first Mythic hunt (world bosses, Eclipse crate); month 3 = everything on the main tools maxed, armor and second tools still growing. **Casual:** 2–3 slots at levels I–III by month 1, one Epic. **Top:** four maxed slots by ~week 2–3, Mythics from bosses.

### 12.3 Throughput check for the balance model (E4)
Effective extra blocks per hour at max level: Vein Rend +48%, Dragonfire Blast +52%, Treefeller +52%, Wildfire +48%, a Mythic +15%, Prosperity +20% drops, Gilded Touch +15% value. The balance sheet today models break rate and slots (`+12% per slot`) but no area factor: **E4 must add an area factor (≤ ×1.5 at endgame, ×1.1–1.25 mid-game) and re-check the income targets**, together with BAL2 (+300% sell cap).

---

## 13. Implementation

### 13.1 Module and data
- **Module:** `modules/enchants` (new; permissions in `modules/enchants/src/main/resources/permissions/enchants.yml`, LuckPerms grants in `server/permissions/enchants.txt`, docs in `docs/commands/enchants.md`).
- **Common API:** `common/enchant/EnchantService` (level of an enchant on an item, slots, proc hooks) and `common/bonus/BonusFamily` + `BonusService` (families and caps from §8.1, reusing `MultiplierCap` math) so masteries, sets, boosters and enchants all report into one place.
- **Configs:** `enchants.yml` (typed: id, rarity, max, tools, slot cost, conflict group, min tier, pools, per-level effect params, PvP params, enabled), `enchanter.yml` (cost steps, rarity factors, fees, rolls, scrolls, drop chances, rarity bands, first-find/pity numbers, daily caps), `messages/enchants.yml` (all names, effect lines, menu text).
- **Tables:** `enchant_books` (book uuid, enchant id, level, kind book/scroll, profile id, origin, state active/consumed/confiscated, created, consumed, reason); applied enchants live in the tool's perks JSON (M4.2 `tools.perks`: slot, id, level, crystals invested); `enchant_first_finds` (account, season, activity, milestone); `enchant_pity` (profile, activity, counter); `enchant_daily` (profile, day, rolls per type, stack books). Migrations via the core migration runner.
- **Audit:** apply, upgrade, combine, remove, shatter, roll, book created (drop source), confiscation.
- **Commands:** `/enchanter` only (planned). Staff testing tools as `/enchanter admin give|apply|clear` subcommands are proposed but **need owner approval** (EN20, open question); until then staff use a menu button in `/admin` if approved, otherwise nothing.

### 13.2 Phase E milestones
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| E1 | Enchant registry (`enchants.yml`, all 48 typed definitions + validation test), enchant books + Disenchant Scrolls as unique DB-backed items (dupe-safe, tradeable, AH category), `/enchanter` GUI (apply, upgrade, combine, remove/shatter, book shop rolls + scrolls, guide), slot/conflict/level-gate rules, crystal costs, tool + book lore, `EnchantService` + `BonusService` in common; simple stat effects (Swiftness, Tempering, Scholar's Insight, Crystal Seeker hooks where tools exist) | M4.2 (perk slots), CRY | new modules/enchants, common/enchant, common/bonus | ⬜ |
| E2 | Book drops: Depths mine/forest/combat hooks, rarity bands by tier, min tiers, first finds + pity, mini/world boss + crate + Black Market hooks (stubs until those exist); gathering effects (Prosperity, Gilded Touch, Geode Sense, Amber Sap, Treasure Hunter, Momentum, Haste) and area enchants through the per-player block engine (enchant-break flag for anti-fast-break); Depths sword effects (Phoenix Edge, Bloodthirst, Ember Strike, Plunder, Cleave, Giant Slayer, Executioner, Phoenix Wrath) + PvP values | E1, M4.7, M4.8, M4.9 | modules/enchants + hook events in modules/depths | ⬜ |
| E3 | Stacked-mob kill enchants: Slaughter, Bloodlust, Reaper's Sweep, Soul Reaper, Plunder per mob, Phoenix Wrath on stacks; batch kill API in stacking; island stack book drops with the daily cap; keep-up table re-checked with real spawner rates | E1, STACK | modules/enchants + kill API in modules/stacking | ⬜ |
| E4 | Tuning: enchant factors (area, drop/crystal families, book rates, roll costs) in `tools/economy_model.py` + `ECONOMY_BALANCE.md` §9; family caps verified in `BonusService` tests; combat/fishing/crop drop chances tuned to ~1–1.4 books/h; dashboard rows (books created/consumed per day, crystals spent at the Enchanter) for M3.8 | E2, E3, BAL2 | docs, tools/economy_model.py, enchants/enchanter configs | ⬜ |
| E5 | Armor enchants on wardrobe set pieces (slots by set level, Vitality … Phoenix Rebirth, PvP values) | E1, wardrobe build (PLAN-SETS) | modules/enchants + wardrobe module API | ⬜ |
| E6 | Hoe + rod effects: harvester-hoe hooks (Wide Reap, Green Thumb, Golden Seed + Golden crop, Harvest Moon, Sunfire Harvest), fishing hooks (Quick Bite, Double Catch, Deep Line, Tidecaller + Tide Chest, Leviathan's Hook); farming/fishing book pools go live | E1, Phase 7 farming/fishing + harvester hoe | modules/enchants + farming/fishing modules | ⬜ |

Lanes are distinct from each other except `modules/enchants`, so E2/E3/E5/E6 run one at a time in that module (or E3 waits for E2 to merge). E1 is the only one that touches common.

### 13.3 Unit tests (required per milestone)
- Cost formula (every row of §5), level gate (§2.4 table), conflict groups, slot cost incl. 2-slot and prestige-only, apply fee and combine fee.
- Book state machine (active → consumed; consumed copy → confiscated) with an in-memory DB; apply transaction rolls back fully on failure.
- Drop rolls: rarity band distribution, min tier filter, first-find milestones, pity counter, daily stack cap (seeded RNG).
- Bonus families: sums and caps; the sell family uses the economy cap.
- Slaughter kill count with Bloodlust and stack-size clamp; Reaper's Sweep target selection.
- Lore lines ≤ 36 characters and names ≤ 16 for every enchant (config validation test).

### 13.4 Owner test checklists
| Milestone | You check (Java + Bedrock) |
|---|---|
| E1 | `/enchanter` opens on both clients; staff-given book applies to a levelled tool; conflicts and "Needs tool Lv" show; upgrade with crystals; combine two books; remove with a scroll returns the book; shatter refunds 20%; a roll gives a book; book lore readable on Bedrock; list a book on the AH |
| E2 | Mine 100 blocks → first Common book; book drops show rarity color; Vein Rend/Treefeller break only your blocks (alt next to you sees nothing change); proc action bar; Depths sword enchants on mobs |
| E3 | A stack of 200 zombies: no enchant kills 1 per hit, Slaughter V kills 8; Reaper's Sweep hits neighbouring stacks; drops arrive in one batch; book cap after 5 |
| E4 | Balance sheet regenerated with enchant factors; crystals/books per hour in the dashboard match the tables |
| E5 | Armor enchants on a wardrobe piece, survive a `/loadout` swap; Phoenix Rebirth triggers once per 10 min, not in the warzone |
| E6 | Harvester hoe with Wide Reap II harvests 9×9; Golden Seed grows; rod enchants on catches |

### 13.5 CHANGELOG line (for the main session)
`docs: custom enchant plan (docs/plans/ENCHANTS.md): 48 enchants, books + /enchanter + crystals, stacked-mob Slaughter, bonus families, milestones E1–E6`

---

## 14. Open questions (defaults apply; also in `OPEN_QUESTIONS.md`)
1. Books unlock, crystals level (default) — or should levels also need books (more trading, slower)? (EN6/EN11)
2. Books on the auction house (default yes) and tradeable everywhere (default yes).
3. Mythics only in the prestige slot / mythic armor slot (default yes). (EN2)
4. Dragonfire Blast and Wildfire cost 2 slots (default yes). (EN3)
5. Tool-level gate `ceil(max × level / 50)` (default yes). (EN4)
6. Remove: Disenchant Scroll keeps the book, shatter refunds 20% (default). (EN13)
7. Book shop rolls up to Legendary 10% for crystals, daily limits 5/5/3 (default). (EN9)
8. Island stack books 1 in 25,000 per mob, max 5 per day per profile (default). (EN15)
9. Family caps (crystals +200%, drops +200%, skill XP +300%, rare finds +150%, mob damage +200%) (default). (EN16)
10. PvP values (damage 40%, healing 50%, AoE and Phoenix Rebirth off) (default). (EN17)
11. Armor slots: 1 per piece, 2nd at set level 25, chestplate mythic slot at 50 (default; final in SETS plan). (EN12)
12. Staff `/enchanter admin give|apply|clear` subcommands for testing (default: add, staff-only, after approval). (EN20)
13. Names (all 48, §6) OK? (default yes)

---

## 15. Defaults register
| Id | Default | Section |
|---|---|---|
| EN1 | Custom tools + wardrobe set pieces only; vanilla gear keeps vanilla enchanting | §2.1 |
| EN2 | Mythics only in the prestige slot / chestplate mythic slot; one per item | §2.2 |
| EN3 | Dragonfire Blast and Wildfire cost 2 slots; everything else 1 | §2.2 |
| EN4 | Level gate `ceil(max × toolLevel / 50)`; prestige = 50; armor by set level | §2.4 |
| EN5 | First finds at 100 / 1,000 actions per account; pity at 2.5× the tier's expected gap | §3.4 |
| EN6 | Activity pools; rarity first, then enchant with equal weights | §3.1 |
| EN7 | Drop chances per action (§3.2 table), target ~1–1.4 books/h everywhere | §3.2 |
| EN8 | Rarity bands A–D; minimum tier per enchant; book level odds | §3.3 |
| EN9 | Rolls ◆ 500 / 4,000 / 25,000, limits 5 / 5 / 3 per day, no Mythics | §3.5 |
| EN10 | No fail chance; apply fee = level-I cost or 25% of the book level's cost | §4.2 |
| EN11 | Combine two equal books (or book + applied) → +1 level for 25% of the new level's cost | §4.4 |
| EN12 | Armor: 1 slot per piece, 2nd at set level 25, chestplate mythic slot at 50 | §2.1 |
| EN13 | Disenchant Scroll ◆ 2,500 returns the book; shatter refunds 20% | §4.5 |
| EN14 | Cost steps `100 × 1.8^(s−1)` spread over 10 steps × rarity factor 0.5–4.0 | §5 |
| EN15 | Island stack books 1 in 25,000, max 5/day/profile, stacked-mob enchants + Plunder only | §7.5 |
| EN16 | Bonus families and caps (§8.1); sell = +300% | §8.1 |
| EN17 | PvP normalization values | §8.3 |
| EN18 | Books unique, tradeable, AH-listable, seasonal, never for gems/credits | §9 |
| EN19 | `enchants.yml → enabled: false` per enchant as a kill switch | §10 |
| EN20 | Staff `/enchanter admin` subcommands only after owner approval | §13.1 |
