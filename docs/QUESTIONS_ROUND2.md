# Questions round 2 (2026-10-30)

How to answer: write the code and your answer, for example `P1 yes`, `S3 64`, `N2 shared`. A short answer is fine.
Anything you skip keeps the **[default]** in brackets until you answer, but nothing new is built on it.

---

## Part 1. Island profiles (most important: changes the base of the plugin)
Not in our plans yet. Every module we have built (money, skills, islands, ender chest, AH, notes, stats) stores data per *player*. Profiles means storing it per *player + profile* instead. That is a big change, and every week we wait more code has to be changed. I want to build the profile layer **next**, before more economy and progression features.

- **P1.** A player can have up to 3 profiles. Is a profile simply "one island + everything that belongs to it"? [yes]
- **P2.** What is separate per profile? Answer yes/no for each:
  - a) money
  - b) tokens
  - c) shards
  - d) gems (store currency)
  - e) inventory
  - f) ender chest
  - g) armor
  - h) XP levels
  - i) skills
  - j) Depths tier unlocks
  - k) satchel
  - l) personal bank
  - m) tools/levels (they're items, so they follow the inventory)
  - n) quests/battle pass
  - o) achievements
  - p) titles/tags
  - q) cosmetics
  - r) settings
  - s) nickname
  - t) rank
  - u) playtime
  - v) punishments
  - w) AH listings
  - x) collection bin
  - y) money notes
  - z) homes

  [separate: a, b, c, e, f, g, h, i, j, k, l, m, n, w, x, z; shared: d, o (achievement progress shared?), p, q, r, s, t, u, v]
- **P3.** Can a profile be on the same island as another of your own profiles (coop/member)? [no]
- **P4.** Can items or money be moved between your own profiles? This is a classic dupe and alt-farm risk. [no, never]
- **P5.** How do you switch: `/profile` menu, `/is profiles`, or both? Allowed anywhere, or only at spawn / out of combat / not frozen? Cooldown? [`/profiles` menu + `/is profiles`; spawn or own island only; 60 s cooldown]
- **P6.** Do new players start with 1 profile slot, unlocking 2 and 3 later (island level, rank, gems)? Or all 3 free? [1 free; 2 and 3 unlocked by rank or gems]
- **P7.** Leaderboards (island top, baltop, skills): per profile or per player (best profile)? [per profile; a player can appear more than once]
- **P8.** Season reset: are all profiles wiped, and are profile slots kept? [all wiped, slots kept]
- **P9.** Can a profile be deleted? Cooldown before a new one? [yes, typed confirm, no cooldown (as for disband)]
- **P10.** Profile names and icons (e.g. "Main", "Grind", picked icon)? [yes]
- **P11.** Join limits (3 island joins per season): per player or per profile? [per player, otherwise profiles bypass the limit]
- **P12.** While these questions are open, pause new money/progression items (bank, sell, shop, trade, tools)? [yes, pause those; islands visiting and Phase P continue]

## Part 2. Overlaps and conflicts in your answers (please confirm)
- **O1.** Answer 10 (block limits) vs our plan "**No hopper limit**, hoppers are priced high". Which blocks get limits: hoppers, spawners (you said stacked), redstone, chests, beacons…? [limit: entities/minecarts and redstone clocks only, no hopper limit]
- **O2.** Answer 39 = **no** (hopper limit + filter hoppers), but 10 = yes. So no filter hoppers and no hopper limit, correct? [correct]
- **O3.** Answer 26 "already planned" (pillars) and 27 yes (block worth + pillars). Island level = pillars + upgrades **+ a small part from block worth**? How big? [block worth at most 20% of island level]
- **O4.** Answer 6 `/is storage` and the planned "Island Vault" mastery: the same thing (vault upgrade = more storage rows)? [same thing]
- **O5.** Answer 12 (crop growth), 13 (mob drops), 14 (spawner rates), 33 (generator per level): all already masteries in `/is masteries`. Nothing extra needed? [nothing extra]
- **O6.** Answer 43 "challenge book for /is upgrades": the masteries already have tasks per level. Is the challenge book the *list of those tasks* (one book menu showing every mastery task and its progress)? [yes]
- **O7.** Answer 9 (ratings 1–5) and 65 (likes once per day): keep both? [both: rating = quality, like = daily popularity]
- **O8.** Answer 56 "island shop with island crystals" adds a **5th currency**. We have money, tokens, gems, shards. New currency, or the island shop uses existing currencies? [no new currency; island shop uses money + tokens]
- **O9.** Answer 71: trash bins at spawn for everyone, but `/trash` stays donator-only (your earlier answer)? [yes]
- **O10.** Answer 72 (AFK zone with rewards) vs island fuel (fuel only while active on the island). Rewards in the AFK zone could make AFK machines worth it. Rewards: small money? keys? nothing but fuel? [small token amount per 15 min, max 2 h per day, no fuel]
- **O11.** Answers 48–50 (island, personal and global boosters) vs the planned **global multiplier cap**. Do boosters count toward the cap? [yes, all boosts share one cap]
- **O12.** Answer 30 (weekly top rewards) and 77 (weekly island competitions): two different weekly things? [yes: 30 = weekly island top, 77 = one rotating category per week]
- **O13.** Answer 57 (player chest shops on islands) vs AH: AH tax 5%; chest shops also taxed? [yes 5%]
- **O14.** Answer 74 (holograms on islands) and 22 (island announcement): announcement in chat on join, holograms in the world? [yes]
- **O15.** Answer 69 auto-pickup: only in The Depths (satchel), or also on islands (blocks you break go straight to inventory)? [Depths only; islands optional upgrade]
- **O16.** Answer 1 (choose starter island) and the plan "starting island is a configurable schematic": each option has its own schematic, starter chest and home spot, all changeable in-game by staff? [yes]
- **O17.** You didn't answer **19** (island invites menu) and **85** (lag tools per island for staff). [19 yes, 85 yes]
- **O18.** "45 yes" was written as "4 yes" in your list. Achievements → titles = yes? [yes]

## Part 3. `/is notes` (new idea 0)
- **N1.** Who can read and write: all members, plus coop players? Separate read/write permission in the role matrix? [members read+write, coop read only, matrix toggle]
- **N2.** One shared book for the island, or one per member, or both (shared + private)? [one shared]
- **N3.** A real book item in the inventory (book & quill opens, closing saves) or a menu with notes as items (add with chat input)? Book: works on Java and Bedrock, but items can be dropped, copied or traded. Menu: safer, works everywhere. [menu list + "open as book" button that gives a temporary book which saves on close and is removed afterwards]
- **N4.** To-do list mode: each note can be "done" (✔) and shows who wrote it and when? [yes]
- **N5.** Notify online members when a note is added? Show the newest open to-do on the scoreboard ("Next: …")? [notify yes, scoreboard optional setting]
- **N6.** Limits: max notes, max length per note, max pages? [50 notes, 200 characters each]
- **N7.** History: keep deleted notes for a while, staff can read them (for scam/abuse reports)? [kept 7 days, staff can read]
- **N8.** Chat filter (links, swear words) on notes? [yes, same filter as chat]
- **N9.** Notes pinned to a place? (e.g. "farm needs fixing" shown as a hologram at that spot) [no, later maybe]

## Part 4. Stacked mobs, items and spawners (answers 10, 11, 36, 37, 38)
### Mobs
- **S1.** Which mobs stack: only spawner mobs, or all mobs (also natural, also in The Depths)? [spawner + natural mobs on islands; never in The Depths, warzone or bosses]
- **S2.** Max mobs per stack? [200]
- **S3.** Merge radius? [5 blocks]
- **S4.** How does a stack die?
  - a) one at a time
  - b) whole stack at once
  - c) depends on the weapon (custom enchant "Slaughter" kills more)

  [a, with enchants later]
- **S5.** Fall/lava/void damage: does the whole stack die (mob grinders)? [whole stack, so farms work]
- **S6.** Drops and XP for a whole-stack kill: full drops times the stack size? [yes]
- **S7.** Stacked mobs have no AI (don't move, don't attack)? This saves a lot of lag. [no AI for spawner mobs, normal AI for natural mobs]
- **S8.** Name shown above the stack, e.g. `Zombie x45`? Colors? [yes, white name + gold count]
- **S9.** Named mobs (name tag), tamed animals, villagers, leashed/saddled mobs never stack? [never stack]
- **S10.** Breeding: can stacked animals breed? Does feeding a stack breed many at once? [feeding a stack breeds up to 10 at once]
- **S11.** You said mob stacking can be turned off. Per island (leader setting), per player, or server-wide by staff? [per island in /is settings]
- **S12.** No despawning: for stacked mobs, all mobs on islands, or only spawner mobs? Minecarts never despawn, also abandoned ones? [no despawn for stacked + spawner mobs and minecarts on islands]
- **S13.** Entity limits per island (answer 11): numbers? E.g. animals 50 stacks, villagers 30, minecarts 50, armor stands 50, item frames 200. [those numbers, raised by an upgrade]

### Items
- **S14.** Dropped items merge into one with a counter: radius and max per stack? [3 blocks, no max (stored as a number)]
- **S15.** Name shown on the item: `Diamond x1,234`? [yes]
- **S16.** Picking up a big stack: as much as fits in the inventory, rest stays? [yes]
- **S17.** Hoppers take from a stacked item one by one or in bulk? [bulk, 64 per tick-step]
- **S18.** Despawn time for stacked items on islands: vanilla 5 min, longer, or never? [10 min]
- **S19.** Do custom/unique items (tools, notes, tag items) stack? [never]

### Spawners
- **S20.** Max spawners in one stack (start / max with the Spawner Stack Limit mastery)? [start 10, max 500]
- **S21.** How do you add spawners: right-click with a spawner in hand, or a menu (shift-right-click the spawner)? Bedrock needs the menu. [both]
- **S22.** Breaking a stack: gives all spawners back, or one at a time? Needs silk touch or a special tool? [one per break, shift-break all; no silk touch needed]
- **S23.** Spawn rate: each stacked spawner adds full rate, or diminishing? [full rate × stack, then Spawner Rate mastery]
- **S24.** Activation range: player within 16 blocks, or always active while the island has fuel? [16 blocks, or fuel when offline]
- **S25.** Spawn conditions (light, space, blocks) ignored for stacked spawners? [ignored]
- **S26.** Where do spawners come from: crates, store, shop, Black Market, mob drops? [crates + shop for low tiers, crates/Black Market for high tiers]
- **S27.** Spawner types list (pig, cow, zombie, skeleton, blaze, iron golem…)? [owner provides list; default vanilla farm mobs + blaze + iron golem]
- **S28.** Spawner menu: shows type, stack, rate, next spawn, fuel, upgrade buttons? [yes]

## Part 5. Islands (answers 1–25)
- **I1.** Starter island choice (1): how many options, names and themes? Can a player change later (only by reset)? [3: Ember Meadow (plains), Frost Peak (snow), Ash Dunes (desert); change only by reset]
- **I2.** Preview (2): spectator flight for 15 s around a copy of the island, then back? [yes]
- **I3.** `/is settings` (3): which flags, and which are on by default?
  - mob spawning (on)
  - animal spawning (on)
  - fire spread (off)
  - explosions (off)
  - leaf decay (on)
  - crop trampling (off)
  - visitors can pick up items (off)
  - PvP (always off)
  - weather (client)
  - time (client)
  - mob stacking (on)
  - item stacking (on)
  - notifications (on)

  [as listed]
- **I4.** Island fly (4): permission name and which upgrade? Visitors can't fly? Fly turns off when you leave your border? [`skyblock.island.fly` or mastery "Wings"; members only; off outside]
- **I5.** Custom borders (5): the vanilla world border can only show **blue, green or red**. "Custom" borders (other colors, particles, patterns) would have to be particles, which cost performance and look weaker on Bedrock. Options:
  - a) only the 3 colors
  - b) 3 colors + particle styles on top
  - c) a resource-pack border texture later

  [b]
- **I6.** Custom border price in gems and which crates? [crates + 100–500 gems by rarity]
- **I7.** `/is storage` (6): start size, max size, upgrade steps, who can access (role matrix), log of who took what? [1 row → 6 rows (54 slots) over 5 levels; role toggle; log yes]
- **I8.** `/is storage` extras ("give it good use"): collectors and autosell chests can send overflow into it, and it can't be accessed while frozen/combat. More ideas from you? [those two]
- **I9.** Leader logs (7): which events do leaders/members with permission see? Breaks/places of valuable blocks, container access, spawner changes, member changes, bank, storage? How long? [those, 7 days]
- **I10.** Custom role names (20): max length, colors allowed? [16 characters, `&` colors]
- **I11.** Role colors in island chat (21): picked by the leader from a palette? [yes, 8 colors]
- **I12.** Island announcement (22): shown to members on join, in chat? Max length? [chat, 200 characters]
- **I13.** Member homes (23): how many per member? [1]
- **I14.** Warp categories (24): which categories (shops, farms, parkour, builds, other)? [those 5]
- **I15.** Sign warps (25): sign format `[warp]` + name; count toward the 3 warp slots? [yes]
- **I16.** Invites menu (19): see sent invites and cancel them? [yes]

## Part 6. Value, leaderboards, generators, collectors (26–40)
- **V1.** Weekly top rewards (30): which rewards for places 1/2/3 (solo and team)? [Legendary / Epic / Rare key]
- **V2.** Island value history (31): how far back (season, 30 days)? Shown as a menu with daily points? [season, daily points]
- **V3.** Top statues (29): top 3 solo + top 3 team at spawn, update every hour? [yes]
- **V4.** Custom generator (32): which ores at each generator level (cobble, coal, iron, gold, diamond, emerald, custom ores)? [owner list; default vanilla ores + 2 custom]
- **V5.** Collectors and autosell chests (34, 35): tier 1–5 stats (radius, storage, sell %)? Sell at the dynamic price? Run on fuel when offline? [owner sets numbers; dynamic price; fuel yes]
- **V6.** Crop harvester hoe (40): area per level (3×3, 5×5, 7×7), auto-replant, from the Forge? [yes, Forge]

## Part 7. Progression, boosters, rewards (41–53)
- **R1.** Battle pass (44): season length = 3 months, how many levels, premium price (credits), free track rewards? [100 levels, premium via store credits]
- **R2.** Island milestone rewards (47): rewards at island level 10/25/50/100/200? [owner list; default keys + cosmetics]
- **R3.** Boosters (48–50): types (XP, sell, farming, spawner, fly), durations (15/30/60 min), strength (+25/50/100%)? Do two boosters of the same type stack? [no stacking of same type, extend time instead]
- **R4.** Global booster (50): who can start one (store/crates), and does everyone online get a "thank you" reward option (`/thanks` gives the starter a small reward)? [yes]
- **R5.** Daily login rewards (51): 7-day or 30-day calendar? Streak lost after a missed day? [30-day calendar, streak keeps the day but resets the bonus]
- **R6.** Playtime rewards (52): every 1 h / 5 h / 24 h of playtime? [1 h small, 10 h key, 50 h title]
- **R7.** Vote party (53): votes needed, rewards? [100 votes, everyone online gets a Rare key]

## Part 8. Economy and trading (55–61)
- **E1.** Player chest shops (57): sign shops on islands, or a "shop block" with a menu? Bedrock-friendly menu needed. Visitors buy only when the island allows it? [shop block with menu; island setting]
- **E2.** Buy orders (58): `/orders` menu, money held in escrow when the order is placed, sellers fill partly, 5% tax? Max orders per player? [yes, 5 orders]
- **E3.** Sell wand (59): uses (100 / 500 / unlimited), source (crates/store), uses island sell multiplier? [uses-based, crates, multiplier yes]
- **E4.** Island bank (55): see the planned table. Also the "early withdrawal 2% fee": keep? [keep]

## Part 9. Social, quality of life, cosmetics (62–92)
- **Q1.** Friends (63): max friends, friend requests, see where friends are (island/Depths), join notifications? [100, requests, location shown]
- **Q2.** Parties (64): max size, party chat `/pc`, shared XP or loot in The Depths? [5, `/pc`, no sharing]
- **Q3.** Guest book (66): visitors write max 1 message per island per day, leader can delete, staff can read? [yes]
- **Q4.** `[island]` in chat (68): shows an island card on hover (name, level, members)? [yes]
- **Q5.** Sort inventory (70): button in chests/inventory (Java middle-click, Bedrock?). Bedrock has no middle click: a `/sort` command or a button in our menus only? [`/sort` + sort button in island storage]
- **Q6.** Island scoreboard page (73): switches automatically when you're on your island? [yes]
- **Q7.** Island holograms (74): how many, max lines, filtered for bad words? [3 holograms, 4 lines, filtered]
- **Q8.** Particle trails / kill effects (89, 90): only cosmetic from crates/gems? Where do kill effects show (warzone, mobs)? [crates/gems; mobs + warzone]
- **Q9.** Island themes (92): what's in a theme (border style, particles, music, weather look)? [border + particles]
- **Q10.** Website stats (87): do you have a website/host for it? [later]

## Part 10. Events and staff (75–87)
- **EV1.** Island competitions (77): weekly categories (most crops, most mined, most mobs, most sold)? Rewards? [rotating, top 3 get keys]
- **EV2.** Fishing competition (80): every 3 hours, 15 min long, top 3 rewards? [every 4 h, 15 min]
- **EV3.** Chat games (81): how often (every 15 min), types (unscramble, math, first to type), rewards (small money/tokens)? [every 20 min, $5k]
- **ST1.** Island inspector (83): staff tool in staff mode (click a block → island info)? [yes]
- **ST2.** Island backup (84): automatic snapshot before disband/reset, kept how long, staff restore command? [kept 7 days, `/isadmin restore`]

---

## Part 11. Still waiting from earlier rounds (short; defaults apply until answered)
### Staff and punishments
1. Frozen players may use `/discord` and `/rules`? [no commands]
2. Staff/plugin teleports can move frozen players? [yes]
3. Frozen players hover; on unfreeze they get 10 s of no fall damage? [yes]
4. Freeze stays after a ban/unban? [keep]
5. Inspect tool = ender eye? [yes]
6. Helpers may freeze, use staff mode and see join logs (no IPs)? [yes]
7. Logout while frozen: alert or auto-ban? [alert]
8. Punishment ladders OK (Hacking 30d → perm; Toxicity warn → 1h → 1d → 7d → perm mute)? [yes]
9. Public "X was banned/muted" for players? [on]
10. Unhandled warn suggestion expires after 24 h without resetting the counter? [suggest again]
11. Offline exempt check via LuckPerms? [online only]
12. Never-shorten for `/tempmute` and `/tempban` too? [yes]
13. Kick button uses a fixed reason? [yes]
14. Auto mutes never announced publicly? [staff only]
15. Muted-while-offline message on every join or once? [every join]
16. Time zone for stats and reports: Europe/Oslo? [Oslo]
17. Discord webhook for punishments: now or with the Discord integration? [later]
18. Deleted titles/tags: keep hidden in the database, or delete? [keep hidden]

### Islands
19. Member cap 4 includes the leader? [yes]
20. Join cooldown 24 h after the last join or leave? [yes]
21. Accepting an invite auto-disbands an untouched starter island? [no]
22. Invite/kick/coop as permission matrix toggles? [fixed to Leader + Mod]
23. After transfer, old leader becomes Mod? [Mod]
24. Max 4 coops, and coop on several islands? [yes]
25. Hostile mobs can be hurt by visitors? [yes]
26. `/is bypass` admins only, per session? [yes]
27. Default role permissions (Mod all, Member all but spawners, Coop all but fire/spawners, visitors nothing)? [yes]
28. Players can't walk/fly out of an island border? [yes]
29. Falling blocks outside the border disappear (anti-dupe)? [yes]
30. Join the server at spawn or at your island? [spawn]
31. Staff delete/reset don't start cooldowns; reset gives only the leader a new island? [yes]
32. Disband confirm word "disband"? [yes]
33. Island bank on disband: paid to the leader? [decide with M3.3]

### Economy
34. Approve the balance sheet `docs/ECONOMY_BALANCE.md` (its §12)? [pending]
35. Day-3 target: accept ~$15M for top players, or reshape prices? [accept]
36. Endless late money sink "Mastery"? [yes]
37. Tool XP ×1.21 per level? [yes]
38. Interest on the lowest balance of each 3 h cycle, and only with ≥ 2 h play in the last 7 days? [yes]
39. Tokens tradeable (/pay, AH)? [no]
40. New accounts (< 2 h) can't `/pay`, `/withdraw`, list or buy on the AH? [yes]
41. Price recovery 2% of the gap per 10 min, Hot Item +25% for 24 h? [yes]
42. Personal bank tier unlock cost 2% of capacity? [yes]
43. Shards: how are they earned? [bosses, envoys, KOTH, high Depths tiers]
44. Gems at season reset: kept? [kept]
45. Personal bank: perks per tier (10 tiers)? [owner list]
46. Max total sell multiplier? [+100%]
47. `/eco` and `/eco note` owner-only? [yes]
48. `/pay` minimums and confirm thresholds OK ($10, $1M)? [yes]
49. Everyone can see `/bal <player>`? [yes]
50. Which currencies wipe at season start? [money, tokens, shards wipe; gems kept]
51. AH: refund the listing fee on cancel? [no]
52. AH: extra listing slots per rank? [none until ranks exist]
53. AH: money notes listable? [no]
54. AH: filled shulker boxes/bundles not listable? [yes]
55. AH limits ($10–$1T, 10 s, 3 per 30 min)? [yes]
56. Money notes: $100–$1T, 10 s cooldown, no fee, money only? [yes]

### The Depths and skills
57. Fast-break cheaters: silent (no reward) + staff alert? [yes]
58. Depths visibility setting saved? [yes, with the settings toggle]
59. No building inside Depths regions? [yes]
60. Fourth wing space south of The Gate kept free? [yes]
61. Combat unlock levels 0/6/12/18/24/30/38/46/55/66? [yes]
62. Locked gate block = iron bars? [yes]
63. Player in a zone that becomes locked → sent to The Gate? [yes]
64. One shared teleport warmup for every teleport (follow-up F4)? [yes]
65. Max skill level 66? [66]
66. Combat XP per kill as proposed? [yes]
67. Mining/Foraging XP only in The Depths? [yes]
68. Staff `/skills set|add` command? [no]
69. Mine drops mix per tier (ores, tokens, keys, rare items)? [owner list]
70. Custom ore/tree tier names? [developer proposes]

### Other (from the first review)
71. Sets: how are pieces obtained? Final list? [owner]
72. Tool prestige reward? [title + skin + small bonus]
73. Team rewards for 2nd/3rd place? [owner]
74. Spawner types and max stack? (see S20, S27)
75. Rank perks per rank? [owner]
76. Death rules in PvP zones (keep items?)? [keep inventory, lose some money]
77. Vanilla rules table OK? [owner]
78. Nether/End future use? [owner]
79. Elytra source? [crates/Black Market/events]
80. Other gamemodes and when? Hub contents? [owner]
