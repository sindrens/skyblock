# Phoenix Skyblock: every feature (short list)

Every feature on the server, built or planned, one line each. Each gets its own **feature card** (`features/<id>.md`): a short, standalone description of how it works and why. You can paste a card into any AI, improve it, and lock it before anything is built.

**Status:** ✅ built · 🟡 partly built · 📝 plan written · 💡 idea, needs a plan
**Card:** — not written yet · Draft · Review N (round N of AI improvements) · **Locked** (ready to build) · Built (matches the code)

---

## How the improvement loop works
1. **Pick one card.** Start with the ones that change money or progress: economy, spawners, ranks/perks, progression track, Depths v2.
2. **Paste it into an AI** together with the prompt below. If the feature touches other features (most economy ones do), paste those cards too.
3. **Say yes or no to each idea.** Yes ideas go into the card's text, with the round number. No ideas go into the card's **Rejected** list, with a reason, so no AI suggests them again.
4. **Repeat** until the AI answers "No more useful ideas", or only gives ideas you've already rejected.
5. **Mark the card Locked.** Send it to the developer (main session). The developer builds only Locked cards, and updates the card to **Built** when the code matches.
6. **After launch:** reopen a card only with real data, such as player numbers, economy alerts or complaints. Changing a Locked card bumps its version.

**Why this works:** each card is small, so the AI focuses. The Rejected list stops the same ideas coming back. The principles below keep every AI inside your rules.

### Prompt to paste with a card
```
You are reviewing ONE feature of Phoenix Skyblock, a Minecraft skyblock server (Java + Bedrock via Geyser,
custom Paper plugins, 200-300 players online, 3-month seasons, a strong player economy).

Owner principles you must respect:
1. No waiting, no busywork (no cooldowns unless they stop real abuse).
2. Profiles are islands; never a way to move value between your own profiles.
3. Nothing is ever lost by accident (overflow goes to /box; staff can restore).
4. The tutorial unlocks the game.
5. Everything reachable from menus; Bedrock players never need long commands.
6. English only.
7. Red phoenix look; a forced resource pack skins GUIs later.
8. Mojang's commercial usage guidelines: nothing sold may give a competitive gameplay advantage.
9. Performance first: 300 players on one server; no per-tick scans.

Read the feature card below (and any linked cards). Then give at most 10 improvements. For each:
- What to change (concrete numbers where possible)
- Why it is better (fun, fairness, economy, performance, abuse)
- The downside or risk
- How hard it is (small / medium / large)
Do NOT repeat ideas in the card's "Rejected" list. Do NOT change anything marked LOCKED unless it is clearly broken; then say why.
If nothing useful is left, answer exactly: No more useful ideas.
```

### Card template (`features/<id>.md`)
```
# <Feature name>   ·   id: <id>   ·   status: Draft | Review N | Locked | Built   ·   version: 1
## In one sentence
## How it works (the logic, step by step, with numbers)
## What the player sees (menus, messages, commands)
## Rules and limits (anti-abuse, Bedrock, performance)
## Connections (which features it feeds or depends on)
## Decided (owner answers, with ids)
## Open questions
## Rejected ideas (with the reason)
## Change log (round, what changed)
```

---

## A. Joining and the first hour
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| A1 | Join flow and spawn | First join, welcome, spawn protection, void rescue | ✅ | — |
| A2 | Spawn portal → island | Walk into the portal, your island is created and you're sent there | ✅ | — |
| A3 | Tutorial | Step-by-step guide on your island; features unlock per step | 🟡 (TU1–TU4 of 8) | — |
| A4 | Starter chest | What a new island starts with | 💡 | — |
| A5 | NPCs and holograms | Help NPCs, holograms, leaderboard holograms from the builds | ✅ | — |
| A6 | Portals | Build portals that teleport | ✅ | — |
| A7 | MOTD | Server list message, animated | ✅ | — |

## B. Islands
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| B1 | Island creation and grid | 512×512 grid, starter island, home, borders | ✅ | — |
| B2 | Protection and roles | Leader/Admin/Mod/Member/Coop/Visitor, permission matrix | ✅ | — |
| B3 | Members, invites, coop | Team rules, solo → team, 4 + leader, slot lock 48 h (new) | 🟡 | — |
| B4 | Contribution | Who gave what; kick keeps 50%, top-contributor rewards (new) | 🟡 (stats only) | — |
| B5 | Visiting | /is visit browser, lock, expel, ban | ✅ | — |
| B6 | Warps, ratings, likes | Island warps, top rated (5 IPs), likes | ✅ | — |
| B7 | Island settings and fly | Flags, weather, time, island fly | ✅ | — |
| B8 | Island menu and chat | /is hub, /is chat | ✅ | — |
| B9 | Island notes | Shared to-do list, pinned holograms | ✅ | — |
| B10 | Disband, reset, staff tools | Two-step disband, /isadmin, backups | ✅ | — |
| B11 | Island bank | Shared bank, role permissions set by the leader | ✅ | — |
| B12 | Island storage / vault | Shared storage with take-out limits | 💡 | — |
| B13 | Island level and pillars | Level from many sources, not only blocks | 📝 (Phase 5) | — |
| B14 | Upgrades and masteries | /is upgrade, mastery tasks (Homestead, Treasury…) | 📝 (Phase 5) | — |
| B15 | Island size | 3×3 → 9×9 by upgrades; bigger with shards or gems | 📝 | — |
| B16 | /is top and leaderboards | Solo/team, weekly gain, per pillar | 📝 (Phase 5) | — |
| B17 | Profiles | Up to 3 profiles (islands) per account | ✅ | — |

## C. Money and trading
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| C1 | Currencies | Money, crystals (island only), gems (bought = tradeable, earned = bound), shards | 🟡 | — |
| C2 | Pay, balance, baltop | /pay, /bal, /baltop, /transactions, $10k earned rule (F1) | ✅ | — |
| C3 | Money notes | /withdraw notes, 20 a minute, purge | ✅ | — |
| C4 | Selling | /sell chest, dynamic prices, sell cap 300% | ✅ | — |
| C5 | Shop | /shop categories, quick-buy, undo last buy (new) | ✅ | — |
| C6 | Auction house | /ah listings, fees, collection bin | ✅ | — |
| C7 | Secure trade | /trade, value hints | ✅ | — |
| C8 | Personal bank and interest | Interest every 8 h, 2% fee | ✅ | — |
| C9 | /box | Overflow mailbox, nothing is lost | ✅ | — |
| C10 | Economy watch | Anomaly alerts, /eco limits ($1T + Allow) | ✅ | — |
| C11 | Player shops and buy orders | Chest shops, buy orders | 💡 | — |
| C12 | Sell wands | Sell a chest's contents with a wand | 💡 | — |
| C13 | Black Market | Rotating rare offers for shards | 📝 (Phase 10) | — |
| C14 | Season economy curve | Money targets per stage, balance sheet | 📝 (draft) | — |

## D. Production
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| D1 | Mob and item stacking | Stacks up to 1024, 40 stacks a chunk, no AI for spawner mobs | ✅ | — |
| D2 | Spawners | 5 tiers, stacks 8→512, fixed price + diminishing output (F2) | 🟡 | **Draft** (example below) |
| D3 | Hoppers | 64 items every 8 ticks, per-chunk counter | 💡 | — |
| D4 | Custom mob drops | Mostly vanilla from spawners; special drops (silverfish → nether star…) | 💡 | — |
| D5 | Island fuel | Fuel that runs production | 📝 (Phase 7) | — |
| D6 | Generators and crops | Custom generators, crops without light | 📝 (Phase 7) | — |
| D7 | Collectors and auto-sell | Collect and sell farm output | 📝 (Phase 7) | — |
| D8 | Fishing | Fishing pools and rods | 📝 (Phase 7) | — |
| D9 | Outposts and biomes | Extra production places | 📝 (Phase 7) | — |
| D10 | AFK pool | Fuel + money + keys while AFK, 8 h a day, own area | ✅ (closed until built) | — |

## E. The Depths, tools and skills
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| E1 | Depths v2 zones | 5 zones per line, tier palettes, boss hall + main arena | 📝 (plan next) | — |
| E2 | Per-player blocks | Your own ore and log state, 10 s respawn | ✅ | — |
| E3 | Tiers and gates | Skill-level unlocks, pick a lower tier | 🟡 | — |
| E4 | Satchel | Gathering bag, overflow to inventory, then /box | ✅ | — |
| E5 | Depths drops | Drop tables per tier, fragments, geodes | ✅ | — |
| E6 | Combat zones and layered mobs | Each player fights their tier's mobs | 🟡 (shared mobs) | — |
| E7 | Mini-bosses | Health scaling, anti-alt rules | 🟡 | — |
| E8 | Visibility | Everyone / friends+island / nobody, pin a player | 💡 | — |
| E9 | Skills | Mining, Foraging, Combat to level 150 | ✅ | — |
| E10 | Tools and the Forge | Starter kit, living tools Lv 1–50, perks, prestige | ✅ | — |
| E11 | Blueprints and skins | Tool recipes and looks | ✅ | — |
| E12 | Enchants | 48 enchants, books, /enchanter, slots | ✅ (hoe/rod later) | — |
| E13 | Wardrobe and sets | 16 sets, set levels, merging | ✅ | — |
| E14 | Loadouts | Armor + pet presets in one click | ✅ | — |

## F. Progression and rewards
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| F1 | Progression track | One start-to-endgame track like a battle pass that unlocks perks (F7) | 💡 | — |
| F2 | Ranks and perks | Free Ashborn, 7 bought ranks, 3 won ranks, perk tags | 📝 | — |
| F3 | Achievements | Progress tracking → titles | ✅ | — |
| F4 | Tags and titles | /tags, /display, tag items | ✅ | — |
| F5 | Crates and keys | 7 crates, bound/tradeable keys, fragments | 📝 (Phase 10) | — |
| F6 | Voting | Vote rewards and streaks | 📝 (Phase 10) | — |
| F7 | Daily rewards / calendar | Per-profile daily login | 📝 (Phase 10) | — |
| F8 | Quests | Daily and weekly quests | 📝 (Phase 10) | — |
| F9 | Battle pass | Free and premium (cosmetic) pass | 📝 (Phase 10) | — |
| F10 | Collection log | Collect everything, rewards | 📝 (Phase 10) | — |
| F11 | Boosters and thanks | Global boosters, /thanks | 📝 (Phase 10) | — |

## G. Social, chat and HUD
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| G1 | Chat | Format, rank colours, mentions, [item], filters | ✅ | — |
| G2 | Private messages | /msg, /r, /ignore, link filter, tutorial rule | ✅ | — |
| G3 | Social commands | /list, /seen, /whois, /afk, /playtime… | ✅ | — |
| G4 | Messages and texts | Every text configurable in messages/<module>.yml, one format | ✅ | — |
| G5 | Chat broadcasts | Tips, vote, market news every X minutes | 💡 | — |
| G6 | Friends, parties, guest book | | 💡 | — |
| G7 | Scoreboard | One layout per place | ✅ | — |
| G8 | Tab list | Rank on every player, sorting, header/footer | ✅ (redesign open) | — |
| G9 | /menu hub | Smart hub layout | ✅ (redesign open) | — |
| G10 | /help and /settings | Help topics, per-player settings | ✅ | — |
| G11 | Teleports | /spawn, /tpa, warmups only in combat | ✅ | — |
| G12 | Essentials and perks commands | /craft, /ec, /hat, /nick… | ✅ | — |

## H. Staff and safety
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| H1 | Staff mode | /s, vanish, creative island editing (new) | ✅ | — |
| H2 | Punishments | /p menu, warns, undo, appeals, evidence | ✅ | — |
| H3 | Reports | /report, reasons, DMs as evidence | ✅ | — |
| H4 | Freeze and inspect | | ✅ | — |
| H5 | Logs | /logs, audit log | ✅ | — |
| H6 | Admin panel | /admin, config in-game | ✅ | — |
| H7 | Anti-dupe and security | Unique items, dupe detection, 24 h trade block | ✅ | — |
| H8 | Anticheat | GrimAC setup and suggestions | ✅ | — |
| H9 | Performance | Server config, lag guard, load test | 💡 | — |

## I. Later systems
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| I1 | Warzone and combat tag | PvP zone, money loss, kill reward | 💡 | — |
| I2 | Duels | Arenas, wagers | 💡 | — |
| I3 | Events | KOTH, envoys, event engine | 💡 | — |
| I4 | PvE, bosses, pets | Custom mobs, world bosses, pets | 📝 (Phase 8) | — |
| I5 | Seasons | 3-month resets, prizes, what carries over | 💡 | — |
| I6 | Store and Discord | Tebex, gems, Discord linking | 💡 | — |
| I7 | Resource pack | Forced pack, GUIs, icons, sounds | 📝 | — |
| I8 | Launch prep | Balance pass, load test, checklist | 💡 | — |

**Total:** 102 features. Order the cards are written in: D2 Spawners (example below) → C1 Currencies → F1 Progression track → F2 Ranks and perks → E1 Depths v2 → the rest, by area.

---

## Example card

# Spawners · id: D2 · status: Draft · version: 1

## In one sentence
Spawners make mobs (money and drops) on your island. They are always worth having, but each extra spawner of the same tier on one island gives less, so the island decides when buying more stops paying off.

## How it works
- **5 tiers**: tier 1-2 in /shop, tier 3-5 from drops, crates and the Black Market. 30 mob types in total (5–7 per tier).
- **Shop price is fixed:** tier 1 $117,000, tier 2 $213,000. No price rise per spawner owned.
- **Stacks:** one spawner block holds up to 512 spawners (steps 8 → 512).
- **Output:** 1 mob per spawn cycle per spawner (owner answer 641). Spawner mobs have no AI and stack up to 1,024.
- **Diminishing output per island and tier.** All spawners of one tier on the island are counted, across every block and outpost:

| Spawners of one tier on the island | Each one works at |
|---|---|
| 1–16 | 100% |
| 17–32 | 60% |
| 33–64 | 35% |
| 65–128 | 20% |
| 129+ | 10% |

  Example: 38 tier-2 spawners = 16 + 16×0.6 + 6×0.35 = 27.7 spawners' worth of output.
- **Spawners are tradeable** (AH, /trade). The brake follows the island they work on, so alts or trading can't skip it.
- **Drops:** vanilla loot only. A few special drops are allowed (silverfish → nether star, villager → emerald), balanced in the custom drops card (D4).
- **Crash safety:** a spawner whose block is gone is frozen and logged, never deleted. Spawners on islands disbanded or reset on purpose are cleared.

## What the player sees
- **Spawner menu:** "Tier 2 on this island: 38 = 27.7 at full output. Next one works at 35%. 18 mobs/min."
- **/shop tile, and the lore on AH listings:** "$213,000 · Next works at 35% · +$1.2k/h · Pays back in 171 h (~14 days at your pace)".
  - Green up to 100 h, yellow up to 300 h, red "Poor deal" above that.
- **Payback updates by itself** as the island's sell multiplier and spawner upgrades grow.

## Rules and limits
- Max 8 mob stacks per type per chunk, and 40 stacks per chunk in total.
- Spawner mobs give no Combat XP and no custom drops.
- The counts per island and tier are kept in memory (no scanning). They are updated when a spawner is placed, broken, merged or moved.
- Bedrock: everything shows in the menu and lore; nothing depends on chat clicks.

## Connections
- Selling (C4): spawner loot sells at the dynamic price, with a 40% floor.
- Island level (B13): block worth of spawners.
- Stacking (D1). Upgrades and masteries (B14: Spawner Rate).
- Crates (F5): tier 3–5 spawners.
- AFK pool (D10): no spawner rewards.
- Economy curve (C14): spawners are the biggest money source in the model.

## Decided
- 641: 1 mob per cycle.
- 988: spawners are tradeable.
- R3-21a: spawner mobs drop vanilla loot only, plus special drops.
- R3-26: 40 stacks per chunk.
- R3-13: frozen spawners, never deleted.
- F2: fixed price + diminishing output. This replaces 642 (+10% per owned spawner).

## Open questions
1. Are the free count (16) and the steps (60/35/20/10%) right? The model gives the same money supply as the old plan: sink ratio 84%, $1.8T money supply on day 90.
2. Count per tier (as now), or per mob type?
3. Later outlet for surplus spawners: fusion (4 of a tier → 1 of the next)? Default: not at launch; the AH is the outlet.

## Rejected ideas
- +10% shop price per spawner owned (642): a wall at about 25 spawners, and alt islands bypass it.
- Server buy-back at 25%: adds money to the economy and makes a price floor for key farms.
- Upkeep or season decay: breaks "always worth having".

## Change log
- v1 (30 Oct): written from REVIEW3 F2, ECONOMY_BALANCE.md, the stacking module and the model run.
