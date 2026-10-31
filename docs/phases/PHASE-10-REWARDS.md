# Phase 10: Rewards and Retention

**Goal:** give players a reason to come back every day and a steady stream of small wins. Crates and keys, voting, a daily calendar, quests, a battle pass, achievements, a collection log, the Black Market and the tutorial. Everything here is **earned by playing**. Nothing prints money: all rewards together stay inside the balance sheet's event budget.

Depends on Phases 0–5 (box, economy, crystals, enchants, wardrobe, spawners, island level). Some parts wait for Phase 7–9 content (fishing quests, pet eggs, KOTH for the Conqueror crate); they show "Coming soon" until then.

**Every name, number and rule below is a default** the owner can change. Numbers go into typed configs, text into `messages/<module>.yml`. Unanswered decisions are in the QUESTIONS JSON at the end.

**Sources:** `OWNER_PRINCIPLES.md` (1, 2, 3, 5, 6, 7, 9, 11), `OWNER_ANSWERS_ROUND1.md` (7, 41, 127, 128, 129, 130, 157), `OWNER_ANSWERS_ROUND2.md` (320, 355, 412), `DECISIONS_2026-10-30.md` (P2, P4, P7, P8, O10, O11, V1, R1–R7, E3, S26, CR1–CR3, CA1–CA7, RW1–RW3, SE2, SK3–SK7), `QUESTIONS_ROUND2.md` / `QUESTIONS_ROUND3.md`, `FEATURE_IDEAS.md` (41–47, 49–53, 61, 74, 88–92), `ECONOMY_BALANCE.md` (§1.2, §2.3, §2.7, §7.5, §9.2–9.4, §9.7), `plans/ENCHANTS.md` (§2, crates line), `plans/WARDROBE.md` (set sources), `plans/RESOURCE_PACK_ART_BRIEF.md` (H, I, B32–B40, L35), `plans/RANKS_TITLES_ACHIEVEMENTS.md`, `plans/TUTORIAL.md`, `BUILDER_BRIEF.md` (§5.9 crate yard, §5.10 Tavern), `WORLD.md` (Market Square, Tavern).

---

## Decided already (do not ask again)
- **7 crates** (CA1–CA7): Ember (common), Cinder (uncommon), Blaze (rare), Inferno (epic), Phoenix (legendary), Eclipse (monthly, mythic, new rewards each month), Conqueror (KOTH).
- **Keys are earned only.** Never sold for money or gems (DESIGN §9, 2026-10-29). Keys are virtual and can be withdrawn as items.
- **Keys, key fragments, the daily reward + streak, quests, battle pass, collection log and achievements are per profile** (127, 128, 130, P2). Nothing moves between your own profiles (P4).
- **One vote = one reward, paid to the active profile** (129).
- **Overflow goes to `/box`**, never on the ground (7). Crate rewards use `BoxSource.CRATE`, AFK keys `BoxSource.AFK`.
- **Daily calendar is 30 days.** A broken streak can be saved for 100 store credits within 2 days (R5).
- **Battle pass:** free + premium track, season = 3 months, 100 levels, premium paid with store credits (R1, FI 44).
- **Vote party** exists (R7, FI 53). **Playtime rewards:** 1 h small, 10 h key, 50 h title (R6).
- **Boosters:** same type never stacks, it extends the time; personal and global; all inside the +300% sell cap (R3, R4, O11). `/boosters` and `/thanks` are approved (R4, RW2).
- **Weekly top pays keys** (V1, details in `PHASE-5-PROGRESSION.md` PG27).
- **Island size above 9x9:** gems or the **Black Market** (157). The Black Market is paid in **shards** (CR2, DESIGN §6). Shards come from bosses, envoys, KOTH and high Depths tiers (#43) and are tradeable.
- **Spawners:** tier 3 from the Depths + crates, tier 4 from crates, tier 5 from crates + Black Market (S26, SK5–SK7).
- **Secret sets** may come from completing the collection log (SE2).
- **Crate money must stay small** (BAL2, `ECONOMY_BALANCE.md` §9.4).
- **English only**, vanilla look now, the resource pack retextures crates and menus later (principle 7). Crate preview lore uses the V2 format `★ Name (xN) | chance%` (STYLE2, `messages/items.yml → hologram.*`).
- **No waiting, no busywork** (principle 1): crates open from a menu anywhere, bulk open exists, animations can be skipped, rewards auto-claim.

## What already exists
| Piece | Where | Phase 10 use |
|---|---|---|
| `CrateKeys` service interface | `common/crate/CrateKeys.java` | The crates module implements it. The AFK pool's pending keys are delivered the moment it is published |
| AFK pool keys (1 h Ember, 3 h Ember, 6 h Cinder, 8 h Blaze) | `modules/afk`, `afk.yml → keys.milestones` | Saved as pending grants until crates exist |
| Item box | `modules/box`, `common/box/ItemBox` | Every reward that does not fit |
| Crate yard (6 pedestals, key board hologram, particles) | `server/builds/spawn_crate_yard_v1.yml`, build points | Physical crates. No 7th pedestal for Conqueror yet |
| NPCs + holograms | `modules/npcs` (NPCHOLO) | Crate Keeper, Quest Master, Daily Rewards, Voting, Black Market roles show "help (coming soon)" today. Phase 10 switches them to their commands |
| Enchant books, Disenchant Scrolls | `common/enchant/EnchantService` (`createBook`, `createScroll`) | Crate and Black Market rewards |
| Set Tokens, Wardrobe Tomes | `common/wardrobe/WardrobeService` (`createToken`) | Crate and Black Market rewards |
| Spawner items | `modules/stacking` (`/spawner give`) | Crate and Black Market rewards |
| Tags, titles, skins | `modules/cosmetics`, `modules/tools` | Crate, pass and collection rewards |
| Achievements module (30 starter achievements) | `modules/achievements` | Extended by AC1–AC5 |
| Bonus families + caps | `common/bonus/BonusService` | Boosters go through it |
| Planned commands | `docs/commands/planned.md` | `/crates`, `/vote`, `/daily`, `/quests`, `/pass`, `/blackmarket`, `/collection` |

---

## M10.1 Crates core
**Module:** `modules/crates`. Implements `CrateKeys`.

**Keys**
- Keys live in the database per **profile** and crate, as two counters: **bound** and **tradeable**.
- Bound keys come from sources an alt could farm: votes, the AFK pool, the daily calendar, playtime and monthly rank keys. They can't be withdrawn.
- Tradeable keys come from quests, the battle pass, events, weekly top, key fragments and crate chains. They can be withdrawn as items (M10.4).
- Opening uses bound keys first.
- The AFK pool's pending keys are delivered on start-up (`CrateKeys.giveKeys`). Every grant is one audit row (`crates/key_give`, reason = source).

**Opening (no waiting)**
- `/crates` opens anywhere. You need a key of that crate on your **active profile**.
- **Open 1** plays the animation (M10.3). **Open 10** and **Open all** (up to 64 per click) skip it and show one summary screen.
- Left-click a crate = preview. Right-click = open 1. Shift-click = open 10 (Bedrock: buttons in the preview menu).

**Rolling (server decides first)**
- The result is rolled and saved **before** anything is shown. The animation is only a show.
- One database transaction per open: take the key, bump or reset pity, write `crate_opens`, grant the reward (or queue it into the box). Idempotency key `crate_open:<open id>`. A crash mid-animation never loses or doubles a reward.
- Currency rewards go through the ledger (reason `crate_reward`). Items go to the inventory, the rest to `/box`.
- Unique items (books, tokens, skins, tag items, spawners) are created by their own services with origin `crate:<id>` (dupe detection M0.7).

**Money and crystal pouches scale with your tier**
- Money Pouch = N minutes of `REF(t)` (balance sheet §1.2). `t` = your best Depths tier, the same rule the AFK pool uses. So a pouch feels the same on day 1 and in month 3, and is never a printer.
- Crystal Pouch = base × 1.3^(t−1) (crystals grow ×1.30 per tier, §9.1).
- Examples: 1 min of REF is $833 at tier 1, $42.7k at tier 8 (regular, month 1).

**Duplicates**
- A cosmetic you already own (tag, title, skin, border style) re-rolls into one you don't own from the same crate band. If you own them all, you get its crystal value instead.

**Pity**
- Each crate has a **top band** (entries marked ★ in the tables). A counter per profile and crate counts opens without a top-band reward.
- At the pity number the open is forced into the top band. The preview shows `Pity 12/30`.
- Pity ≈ 2.5 × the average gap, the same rule as enchant books (EN5): it fires in about 8% of gaps.
- Counters never move between profiles and reset at the season reset.

**Config:** `crates.yml` (crates, tables, weights, pity, pouch minutes, top bands). The tables are **catalog lists**: no plain `@Setting` key inside a list section.

## Crate catalog and reward tables

All chances add up to 100% per crate. ★ = top band (counts for pity). "min REF" = minutes of reference income at your tier.

### Overview
| Crate | Rarity | Vanilla block now | Money EV per open | Top band ★ | Pity after | Main key sources |
|---|---|---|---|---|---|---|
| Ember | Common | Barrel | 0.75 min REF | 8% | 30 | votes, AFK pool, calendar, pass |
| Cinder | Uncommon | Chest | 1.6 min REF | 9% | 28 | daily quests, playtime, AFK, calendar, pass |
| Blaze | Rare | Copper chest | 2.7 min REF | 11% | 23 | weekly quests, AFK 8 h, vote streak, calendar, pass |
| Inferno | Epic | Ender chest | 4.5 min REF | 14% | 18 | calendar day 28, vote streak 30, pass 50, weekly top #1 |
| Phoenix | Legendary | Vault | 7.2 min REF | 14% | 18 | pass 100, 3 key fragments, Inferno chain |
| Eclipse | Mythic (monthly) | Ominous vault | 9 min REF | 8% | 10 | calendar day 30, Phoenix chain, Black Market |
| Conqueror | Special (KOTH) | Trial spawner | 4.5 min REF | 12% | 20 | KOTH captures (Phase 9) |

The pack later swaps the vanilla block for the owner's crate model (art brief H08–H14) without code changes.

### Ember Crate (common)
| Reward | Chance |
|---|---|
| Money Pouch (3 min REF) | 25% |
| Crystal Pouch (15 × 1.3^(t−1)) | 20% |
| Island Fuel, 1 h | 15% |
| Common enchant book (random) | 12% |
| Personal booster: Sell +25%, 10 min | 8% |
| Personal booster: Skill XP +25%, 15 min | 7% |
| Common tag item | 5% |
| ★ Cinder key | 3% |
| ★ Uncommon enchant book | 3% |
| ★ Tool skin (common pool) | 1.5% |
| ★ Wardrobe Tome | 0.5% |

### Cinder Crate (uncommon)
| Reward | Chance |
|---|---|
| Money Pouch (8 min REF) | 20% |
| Crystal Pouch (40 × 1.3^(t−1)) | 18% |
| Uncommon enchant book | 14% |
| Island Fuel, 3 h | 10% |
| Personal booster: Sell +50%, 10 min | 8% |
| Disenchant Scroll | 6% |
| Personal booster: Skill XP +50%, 15 min | 6% |
| Uncommon tag or border color | 5% |
| Sell Wand (100 uses) | 4% |
| ★ Blaze key | 3% |
| ★ Tier 3 spawner (random type) | 3% |
| ★ Tool skin (uncommon pool) | 2% |
| ★ Wardrobe Tome | 1% |

### Blaze Crate (rare)
| Reward | Chance |
|---|---|
| Money Pouch (15 min REF) | 18% |
| Crystal Pouch (100 × 1.3^(t−1)) | 16% |
| Rare enchant book | 15% |
| Personal booster: Sell +50%, 30 min | 8% |
| Personal booster: Skill XP +50%, 30 min | 6% |
| Disenchant Scroll | 6% |
| Sell Wand (500 uses) | 5% |
| Auto-sell chest, tier 1 | 5% |
| Rare tag or particle trail | 5% |
| Tier 3 spawner ×2 | 5% |
| ★ Tier 4 spawner | 3% |
| ★ Tool skin (rare pool) | 3% |
| ★ Inferno key | 2% |
| ★ Wardrobe Tome | 1.5% |
| ★ Lorekeeper Robes Set Token (WARDROBE) | 1% |
| ★ Blueprint (a tier you have unlocked) | 0.5% |

### Inferno Crate (epic)
| Reward | Chance |
|---|---|
| Money Pouch (30 min REF) | 15% |
| Crystal Pouch (250 × 1.3^(t−1)) | 14% |
| Epic enchant book | 14% |
| Personal booster: Sell +100%, 30 min | 7% |
| Tier 4 spawner ×2 | 6% |
| Epic tag, kill effect or border style | 6% |
| Personal booster: Skill XP +100%, 30 min | 5% |
| Chunk collector, tier 1 | 5% |
| Auto-sell chest, tier 2 | 4% |
| Sell Wand (2,000 uses) | 4% |
| Tool skin (epic pool) | 4% |
| Island hologram (FI 74) | 2% |
| ★ Greater Wardrobe Tome | 3% |
| ★ Deepforge / Cropwarden / Dreadguard Set Token (1% each) | 3% |
| ★ Thornward / Stormkeel Set Token (1.5% each) | 3% |
| ★ Tier 5 spawner | 2% |
| ★ Phoenix key | 1.5% |
| ★ Blueprint | 1.5% |

### Phoenix Crate (legendary)
| Reward | Chance |
|---|---|
| Legendary enchant book | 14% |
| Money Pouch (60 min REF) | 12% |
| Crystal Pouch (600 × 1.3^(t−1)) | 12% |
| Personal booster: Sell +100%, 60 min | 6% |
| Tier 4 spawner ×4 | 6% |
| Legendary tag or animated border | 6% |
| Tool skin (legendary pool) | 6% |
| Chunk collector, tier 2 | 5% |
| Greater Wardrobe Tome ×2 | 5% |
| Global booster: Sell +50%, 30 min (everyone online, `/thanks`) | 4% |
| Auto-sell chest, tier 3 | 4% |
| Blueprint (rare ability) | 4% |
| Phoenix key (open again) | 2% |
| ★ Tier 5 spawner | 6% |
| ★ Elytra | 3% |
| ★ Voidforged Plate Set Token (WARDROBE) | 2% |
| ★ Legendary title (crate-only) | 2% |
| ★ Eclipse key | 1% |

### Eclipse Crate (monthly, mythic)
The featured rewards change every month (`crates.yml → eclipse.months`). The month's tag never comes back (TT6).
| Reward | Chance |
|---|---|
| Legendary enchant book | 14% |
| Money Pouch (90 min REF) | 10% |
| Crystal Pouch (1,000 × 1.3^(t−1)) | 10% |
| The month's tool skin | 10% |
| The month's tag (never returns) | 10% |
| The month's border + particle theme | 8% |
| Pet egg (hatches when pets exist, Phase 8) | 8% |
| Tier 5 spawner ×2 | 6% |
| Greater Wardrobe Tome ×3 | 5% |
| Chunk collector, tier 3 | 4% |
| Global booster: Sell +50%, 60 min | 4% |
| Auto-sell chest, tier 4 | 3% |
| ★ Phoenix key | 3% |
| ★ Mythic enchant book (ENCHANTS) | 2% |
| ★ Phoenix Regalia Set Token (featured month, WARDROBE) | 2% |
| ★ The month's mythic title | 1% |

### Conqueror Crate (KOTH, Phase 9)
Hidden ("Coming soon") until KOTH exists. The table is ready.
| Reward | Chance |
|---|---|
| Money Pouch (30 min REF) | 15% |
| Shards ×5 | 15% |
| Combat-pool Rare enchant book | 15% |
| Disenchant Scroll | 10% |
| Banneret Tabard Set Token (WARDROBE) | 10% |
| Warzone kill effect | 8% |
| Conquest tag | 6% |
| Tier 4 spawner | 5% |
| Conquest pillar XP pouch | 4% |
| ★ Combat-pool Epic enchant book | 6% |
| ★ Conqueror Aegis Set Token (WARDROBE) | 4% |
| ★ Combat-pool Legendary enchant book | 2% |

## Expected value vs the balance sheet

The balance sheet gives **events, quests and crates together 0.3 × Depths income per played hour** (§1.2), about 18% of income early in the season (§2.3). Phase 9 events need part of it. So Phase 10 aims at **~15% of a regular player's income, with crates ~5%**.

**Keys per day (estimate, `crates.yml` + source configs):**
| Archetype | Ember | Cinder | Blaze | Inferno | Phoenix | Eclipse |
|---|---|---|---|---|---|---|
| Casual (1 h/day, 3 votes, 1 h AFK, half the dailies) | 4.3 | 0.8 | 0.1 | 0.05 | ~0 | 0.03 |
| Regular (3 h/day, 5 votes, 3 h AFK, most quests) | 7.4 | 1.5 | 0.2 | 0.08 | 0.01 | 0.03 |
| Grinder (8 h/day, 5 votes, 8 h AFK, all quests) | 7.4 | 3.1 | 1.2 | 0.08 | 0.03 | 0.03 |

**Money from Phase 10 per day (minutes of REF at the player's tier):**
| Source | Casual | Regular | Grinder |
|---|---|---|---|
| Crates (keys above × money EV) | 5.3 | 9.1 | 14.6 |
| Daily quests (3 × 3 min) | 4.5 | 8.1 | 9 |
| Weekly quests (5 × 8 min ÷ 7) | 1.7 | 4.6 | 5.7 |
| Daily calendar (average) | 1.3 | 1.3 | 1.3 |
| Battle pass free track (average) | 0.9 | 1.8 | 1.8 |
| **Total** | **13.7 min** | **24.9 min** | **32.4 min** |
| **Share of the day's played-hour income** | **~23%** | **~14%** | **~7%** |

- Casual players get relatively more. That is on purpose: fixed daily rewards help them keep up (#61 "fast at first"). In money it is small.
- **Spawners from crates:** a regular finds ~0.1 per day from crates. The model allows 0.1 per played hour from Depths + crates (0.3 per day). Fine.
- **Boosters from crates:** a regular gets ~4% sell booster uptime from crates. The model uses 15% (AFK pool, island shop, votes and crates together). Fine.
- **Crystals** are not tradeable and have big sinks (enchants, sets). Pouches are checked in the M10.13 model run.
- **Levers if the dashboard disagrees:** `pouch-minutes` per crate, quest `money-minutes`, vote party size, key amounts per source. Each is one config line.

---

## M10.2 Boosters (`/boosters`, `/thanks`, approved R4)
**Module:** `modules/boosters`.
- **Personal boosters** are bound items (from crates, the calendar, quests, the AFK pool). Right-click to start. Types: Sell, Skill XP, Tool XP, Crystals. Strength +25 / 50 / 100%, 10–60 min.
- Same type does not stack: it adds time (R3). Max banked time per type 3 h.
- **Global boosters** (Phoenix/Eclipse crates, store later): everyone online gets it. Boss bar countdown. `/thanks` or saying "thanks" in chat gives the starter 1 gem, once per booster per player (RW2).
- Everything goes through `BonusService`, so the +300% sell cap and the family caps always apply (O11).
- The AFK pool's banked sell booster moves here (one place for all boosters).
- `/boosters` shows active and banked boosters, personal and global.

## M10.3 Physical crates and animations (vanilla now)
- The crates spawn on the crate yard pedestals from build points (`crates: common … monthly`). Real vanilla blocks (overview table), so Bedrock sees them too.
- Hologram above each crate: name in its rarity color, `Click: Open`, `Sneak-click: View rewards` (UI_STYLE §6.4b, UX3: a plain click opens, texts never say right-click). Without a key a click shows the rewards and how to get keys. The key board hologram lists how to get keys and the top-band odds of every crate.
- **GUI animation** (B40 spinner): items scroll through slots 10–16 and slow down onto slot 13, ~3 s, soft tick sound. Click to skip. `/settings → Fast crate opening` skips it always.
- **In-world animation** when opened at a pedestal: rarity particles, the reward floats up above the crate with its name for 3 s (everyone nearby sees it), firework for ★.
- **Win message:** ★ rewards from Inferno and higher go to global chat (one line, hover shows the item). Everything else only to the opener.
- **Bedrock:** the spinner is a normal chest menu (≤ 20 updates). Display entities may not show on Bedrock, so the result also comes as a chat line and a title.
- **Conqueror:** the crate yard has 6 pedestals. The builder adds a 7th (or a spot at the warzone gate) before KOTH launches. Until then Conqueror is only in `/crates`.

## M10.4 Key items and fragments
- **Withdraw:** in `/crates`, tradeable keys become key items (unique id, dupe detection). Right-click a key item to put it back on your active profile.
- A key item remembers who withdrew it. **It can't be redeemed on another profile of the same account** (P4). Other players can redeem it.
- Tradeable key items can be traded and listed on the AH (they are behind the tutorial's `/ah` and `/trade` locks).
- **Key fragments** (art L35): rare drop in Depths tiers 11+ (chance in `depths.yml`, today 0). Lucky Strike (MA7) raises it. **3 fragments = 1 Phoenix key** in `/crates`.

## M10.5 Voting (`/vote`)
**Module:** `modules/rewards` (votes, daily calendar, playtime).
- **Receiver:** our own Votifier receiver (DESIGN §11), NuVotifier protocol v2 (token per site) and v1 (RSA). Port and keys in `votes.yml`.
- **Sites (default, owner registers the server):** PlanetMinecraft, Minecraft-MP, MinecraftServers.org, TopG, Minecraft-Server-List. Name, URL and service name per site in `votes.yml`. The vote page `skybornmc.net/vote` (320) links them all.
- **Per vote:** 1 bound Ember key + 3 gems (§9.2) + 25 pass XP. One reward per site per account per 24 h (site rules).
- **Paid to the active profile** (129). Offline: queued and paid to the profile you are on at your next join. Unknown names wait 7 days.
- **Bedrock names:** Floodgate adds a prefix (for example `.Name`). Vote sites often drop it. Look up the name with and without the prefix.
- **Vote streak** (days with at least one vote): day 7 Cinder, 14 Blaze, 21 Cinder, 30 Inferno, then the cycle repeats. A missed day resets it.
- **Vote party:** a server-wide counter. When it fills, everyone online who voted today, finished the tutorial and is not AFK gets the party reward. Counter on the Tavern Vote NPC hologram and in `/vote`. Size and reward: see questions (100 votes ≈ 15 parties a day at our size).
- `/vote` today is a link command in `modules/social`. It becomes the vote menu: one button per site (link in chat, URL in the lore for Bedrock), today's votes ✔/✘, streak, party progress.

## M10.6 Daily rewards and playtime (`/daily`)
**Daily calendar (per profile, 128)**
- Claim once per day (reset 00:00 Europe/Oslo) at the Daily Rewards NPC or `/daily`.
- The calendar day moves forward **only when you claim**. Missing a day never sends you back.
- The **streak** counts days in a row. Every 7 streak days = 10 gems (§9.2). Titles at streak 30 and 100.
- A missed day breaks the streak. You can save it within 2 days for 100 store credits (R5). The save button stays hidden until the store exists (Phase 11).
- All calendar keys and boosters are **bound**.

| Day | Reward | Day | Reward | Day | Reward |
|---|---|---|---|---|---|
| 1 | Money 3 min REF | 11 | Money 5 min | 21 | Cinder key ×2 |
| 2 | Crystal Pouch S | 12 | Island Fuel 4 h | 22 | Money 8 min |
| 3 | Ember key | 13 | Crystal Pouch M | 23 | Island Fuel 6 h |
| 4 | Island Fuel 2 h | 14 | Blaze key | 24 | Ember key ×2 |
| 5 | Money 5 min | 15 | Common enchant book | 25 | Wardrobe Tome |
| 6 | Sell booster +25%, 15 min | 16 | Money 8 min | 26 | Crystal Pouch L |
| 7 | Cinder key | 17 | Ember key | 27 | Skill XP booster +50%, 30 min |
| 8 | Crystal Pouch M | 18 | Sell booster +50%, 15 min | 28 | Inferno key |
| 9 | Skill XP booster +25%, 15 min | 19 | Crystal Pouch L | 29 | Money 10 min |
| 10 | Ember key ×2 | 20 | Disenchant Scroll | 30 | Eclipse key |

Crystal Pouch S / M / L = 20 / 40 / 80 × 1.3^(t−1). After day 30 the calendar starts again at day 1.

**Playtime rewards (R6, per profile)**
- Only **active** time counts: not AFK (no action for 5 min) and not in the AFK pool.
- Every hour: Crystal Pouch S + 20 pass XP (no money, it would add up too fast).
- Every 10 hours: 1 bound Cinder key.
- Titles at 50 h, 100 h, 250 h and 500 h (account titles, PR1).
- Shown in `/daily` → Playtime tab (no new command).

## M10.7 Quests (`/quests`, Tavern)
**Module:** `modules/quests`. Quest Master NPC in the Tavern (412) opens `/quests`. The quest board (7 × 5) at the Tavern shows **your** quests (per-viewer hologram).
- **Daily:** 3 quests per profile per day, picked from a pool. Targets scale with your tier so each takes ~10–15 min. 1 free reroll per day. Reward each: money 3 min REF + crystals + 100 pass XP. **All 3 done = 1 Cinder key** (tradeable).
- **Weekly:** 5 quests, reset Monday 00:00 Oslo, ~45 min each. Reward each: money 8 min REF + crystals + 400 pass XP. **All 5 done = 1 Blaze key** (tradeable).
- **Island missions (CR1, FI 41):** 3 per island per week, shared progress by members (not coop). Every member who helped gets crystals (+20% island missions in the model) + 150 pass XP.
- **Only live content:** a quest type is in the pool only when its system exists and you have it unlocked (no fishing quests before fishing, no tier 6 quest at tier 3).
- Progress comes from server events (block break, kill, sell, forge …), never from the client. Action bar at every 25% and a toast on completion. Optional scoreboard objective line `▸ Mine 400 Iron Ore 230/400` (UI_STYLE).
- Rewards auto-claim on completion.

**Quest types (pool, `quests.yml`)**
| Type | Example | Live from |
|---|---|---|
| Mine / chop / kill in a tier | Mine 400 Iron Ore | now (Depths) |
| Sell for an amount | Sell $500k of Depths items | now |
| Level a tool | Gain 3 tool levels | now |
| Forge | Forge any tool | now |
| Enchant | Add or raise an enchant | now (enchants) |
| Visit / like islands | Visit 3 islands, like 1 | now |
| Island work | Harvest 500 crops, kill 200 spawner mobs | Phase 7 |
| Fishing | Catch 30 fish | Phase 8 |
| Boss / KOTH / envoy | Deal damage to the world boss | Phase 8–9 |

## M10.8 Battle pass (`/pass`)
**Module:** `modules/pass`.
- **Season:** 90 days (R1), 100 levels, **flat 800 pass XP per level** (no wall at the end).
- **Pass XP:** daily quest 100, weekly quest 400, island mission 150, vote 25, calendar claim 50, active hour 20, achievement tier 50, events later.
- Regular (~820 XP/day) reaches ~level 95–100 by season end, a grinder ~day 70, a casual ~55.
- **Free track (per profile):** a small reward every level (money 2 min REF, crystals or 1 h island fuel, rotating). Keys every 5 levels: Ember ×2 at 5, 15, 35 …; Cinder at 10, 20, 30, 40, 60, 70, 80, 90; Blaze at 25 and 75; Inferno at 50; Phoenix at 100. Titles at 50 and 100. Pass keys are tradeable.
- **Premium track:** bought with store credits (Phase 11). Default contents: a cosmetic every 5 levels (tags, titles, border styles, trails, tool skins, kill effects) and a premium skin + animated title at 100, plus 1 extra daily quest reroll. **No keys, boosters or currencies on premium**, so nothing random and no progress is paid for.
- **Premium is account-wide** for that season. Cosmetics are account-wide (P2), so each premium cosmetic is given once per account, on whichever profile reaches the level first. The free track stays per profile.
- The top donor rank does **not** include premium (RK26).
- **Rewards auto-claim** on level-up with a toast. No claim buttons.
- **After level 100:** every 2,000 XP gives an overflow reward (Crystal Pouch M + 1 bound Ember key).
- **Catch-up (late joiners):** +50% pass XP while you are 15+ levels behind the day line (day of season × 1.1).
- Rewards per season in `pass.yml → seasons.<id>`. Staff can grant premium for testing.

## M10.9 Achievements
Fully planned in [`plans/RANKS_TITLES_ACHIEVEMENTS.md`](../plans/RANKS_TITLES_ACHIEVEMENTS.md) §C (personal + island achievements, boosts with caps, titles) and its milestones **AC1–AC5**. Phase 10 only adds: each achievement tier gives 50 pass XP, and achievements never give keys or currency (AC4).

## M10.10 Collection log (`/collection`)
**Module:** `modules/collection`. Per profile (130).
- **An entry unlocks the first time you get the item yourself:** a drop, a crate open, a quest, a calendar reward or a Black Market buy. Trades, AH buys and `/pay` never count, so a log can't be bought.
- **Pages:** Mine (per tier: ores, rare finds), Forest, Combat (drops per tier), Blueprints, Enchant books, each crate's rewards, Black Market, Farming (Phase 7), Fishing (Phase 8), Bosses (Phase 8), Events (Phase 9), Pets (Phase 8). Unknown entries show `???`.
- The enchant guide in `/enchanter` stays account-wide (E1). The log's book page is per profile.
- **Rewards:**
  - Page milestones 25 / 50 / 75 / 100% → collection points, the "Collections" source of island level (PLAN5, 1 point per milestone).
  - A full page → page title + 1 Blaze key.
  - The whole log → title **Keeper of Ashes** (mythic, TT) + 1 Phoenix key + the secret set (SE2).
- `/menu` hub: the "collection" slot (coming soon today) becomes the log.
- Season reset: the log goes with the profile. The best % per season is saved for history.

## M10.11 Black Market (`/blackmarket`)
**Module:** `modules/blackmarket`. A shady trader NPC in the Market Square alley (WORLD.md). Always open: no waiting, but the stock **rotates**.
- **Currency: shards only.** Gems stay in the gem shop (Phase 11), money items stay in `/shop`. Shards are earned (bosses, envoys, KOTH, tier 9+), so the stock is limited by events, not by grinding or paying.
- **Rotation:** every 12 h (00:00 and 12:00 Oslo). 8 slots: 1 headline, 3 rare, 4 common, picked by weight from `blackmarket.yml`. A chat line announces the headline. A hologram in the alley shows the time left.
- **Fixed row (always in stock):** Island Charter (next size step) and Disenchant Scroll.
- **Limits:** per account per rotation. Phoenix Regalia 1 per account per season (WARDROBE). Eclipse key 1 per account per month. The headline has a server-wide stock (25 per rotation) so it feels rare.
- Every buy is one transaction: shards out, item in, `black_market_buys` row, audit `blackmarket/buy`. Items go to the inventory or `/box`.

| Item | Slot | Shards | Note |
|---|---|---|---|
| Island Charter 11x11 / 13x13 / 15x15 | fixed | 150 / 300 / 450 | One step at a time, 9x9 first, season only (PG14). Gem price 250 / 500 / 750 (M5.6) |
| Disenchant Scroll | fixed | 8 | Same as the Book shop ◆ 2,500 path |
| Phoenix Regalia Set Token | headline | 400 | 1 per account per season |
| Voidforged Plate Set Token | headline | 250 | |
| Mythic enchant book (random) | headline | 200 | ENCHANTS: Eclipse, bosses, Black Market only |
| Coinmaster Silks Set Token | rare | 120 | WARDROBE "Black Market rotation" |
| Chunk collector, tier 3 | rare | 100 | |
| Auto-sell chest, tier 4 | rare | 120 | |
| Eclipse key | rare | 75 | 1 per account per month |
| Legendary enchant book | rare | 60 | |
| Phoenix key | rare | 50 | 2 per account per rotation |
| Tier 5 spawner | rare | 40 | S26 |
| Blueprint (random) | common | 40 | |
| Black Market tag (only here) | common | 60 | Cosmetic, account-wide |
| Elytra | common | 30 | WORLD: crates/Black Market/events |
| Sell Wand (2,000 uses) | common | 25 | |
| Greater Wardrobe Tome | common | 20 | |

A late-season regular earns roughly 5–10 shards a day (tier 9+ drops plus some events), so a headline item is a 1–2 month goal and a common item a few days.

## M10.12 Tutorial
Fully planned in [`plans/TUTORIAL.md`](../plans/TUTORIAL.md) (join flow, 11 core steps, locks, starter chest, milestones **TU1–TU8**). TU1–TU4 are being built now. Phase 10 adds:
- **Finish reward:** + 1 bound Ember key, so the first crate is opened in the first minutes.
- **Optional step O10 `rewards`:** at the Tavern, claim the daily reward, see the vote links, take your first quests.
- **Optional step O11 `crates`:** at the crate yard, open the Ember key from the finish reward.

## M10.13 Balance run
- Add keys, crate tables, quests, calendar, votes, pass and playtime to `tools/economy_model.py` and `ECONOMY_BALANCE.md` (new §9.11).
- Check: Phase 10 money ≤ ~15% of a regular's income, crates ≤ ~5%; spawner and booster supply within §1.2; shard supply vs Black Market prices; crystal pouches vs §9.10 sinks.
- Dashboard sources (M3.8): `crate_reward`, `quest_reward`, `daily_reward`, `vote_reward`, `pass_reward`.

## M10.14 Menus, help and NPCs
- `/menu` hub: Crates, Quests, Daily, Vote, Pass, Collection, Black Market replace the "coming soon" entries (hidden at launch until finished, owner 41).
- NPC roles switch from help to their commands: crates → `/crates`, quests → `/quests`, daily → `/daily`, vote → `/vote`, black_market → `/blackmarket` (`npcs.yml → npcs.actions`).
- `/help` topics for every new command. The key board hologram text comes from `messages/crates.yml`.

---

## Anti-abuse
| Risk | Rule |
|---|---|
| Alts farm free keys and pass them to a main | Keys from votes, AFK, calendar, playtime and rank claims are **bound**. Only quest, pass, event and fragment keys trade, and only after the tutorial unlocks `/trade` and `/ah` |
| Moving value between own profiles (P4) | Keys, pity, calendar, quests and pass are per profile. A key item can't be redeemed on another profile of the account that withdrew it. Cosmetics are account-wide anyway |
| Many accounts on one IP vote | Vote rewards for max 3 accounts per IP per day (owner rule 292). Vote party only for players who voted today and are active |
| AFK playtime farming | Playtime counts only active time outside the AFK pool. AFK pool stays one earning account per IP |
| Fake votes | Votifier v2 tokens per site, one reward per site per account per 24 h, unknown names expire after 7 days |
| Duped key items | Unique ids + dupe detection (M0.7); copies are confiscated and staff are told |
| Reward lost or doubled on crash | Roll saved first, one transaction per open, idempotency key, box overflow |
| Rigged odds claims | All odds visible in the preview and on the key board; every roll is logged (`crate_opens`), staff can show a player's history |
| Quest cheating | Progress only from server events; Depths fast-break detection already gives no reward (#57) |
| Black Market sniping by bots | Per-account limits; the headline stock is the only shared stock |
| Pass XP from alts | Pass XP sources are per profile; nothing transfers |

Every data change is audited: `crates/*`, `boosters/*`, `rewards/vote|daily|playtime`, `quests/*`, `pass/*`, `collection/unlock`, `blackmarket/*`.

---

## UI mockups

### `/crates`
```
Row 1  red frame · [your keys: Ember 12 (3 tradeable)] · red frame
Row 2  [Ember] [Cinder] [Blaze] [Inferno] [Phoenix] [Eclipse] [Conqueror: Coming soon]
       lore: "Keys 12 · Pity 7/30", "» LEFT-CLICK Preview", "» RIGHT-CLICK Open 1", "» SHIFT-CLICK Open 10"
Row 3  [Withdraw keys] [Combine fragments 2/3] [Fast opening: off]
Row 6  standard navigation
```

### Crate preview (Blaze)
```
Rows 1–4  every reward as its item, lore "★ Tier 4 Spawner (x1) | 3%"
Row 5     [Open 1] [Open 10] [Open all]   Pity 7/23 · Keys 4
Row 6     standard navigation
```

### `/daily`
```
Rows 1–4  30 days as items: claimed (green glass ✔), today (gold, "» CLICK Claim"), next days (gray, reward in lore)
Row 5     [Streak 12 days · next gems in 2] [Playtime tab] [Save streak: hidden until the store]
Row 6     standard navigation
```

### `/quests`
```
Row 1  tabs: [Daily] [Weekly] [Island] [Pass]
Row 2  3 daily quests: progress bar in lore, reward lines "[+] $ · ◆ · 100 pass XP"
Row 3  [Reroll (1 left)]  [All 3 done: Cinder key]
Row 6  standard navigation
```

### `/pass`
```
Row 1  Level 34 · 420/800 XP · Premium: no
Row 2  free track: 7 levels around your level (claimed ✔ / next / locked)
Row 3  premium track: same 7 levels (locked padlock without premium)
Row 6  [◀ levels] [▶ levels] navigation
```

### `/blackmarket`
```
Row 1  dark frame · "Restocks in 4h 12m" · dark frame
Row 2  [headline item, "Stock 18/25"]
Row 3  3 rare items · Row 4 4 common items
Row 5  fixed: [Island Charter 13x13 · 300 shards] [Disenchant Scroll · 8 shards]
Row 6  [Your shards: 142] navigation
```

## Data (tables)
Column names avoid MariaDB reserved words (`key`, `rows`, `order`, `rank`, `range` …; `SqlReservedWordsTest`).

| Table | Columns (short) |
|---|---|
| `crate_keys` | profile_key, crate_id, bound_amount, tradeable_amount |
| `crate_pity` | profile_key, crate_id, opens_since_top, season_id |
| `crate_opens` | open_id, profile_key, crate_id, reward_id, top_band, opened_at, season_id |
| `crate_key_items` | item_uuid, crate_id, origin_account, origin_profile, state |
| `crate_fragments` | profile_key, fragment_count |
| `boosters` | booster_id, owner_profile, booster_type, strength, minutes_left, scope, started_at |
| `vote_log` | vote_id, account_id, site_id, voted_at, paid_profile, state |
| `vote_streaks` | account_id, streak_days, last_vote_date |
| `vote_party` | party_id, counter, target, filled_at |
| `daily_calendar` | profile_key, calendar_day, last_claim_date, streak_days, streak_broken_on |
| `playtime_rewards` | profile_key, active_minutes, hours_paid, tens_paid |
| `quest_progress` | profile_key, quest_id, quest_kind, progress, target, period_start, done_at |
| `island_missions` | island_id, mission_id, progress, target, week_start |
| `pass_progress` | profile_key, season_id, pass_xp, pass_level |
| `pass_premium` | account_id, season_id, bought_at, source |
| `pass_cosmetic_claims` | account_id, season_id, reward_level |
| `collection_entries` | profile_key, entry_id, first_at, source |
| `black_market_rotation` | rotation_id, slot_index, item_id, price_shards, global_left, starts_at |
| `black_market_buys` | account_id, profile_key, rotation_id, item_id, amount, bought_at |

## Config files
`crates.yml`, `boosters.yml`, `votes.yml`, `daily.yml`, `quests.yml`, `pass.yml`, `collection.yml`, `blackmarket.yml`. Catalog lists (crate tables, calendar days, quest pool, pass rewards, Black Market items) never hold plain `@Setting` keys inside the list section.

## Modules, commands and permissions
| Module | Commands | Status |
|---|---|---|
| `crates` | `/crates` | planned (`planned.md`) |
| `boosters` | `/boosters`, `/thanks` | approved (R4, RW2) |
| `rewards` | `/vote` (moved from `social`), `/daily` | planned |
| `quests` | `/quests` | planned |
| `pass` | `/pass` | planned |
| `collection` | `/collection` | planned |
| `blackmarket` | `/blackmarket` | planned |

- Staff sub-commands (`/crates admin`, `/vote admin test`, `/quests admin`, `/pass admin`, `/blackmarket admin`) need the owner's OK first (see questions).
- Each module: `permissions/<m>.yml`, `server/permissions/<m>.txt`, `docs/commands/<m>.md`, one line each in `settings.gradle.kts`, `plugin/build.gradle.kts`, `SkyblockPlugin.registerModules` (CLAUDE.md "Adding a module").
- Tab completion on every command and argument. No required argument next to sub-commands.

## Milestones
| # | Milestone | Depends on | Lane |
|---|---|---|---|
| **M10.1** | Crates core: `CrateKeys` implementation (delivers AFK pending keys), keys per profile (bound/tradeable), `crates.yml` with the 7 tables, roll engine + pity + duplicate re-roll (pure, unit tested), `/crates` + preview + open 1/10/all, atomic open, box overflow, audit | box, economy, enchants, wardrobe, stacking, cosmetics | modules/crates |
| **M10.2** | Boosters: personal items + global, `/boosters`, `/thanks`, `BonusService` wiring, AFK booster moved here | M10.1 (crate rewards), afk | modules/boosters |
| **M10.3** | Physical crates on the crate yard pedestals, holograms, GUI spinner, in-world animation, win broadcasts, Fast opening setting | M10.1, NPCHOLO | modules/crates |
| **M10.4** | Key withdraw/redeem items, cross-profile guard, key fragments (enable the Depths chance) + combine | M10.1, M4.9 | modules/crates, depths config |
| **M10.5** | Voting: Votifier receiver, sites, rewards, streaks, vote party, offline queue, `/vote` menu (moved from social) | M10.1 | modules/rewards, social |
| **M10.6** | Daily calendar (per profile), streak + gems, save button (hidden until the store), playtime rewards | M10.1, M10.2 | modules/rewards |
| **M10.7** | Quests: engine + hooks, daily/weekly/island missions, `/quests`, Tavern NPC + quest board, scoreboard line | M10.1, Phase 5 (island data) | modules/quests + small hooks |
| **M10.8** | Battle pass: XP sources, free/premium tracks, auto-claim, overflow, catch-up, staff premium grant (store hook later) | M10.7 | modules/pass |
| **M10.9** | Achievements AC1–AC5 (`RANKS_TITLES_ACHIEVEMENTS.md` §D) + 50 pass XP per tier | M10.8 for XP, PLAN5 for island part | modules/achievements |
| **M10.10** | Collection log: hooks, pages, rewards, island level source, Keeper of Ashes + secret set hook | M10.1, PLAN5 | modules/collection |
| **M10.11** | Black Market: rotation, fixed row, limits, shard payments, Island Charter (M5.6 hook), alley NPC + hologram | M10.1, shard sources (Phase 8–9; staff grant until then) | modules/blackmarket |
| **M10.12** | Tutorial TU1–TU8 (`TUTORIAL.md` §11) + O10 rewards, O11 crates, finish key | TU4, M10.1, M10.6, M10.7 | modules/tutorial |
| **M10.13** | Balance run (model + `ECONOMY_BALANCE.md` §9.11), dashboard sources | M10.1–M10.11 | docs, tools |
| **M10.14** | `/menu` entries, `/help` topics, NPC actions, rank monthly keys (RK5) | M10.1–M10.11, RK3 | menus, npcs, perks |

Order: M10.1 → M10.2/M10.3/M10.5 in parallel → M10.4, M10.6, M10.7 → M10.8 → M10.9/M10.10/M10.11 → M10.12–M10.14.

## Release / test checklists (owner, Java + Bedrock each)
| Milestone | You check |
|---|---|
| M10.1 | AFK keys arrive after the update; `/crates` shows keys; preview odds add up; open 1, 10, all; full inventory → `/box`; pity counter moves and fires |
| M10.2 | Start a booster item; same type adds time; global booster boss bar; `/thanks` gives 1 gem once |
| M10.3 | Crates at the crate yard; holograms; spinner and skip; in-world reward float; ★ win in global chat |
| M10.4 | Withdraw a tradeable key, trade it to an alt, alt redeems; your other profile can't redeem it; 3 fragments → Phoenix key |
| M10.5 | Test vote → key + gems; offline vote paid at join; streak day count; party fills and pays only active voters; Bedrock name with prefix |
| M10.6 | Claim day 1; next day day 2; skip a day → calendar stays, streak resets; profile 2 has its own calendar; playtime hour reward |
| M10.7 | 3 dailies match your tier; reroll; progress on action bar; all 3 → Cinder key; weekly reset Monday |
| M10.8 | Pass XP from a quest; level-up auto-claims; premium (staff grant) cosmetics once per account |
| M10.10 | First drop unlocks an entry; AH buy does not; page milestone adds island level points |
| M10.11 | Rotation at 00:00/12:00; buy with shards; per-account limit; Island Charter after 9x9 |
| M10.12 | Finish the tutorial → Ember key; O10 and O11 work |

---

## QUESTIONS (for the one-click page)

Only decisions that are not answered yet. Fields: `q` question, `s` suggested answer, `why` one-line reason, `p` 1 = important, 0 = detail.

```json
[
  {"q": "RX1: Use the 7 crate reward tables in PHASE-10-REWARDS.md, with money pouches scaled to your Depths tier (Ember 3 min of income up to Eclipse 90 min)?", "s": "Yes", "why": "Crates feel the same all season and stay around 5% of a player's income, so they never print money.", "p": 1},
  {"q": "RX2: Pity per profile and crate: a top-band reward is guaranteed after about 2.5x the average gap (Ember 30 opens ... Eclipse 10), shown in the preview, reset each season?", "s": "Yes", "why": "Same rule as enchant books; bad luck streaks end without changing the average much.", "p": 1},
  {"q": "RX3: A cosmetic you already own re-rolls into one you don't own from the same band, or gives crystals if you own them all?", "s": "Yes", "why": "Nobody wins a useless duplicate.", "p": 0},
  {"q": "RX4: Open crates from /crates anywhere; physical crates at spawn are for the show; Open 10 and Open all skip the animation?", "s": "Yes", "why": "No walking or waiting just to open keys (your rule 1), and the crate yard still feels alive.", "p": 1},
  {"q": "RX5: Crate animation ~3 s, click to skip, plus a 'Fast crate opening' setting?", "s": "Yes", "why": "Fun the first time, never a chore.", "p": 0},
  {"q": "RX6: Keys from votes, AFK pool, daily calendar, playtime and rank claims are bound (no withdraw, no trade); keys from quests, battle pass, events, weekly top and fragments can be withdrawn, traded and sold on the AH?", "s": "Yes", "why": "Alts can't farm free keys for a main, but earned keys still have a market.", "p": 1},
  {"q": "RX7: A withdrawn key item can't be redeemed on another profile of the same account?", "s": "Yes", "why": "Your rule P4: nothing moves between your own profiles.", "p": 0},
  {"q": "RX8: 3 key fragments (Depths tier 11+) combine into 1 Phoenix key?", "s": "Yes", "why": "Gives the deepest tiers a legendary chase; Lucky Strike makes fragments more common.", "p": 0},
  {"q": "RX9: The Conqueror crate stays hidden until KOTH exists, and the builder adds a 7th pedestal (the crate yard has 6)?", "s": "Yes", "why": "An empty crate with no key source confuses players.", "p": 0},
  {"q": "RX10: Eclipse keys are not tied to a month: a key opens whatever month's Eclipse crate is live when you use it?", "s": "Yes", "why": "Simple and fair; players never lose a key at the month change.", "p": 1},
  {"q": "RX11: Vanilla crate blocks until the pack: Ember barrel, Cinder chest, Blaze copper chest, Inferno ender chest, Phoenix vault, Eclipse ominous vault, Conqueror trial spawner?", "s": "Yes", "why": "Real blocks show on Bedrock too and get your textures later.", "p": 0},
  {"q": "RX12: Top-band wins from Inferno, Phoenix, Eclipse and Conqueror go to global chat; all other wins only to the opener?", "s": "Yes", "why": "Big moments are public without spamming chat.", "p": 0},
  {"q": "RX13: Each vote gives 1 bound Ember key + 3 gems + 25 pass XP, on 5 sites (PlanetMinecraft, Minecraft-MP, MinecraftServers.org, TopG, Minecraft-Server-List)?", "s": "Yes", "why": "Matches the balance sheet's 15 gems a day from votes; sites all support Votifier.", "p": 1},
  {"q": "RX14: Vote streak rewards: day 7 Cinder, 14 Blaze, 21 Cinder, 30 Inferno, then repeat; a missed day resets the streak?", "s": "Yes", "why": "Rewards loyal voters without big money.", "p": 0},
  {"q": "RX15: Vote party: 100 votes would fill ~15 times a day at our size (15 Rare keys per player daily). Change it to 500 votes, Blaze key, only for players online who voted today and aren't AFK?", "s": "Yes", "why": "About 3 parties a day keeps the party special and Blaze keys rare.", "p": 1},
  {"q": "RX16: Vote rewards for at most 3 accounts per IP per day?", "s": "Yes", "why": "Same limit you chose for likes and ratings; stops alt vote farms.", "p": 0},
  {"q": "RX17: Daily calendar contents as listed (keys on days 3, 7, 10, 14, 17, 21, 24, 28 Inferno, 30 Eclipse; money, crystals, fuel and boosters on the other days)?", "s": "Yes", "why": "A key every few days and a big one at the end of each month.", "p": 1},
  {"q": "RX18: The calendar only moves forward when you claim (missing a day never sends you back); the streak gives 10 gems every 7 days and resets when broken?", "s": "Yes", "why": "Missing a day hurts the streak, never the calendar; matches your 100-credit streak save.", "p": 0},
  {"q": "RX19: Daily reset for calendar, quests and votes at 00:00 Europe/Oslo; weekly reset Monday 00:00?", "s": "Yes", "why": "The server already uses Oslo time.", "p": 0},
  {"q": "RX20: Playtime rewards count only active time outside the AFK pool: every hour crystals + pass XP, every 10 h a Cinder key, titles at 50, 100, 250 and 500 h, per profile?", "s": "Yes", "why": "Your R6 answer, made safe from AFK farming; no hourly money so it can't add up.", "p": 0},
  {"q": "RX21: Daily quests: 3 per day scaled to your tier, 1 free reroll, each 3 min of income + crystals + 100 pass XP, all 3 done = Cinder key?", "s": "Yes", "why": "About 30-45 minutes of goals a day with a key at the end.", "p": 1},
  {"q": "RX22: Weekly quests: 5 per week, each 8 min of income + crystals + 400 pass XP, all 5 done = Blaze key?", "s": "Yes", "why": "A bigger weekly goal that drives battle pass progress.", "p": 1},
  {"q": "RX23: Island missions: 3 per island per week with shared progress; every member who helped gets crystals + pass XP?", "s": "Yes", "why": "Your CR1 (crystals from island missions) and FI 41 (team missions).", "p": 0},
  {"q": "RX24: Battle pass: flat 800 XP per level for all 100 levels (a regular player reaches ~95-100 in 90 days, a casual ~55)?", "s": "Yes", "why": "No wall at the end; daily play is enough.", "p": 1},
  {"q": "RX25: Free track: a small reward every level, keys every 5 levels (Blaze 25/75, Inferno 50, Phoenix 100), titles at 50 and 100?", "s": "Yes", "why": "Something every level and real goals at the milestones.", "p": 1},
  {"q": "RX26: Premium track: cosmetics every 5 levels, a premium skin and animated title at 100, plus 1 extra daily reroll; no keys, boosters or currencies?", "s": "Yes", "why": "Paying never buys random rewards or progress, which keeps the store safe with Mojang's rules (RK1).", "p": 1},
  {"q": "RX27: Premium battle pass price: 1,000 store credits (about EUR 10) per season?", "s": "Yes", "why": "A common price for a 3-month pass; you set it in Tebex.", "p": 1},
  {"q": "RX28: Premium is bought once per account per season; premium cosmetics are given once per account; the free track is per profile?", "s": "Yes", "why": "Cosmetics are account-wide, so a second profile shouldn't pay again or get duplicates.", "p": 1},
  {"q": "RX29: Pass rewards auto-claim at level-up (no claim buttons)?", "s": "Yes", "why": "No busywork and nothing lost at the season end.", "p": 0},
  {"q": "RX30: Catch-up: +50% pass XP while you are 15+ levels behind the day line?", "s": "Yes", "why": "Late joiners can still finish the pass.", "p": 0},
  {"q": "RX31: After level 100, every 2,000 XP gives crystals + a bound Ember key?", "s": "Yes", "why": "Grinders keep a reason to do quests.", "p": 0},
  {"q": "RX32: The collection log counts only items you got yourself (drops, crates, quests, calendar, Black Market), never trades, AH buys or /pay?", "s": "Yes", "why": "A log you can buy means nothing.", "p": 1},
  {"q": "RX33: Collection rewards: page milestones give island level points, a full page gives a title + Blaze key, the whole log gives Keeper of Ashes + Phoenix key + the secret set?", "s": "Yes", "why": "Links the log to island level (PLAN5) and your secret set answer (SE2).", "p": 1},
  {"q": "RX34: Black Market takes only shards (no gems, no money); gem items stay in the gem shop and money items in /shop?", "s": "Yes", "why": "Shards come from events, so Black Market items stay earned and rare.", "p": 1},
  {"q": "RX35: Black Market always open; 8 rotating slots (1 headline, 3 rare, 4 common) every 12 h at 00:00 and 12:00, plus a fixed row with the Island Charter and Disenchant Scroll?", "s": "Yes", "why": "Something new twice a day without making anyone wait for the island size.", "p": 1},
  {"q": "RX36: Black Market prices as listed (Phoenix Regalia 400, Voidforged 250, Mythic book 200, Coinmaster 120, Eclipse key 75, tier 5 spawner 40, Elytra 30 shards ...)?", "s": "Yes", "why": "A headline is a 1-2 month goal and a common item a few days of late play.", "p": 1},
  {"q": "RX37: Black Market limits: per account per rotation, Phoenix Regalia 1 per account per season, Eclipse key 1 per account per month, headline stock 25 per rotation server-wide?", "s": "Yes", "why": "Stops bulk buying and keeps the headline feeling rare.", "p": 0},
  {"q": "RX38: Island Charter prices 150 / 300 / 450 shards for 11x11 / 13x13 / 15x15 (gem price 250 / 500 / 750), one season only?", "s": "Yes", "why": "Your answer 157; earned path a bit cheaper in effort than the gem path.", "p": 1},
  {"q": "RX39: Personal boosters are bound items you start when you want (max 3 h banked per type)?", "s": "Yes", "why": "Never wasted while AFK, and boosters can't be farmed on alts and sold.", "p": 0},
  {"q": "RX40: The tutorial finish also gives 1 bound Ember key, and two new optional steps: Rewards (Tavern) and First crate?", "s": "Yes", "why": "New players open their first crate in the first minutes.", "p": 0},
  {"q": "RX41: Add staff sub-commands /crates admin, /vote admin test, /quests admin, /pass admin and /blackmarket admin (give, take, reset, test roll; admins edit, mods view; all logged)?", "s": "Yes", "why": "Support must fix stuck rewards and test votes; they are new commands, so they need your OK.", "p": 1},
  {"q": "RX42: Move /vote from a plain link to the vote menu (links, today's votes, streak, party progress)?", "s": "Yes", "why": "One place for everything about voting, and Bedrock players see the links in the menu.", "p": 0},
  {"q": "RX43: Unused keys are wiped with the profile at the season reset, with a warning during the Final Week?", "s": "Yes", "why": "Keys are per profile like money; carrying them over would break the fresh start.", "p": 1}
]
```
