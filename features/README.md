# Phoenix Skyblock: every feature (short list)

_Last checked against GitHub `main` 2f15188 (30 Oct 2026, 23:48) and the owner's final REVIEW3 answers. All 102 cards are written (31 Oct)._

Every feature on the server, built or planned, one line each. Each gets its own **feature card** (`features/<id>.md`): a short, standalone description of how it works and why. You can paste a card into any AI, improve it, and lock it before anything is built.

**Status:** ✅ built · 🟡 partly built · 📝 plan written · 💡 idea, needs a plan
**Card:** — not written yet · Draft · Review N (round N of AI improvements) · **Locked** (ready to build) · Built (matches the code)

A ✅ feature can still have a **Draft** card: the core is built, but the owner has decided changes that are not built yet. The card says which lines are "Planned (decided)". Lines marked **(proposed default)** are suggestions, not decisions. In the cards, REVIEW3 follow-up ids are written as "REVIEW3 F7", "REVIEW3 D1" so they don't mix with card ids.

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
| A1 | Join flow and spawn | First join, welcome, spawn protection, void rescue | ✅ | [Draft](A1.md) |
| A2 | Spawn portal → island | Walk into the portal, your island is created and you're sent there | ✅ | [Draft](A2.md) |
| A3 | Tutorial | Step-by-step guide on your island; features unlock per step | 🟡 (all 8 milestones built; REVIEW3 F1 + R3-32 rules not yet) | [Draft](A3.md) |
| A4 | Starter chest | What a new island starts with: Forge starter tools, seeds, saplings, lava, Field Guide book | ✅ (3 starter variants planned) | [Draft](A4.md) |
| A5 | NPCs and holograms | Help NPCs, holograms, leaderboard holograms from the builds | ✅ | [Built](A5.md) |
| A6 | Portals | Build portals that teleport | ✅ | [Built](A6.md) |
| A7 | MOTD | Server list message, animated | ✅ (maintenance mode planned) | [Draft](A7.md) |

## B. Islands
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| B1 | Island creation and grid | 512×512 grid, starter island, home, borders | 🟡 (3 starter islands, preview, border colours planned) | [Draft](B1.md) |
| B2 | Protection and roles | Leader/Admin/Mod/Member/Coop/Visitor, permission matrix | ✅ | [Draft](B2.md) |
| B3 | Members, invites, coop | Team rules, solo → team, 4 + leader, coop; 48 h slot lock decided | 🟡 | [Draft](B3.md) |
| B4 | Contribution | Who gave what; kick keeps 50%, top-contributor season rewards (decided) | 🟡 (stats only) | [Draft](B4.md) |
| B5 | Visiting | /is visit browser, lock, expel, ban | ✅ | [Built](B5.md) |
| B6 | Warps, ratings, likes | Island warps, top rated (5 IPs), likes | ✅ (5-IP smoothing decided) | [Draft](B6.md) |
| B7 | Island settings and fly | Flags, weather, time, island fly | ✅ | [Built](B7.md) |
| B8 | Island menu and chat | /is hub, /is chat | ✅ | [Built](B8.md) |
| B9 | Island notes | Shared to-do list, pinned holograms | ✅ | [Built](B9.md) |
| B10 | Disband, reset, staff tools | Disband, reset, /isadmin, backups and restore | ✅ (two-step disband decided) | [Draft](B10.md) |
| B11 | Island bank | Shared bank, role permissions set by the leader | ✅ | [Draft](B11.md) |
| B12 | Island storage / vault | Shared Island Vault with daily take-out limits per role | 📝 | [Draft](B12.md) |
| B13 | Island level and pillars | Island level from 8 sources, 4 pillars, block worth | ✅ (M5.1–M5.3; 2 sources wait) | [Draft](B13.md) |
| B14 | Upgrades and masteries | Challenge Book (180 tasks), /is upgrade, masteries (Homestead, Treasury…) | 🟡 (Challenge Book built) | [Draft](B14.md) |
| B15 | Island size | 3×3 → 9×9 by upgrades; bigger with shards or gems | 🟡 (size engine built) | [Draft](B15.md) |
| B16 | /is top and leaderboards | Solo/team, weekly gain, per pillar | 🟡 (Level/Value tabs built) | [Draft](B16.md) |
| B17 | Profiles | Up to 3 profiles (islands) per account | ✅ | [Draft](B17.md) |

## C. Money and trading
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| C1 | Currencies | Money, crystals (island only), gems (bought = tradeable, earned = bound), shards | 🟡 | [Draft](C1.md) |
| C2 | Pay, balance, baltop | /pay, /bal, /baltop, /transactions, $10k earned rule (F1) | ✅ ($10k rule decided) | [Draft](C2.md) |
| C3 | Money notes | /withdraw notes, 20 a minute, purge | ✅ (20/min + purge decided) | [Draft](C3.md) |
| C4 | Selling | /sell chest, dynamic prices, sell cap 300% | ✅ | [Draft](C4.md) |
| C5 | Shop | /shop categories, quick-buy; undo last buy + 50% check decided | ✅ (undo decided) | [Draft](C5.md) |
| C6 | Auction house | /ah listings, fees, collection bin | ✅ | [Draft](C6.md) |
| C7 | Secure trade | /trade, value hints | ✅ (value hints decided) | [Draft](C7.md) |
| C8 | Personal bank and interest | Interest every 8 h, 2% fee | ✅ | [Draft](C8.md) |
| C9 | /box | Overflow mailbox, nothing is lost | ✅ | [Draft](C9.md) |
| C10 | Economy watch | Anomaly alerts, /eco limits ($1T + Allow) | ✅ ($1T Allow decided) | [Draft](C10.md) |
| C11 | Player shops and buy orders | Chest shops, buy orders | 📝 | [Draft](C11.md) |
| C12 | Sell wands | Sell a chest's contents with a wand | 📝 | [Draft](C12.md) |
| C13 | Black Market | Rotating rare offers for shards | 📝 (Phase 10) | [Draft](C13.md) |
| C14 | Season economy curve | Money targets per stage, balance sheet | 🟡 (sheet + model approved for test) | [Draft](C14.md) |

## D. Production
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| D1 | Mob and item stacking | Stacks up to 1024, 40 stacks a chunk, no AI for spawner mobs | 🟡 (40-stack cap not yet) | [Draft](D1.md) |
| D2 | Spawners | 5 tiers, stacks 8→512, fixed price + diminishing output (F2) | 🟡 | [Draft](D2.md) |
| D3 | Hoppers | 64 items every 8 ticks, per-chunk counter + staff alert (decided) | 🟡 (vanilla, 8/8 ticks; F12/F13 decided) | [Draft](D3.md) |
| D4 | Custom mob drops | Depths mobs drop custom items; spawner mobs vanilla + a few special drops | 🟡 (Depths mobs only) | [Draft](D4.md) |
| D5 | Island fuel | Fuel that runs production | 📝 (Phase 7) | [Draft](D5.md) |
| D6 | Generators and crops | Custom generators, crops without light | 🟡 (crops without light built) | [Draft](D6.md) |
| D7 | Collectors and auto-sell | Collect and sell farm output | 📝 (Phase 7) | [Draft](D7.md) |
| D8 | Fishing | Fishing pools and rods | 📝 (Phase 7) | [Draft](D8.md) |
| D9 | Outposts and biomes | Extra production places | 📝 (Phase 7) | [Draft](D9.md) |
| D10 | AFK pool | Fuel + money + keys while AFK, 8 h a day, own area | 🟡 (module built, pool area not) | [Draft](D10.md) |

## E. The Depths, tools and skills
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| E1 | Depths v2 zones | v1 built (33 zones); v2: 5 zones per line, tier palettes, boss hall + main arena | 📝 (decided; plan md next) | [Draft](E1.md) |
| E2 | Per-player blocks | Your own ore and log state, 10 s respawn | ✅ (tier palette for v2 planned) | [Draft](E2.md) |
| E3 | Tiers and gates | Skill-level unlocks, pick a lower tier | 🟡 (pick a lower tier decided) | [Draft](E3.md) |
| E4 | Satchel | Gathering bag, overflow to inventory, then /box | 🟡 (full-satchel fix decided) | [Draft](E4.md) |
| E5 | Depths drops | Drop tables per tier, fragments, geodes | 🟡 (geodes, fragments at 0%) | [Draft](E5.md) |
| E6 | Combat zones and layered mobs | Each player fights their tier's mobs | 🟡 (shared mobs) | [Draft](E6.md) |
| E7 | Mini-bosses | Health scaling, anti-alt rules | 🟡 | [Draft](E7.md) |
| E8 | Visibility | Everyone / friends+island / nobody, pin a player | 📝 (decided, REVIEW3 D4) | [Draft](E8.md) |
| E9 | Skills | Mining, Foraging, Combat to level 150 | 🟡 (R3-24 not yet) | [Draft](E9.md) |
| E10 | Tools and the Forge | Starter kit, living tools Lv 1–50, perks, prestige | 🟡 | [Draft](E10.md) |
| E11 | Blueprints and skins | Tool recipes and looks | 🟡 | [Draft](E11.md) |
| E12 | Enchants | 48 enchants, books, /enchanter, slots | 🟡 (38 of 48 work; hoe/rod later) | [Draft](E12.md) |
| E13 | Wardrobe and sets | 16 sets, set levels, merging | 🟡 (4 sets locked) | [Draft](E13.md) |
| E14 | Loadouts | Armor + tool presets in one click; pets when pets exist | 🟡 (pet is a hook) | [Draft](E14.md) |

## F. Progression and rewards
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| F1 | Progression track | One start-to-endgame track like a battle pass that unlocks perks (F7) | 💡 (direction decided, REVIEW3 F7) | [Draft](F1.md) |
| F2 | Ranks and perks | Free Ashborn, 7 bought ranks, 3 won ranks, perk tags | 🟡 (/freerank, perk commands built) | [Draft](F2.md) |
| F3 | Achievements | Progress tracking → titles | ✅ | [Draft](F3.md) |
| F4 | Tags and titles | /tags, /display, tag items | ✅ | [Draft](F4.md) |
| F5 | Crates and keys | 7 crates, bound/tradeable keys, fragments | 🟡 (7 crates + pity built) | [Draft](F5.md) |
| F6 | Voting | Vote rewards and streaks | 📝 (Phase 10) | [Draft](F6.md) |
| F7 | Daily rewards / calendar | Per-profile daily login | 📝 (Phase 10, M10-6 started) | [Draft](F7.md) |
| F8 | Quests | Daily and weekly quests | 📝 (Phase 10) | [Draft](F8.md) |
| F9 | Battle pass | Free and premium (cosmetic) pass | 📝 (Phase 10) | [Draft](F9.md) |
| F10 | Collection log | Collect everything, rewards | 📝 (Phase 10) | [Draft](F10.md) |
| F11 | Boosters and thanks | Global boosters, /thanks | ✅ | [Draft](F11.md) |

## G. Social, chat and HUD
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| G1 | Chat | Format, rank colours, mentions, [item], filters | ✅ | [Built](G1.md) |
| G2 | Private messages | /msg, /r, /ignore, social spy; link filter + tutorial DM rule decided | 🟡 (R3-32 filter + tutorial rule decided) | [Draft](G2.md) |
| G3 | Social commands | /list, /seen, /whois, /afk, /playtime… | ✅ | [Draft](G3.md) |
| G4 | Messages and texts | Every text configurable in messages/<module>.yml, one format | ✅ | [Built](G4.md) |
| G5 | Chat broadcasts | Tips, vote, market news every X minutes | 📝 (only manual /broadcast built) | [Draft](G5.md) |
| G6 | Friends, parties, guest book | Friends list, parties with /pc, island guest book | 📝 | [Draft](G6.md) |
| G7 | Scoreboard | One layout per place | ✅ | [Built](G7.md) |
| G8 | Tab list | Rank on every player, sorting, header/footer | ✅ (redesign open) | [Draft](G8.md) |
| G9 | /menu hub | Smart hub layout | ✅ (redesign open) | [Draft](G9.md) |
| G10 | /help and /settings | Help topics, per-player settings | 🟡 (new toggles decided) | [Draft](G10.md) |
| G11 | Teleports | /spawn, /tpa, homes: instant; 3 s only in combat | ✅ | [Built](G11.md) |
| G12 | Essentials and perks commands | /craft, /ec, /hat, /nick… | 🟡 (rank grants wait for F2) | [Draft](G12.md) |

## H. Staff and safety
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| H1 | Staff mode | /s, vanish, creative island editing (new) | 🟡 (creative editing decided) | [Draft](H1.md) |
| H2 | Punishments | /p menu, warns, undo, appeals, evidence | ✅ | [Draft](H2.md) |
| H3 | Reports | /report, reasons, DMs as evidence | ✅ (new reasons decided) | [Draft](H3.md) |
| H4 | Freeze and inspect | Freeze a player for a check; Inspect card: profile, inventory, reports, logs | ✅ | [Draft](H4.md) |
| H5 | Logs | /logs, audit log | ✅ | [Draft](H5.md) |
| H6 | Admin panel | /admin, config in-game | ✅ | [Draft](H6.md) |
| H7 | Anti-dupe and security | Unique items, dupe detection, automatic 24 h trade block (decided) | 🟡 (24 h trade block decided) | [Draft](H7.md) |
| H8 | Anticheat | GrimAC setup and suggestions | 🟡 (Grim not wired to /psuggest) | [Draft](H8.md) |
| H9 | Performance | Server config, lag guard, load test | 🟡 (config fixes built; lag guard, load test planned) | [Draft](H9.md) |

## I. Later systems
| # | Feature | What it is | Status | Card |
|---|---|---|---|---|
| I1 | Warzone and combat tag | PvP zone, money loss, kill reward | 🟡 (combat tag built, warzone empty) | [Draft](I1.md) |
| I2 | Duels | Arenas, wagers | 📝 | [Draft](I2.md) |
| I3 | Events | KOTH, envoys, event engine | 📝 | [Draft](I3.md) |
| I4 | PvE, bosses, pets | Custom mobs, world bosses, pets | 🟡 (placeholder mini-bosses) | [Draft](I4.md) |
| I5 | Seasons | 3-month resets, prizes, what carries over | 🟡 (hooks only) | [Draft](I5.md) |
| I6 | Store and Discord | Tebex, gems, Discord linking | 🟡 (links + gems currency) | [Draft](I6.md) |
| I7 | Resource pack | Forced pack, GUIs, icons, sounds | 📝 (waits for art) | [Draft](I7.md) |
| I8 | Launch prep | Balance pass, load test, checklist | 📝 | [Draft](I8.md) |

**Total:** 102 features. Suggested review order: D2 Spawners → C1 Currencies → F1 Progression track → F2 Ranks and perks → E1 Depths v2 → the rest, by area.

---

## Example card
The owner's example card is [D2 Spawners](D2.md). Every other card follows the same template.

