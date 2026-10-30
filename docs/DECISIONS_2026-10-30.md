# Owner decisions, round 2 (2026-10-30)

Answers to `docs/QUESTIONS_ROUND2.md`. "Suggested" = the [default] in that file was accepted. These override older docs where they conflict.
Follow-up questions are at the end of this file (and in chat).

## Profiles (new, high priority, NOT paused)
- P1 a profile = one island + everything that belongs to it.
- P2 **separate per profile:** money, tokens→crystals, shards, inventory, ender chest, armor, XP levels, skills, Depths unlocks, satchel, personal bank, tools, quests/battle pass, achievements, settings, playtime, AH listings, collection bin, money notes, homes. **Shared (account):** gems, titles/tags, cosmetics, nickname, rank, punishments.
- P3 two own profiles never on the same island. P4 **nothing** moves between own profiles (no items, no money, no boosting) — implement with extreme care.
- P5 switch only with `/profiles`. P6 1 free slot, slots 2–3 unlocked by rank or gems. P7 leaderboards use the player's **best** profile. P8 at season reset: gem-bought slots are kept, slots from anything else are lost. P9 delete allowed, no cooldown. P10 names + icons. P11 join limits per player (REVIEW A5). P12 do **not** pause other work.

## Overlaps
- O1/O2 **no block limits, no hopper limit, no filter hoppers**; handle lag with optimized systems + anti-lag-machine detection.
- O3 block worth max 20% of island level. O4 `/is storage` = Island Vault mastery. O5 masteries already cover crop/mob/spawner/generator; owner wants more creative mastery ideas (follow-up F-O5). O6 challenge book = mastery task list.
- O7 likes: one per **player** (not per profile) per island per day; ratings stay too.
- O8 **island crystals replace tokens** (currencies: money, crystals, gems, shards). Tokens are removed everywhere.
- O9 trash bins at spawn for all, `/trash` donators. O10 **AFK pool**: fuel + small money + keys, loot improves the longer you stay, max 8 h per day, balanced. O11 boosters count toward the global multiplier cap.
- O12 weekly top and weekly competition are different. O13 chest shops taxed 5%. O14 announcement in chat, holograms in world. O15 auto-pickup in The Depths and on islands. O16 each starter island has its own schematic/chest/home, editable by staff. O17 invites menu + lag tools yes. O18 achievements → titles yes.

## `/is notes`
- N1–N8 suggested (members write, coop read, matrix toggle; one shared list; menu + temporary book; to-do with author/date; notify + optional scoreboard line; 50 × 200 chars; deleted kept 7 days for staff; chat filter).
- N9 notes can be pinned to a spot as a hologram; removed when checked off.

## Stacking
- Mobs: S1 suggested; **S2 max 1024 per stack**; S3 5 blocks; **S4 kills depend on the weapon** (custom enchants; without one: one at a time); S5 fall/lava kills the stack; S6 full drops/XP; S7 spawner mobs no AI; S8 name in the server color theme; S9 named/tamed/villagers/leashed never stack; **S10 breeding limited by the food you give** (15 cows + 5 wheat = 5 bred); **S11 stacking can't be turned off**; **S12 stacked + spawner mobs and minecarts never despawn, max 8 stacks per chunk per type (each up to 1024)** (confirm, F-S12); **S13 entity limits lower than proposed, balanced** (proposal in follow-up).
- Items: S14–S19 suggested.
- Spawners: **S20 start 8, max 512 per stack**; S21–S24 suggested; **S25 spawn conditions ignored, crops too (grow without light)**; **S26 from The Depths, crates, shop (low tiers), crates/Black Market (high tiers)**; S27 owner wants a researched list (follow-up); S28 spawner menu yes.

## Islands
- I1 3 starter islands (Ember Meadow, Frost Peak, Ash Dunes). I2 preview yes. I3 flags: see follow-up F-I3 (numbers unclear). **I4 island fly for everyone on the island (also visitors) via `skyblock.island.fly` or the "Wings" mastery.** **I5 border colors + particle styles now, and a resource pack later** (needs a resource-pack plan). I6 suggested. I7 storage 1→6 rows over 5 levels, role toggle, log.
- **I8 storage: no collector overflow; blocked while frozen/in combat; auto-collect filter** (picked-up items on the list go straight into storage, e.g. pistons). More ideas in follow-up.
- I9–I12 suggested. **I13 1 home per member, more via masteries.** I14–I16 suggested.

## Value, rewards, economy
- **V1 weekly top: keys; crates get renamed, 7 crates in total including the KOTH key** (names in follow-up). **V2 island value history per season, saved after the season, owner can export it.** V3–V5 suggested. **V6 harvester hoe + custom enchants (synergy).**
- R1–R3 suggested. **R4 `/boosters` shows personal + global boosters; `/thanks` gives the starter 1 gem; players who say "gg" after a store purchase get 1 gem** (limits in follow-up). **R5 30-day calendar; a broken streak can be saved for 100 credits within 2 days.** R6, R7 suggested.
- E1, E2, E4 suggested. **E3 several sell wand types with rarities and custom features.**
- **#46 max total sell multiplier +300%** (only with the best of everything). **#40 new-account limit 30 minutes (not 2 h)** for /pay, /withdraw, AH. **#47 `/eco` owner + admin (permission).** **#48 remove amount suggestions like `10k 10m 10b` in tab completion** (players still type `10m`). #52 rank perks (AH slots etc.) planned with ranks.
- **#39 tokens removed.** #34–38, #41–45, #49–51, #53–56 suggested.
- **#61 progression: fast at first, progressively harder in every system, with ups and downs that keep players motivated** (apply in the balance sheet and every system).

## Social, QoL, events, staff
- Q1–Q5, Q7–Q10 suggested. **Q6 a different scoreboard per place (island, The Depths, spawn, warzone…), only relevant lines.**
- EV1–EV3, ST1, ST2 suggested.
- **New command approved: `/freerank`** (claim a free rank after 30 min playtime).

## Earlier questions (#1–#80)
- Staff: #1–3 suggested; **#4 a ban clears the freeze**; **#5 Inspect tool = blaze rod**; #6–8 suggested; **#9 public announcements unless `-s`**; #10–15 suggested; **#16 CET (Europe/Oslo) on the server, Discord shows each player's local time**; **#17 Discord later — keep everything "later" in `docs/LATER.md`**; #18 suggested.
- Islands: #19 suggested; **#20 join cooldown counts only from the last join**; #21–22 suggested; **#23 new island role Admin; after transfer the old leader becomes Admin**; #24 suggested; **#25 hurting hostile mobs is a role permission (visitors too) in `/is permissions`**; #26 suggested; #27 "be logical" (keep defaults, add the new toggles sensibly); #28–33 suggested.
- Economy: see above. Depths/skills: #57–64 suggested; **#65 max skill level 150**; #66–67 suggested; **#68 staff `/skills set|add` for admin + owner**; #69–70 suggested.
- Other: **#71 sets: easter eggs from every part of the game** (ideas in follow-up); #72–73 suggested; #74 spawner list (S27); #75 suggested; **#76 PvE death: keep inventory, lose nothing, only a death stat** (PvP zone rules in follow-up); #77/#78 see follow-up; #79 suggested; #80 hub later.

---

# Round 3 answers (2026-10-30)
- **Profiles:** PR1–PR5 suggested (achievement titles become account titles; crystals per profile, gems account-wide; rank perks on every profile with separate vault contents; switching teleports to that profile's island home with its inventory/armor/XP/health/effects, not in combat/frozen/boss arena; slot 2 = 500 gems or Knight+, slot 3 = 1,500 gems or Warlord+).
- **Crystals:** CR1–CR4 suggested (earned like tokens + island missions; spent on island shop, /enchanter, Forge extras; shards stay Black Market; not tradeable; aqua crystal look, gems get a new color).
- **/is settings:** mob spawning off, animal spawning on, fire spread off, explosions off, leaf decay on, crop trampling off, visitors pick up items off, notifications on, visitor fly on, mob stacking not a setting. **Weather default always sunny and time default always day, but both changeable in /is settings.**
- **Stacking/spawners:** SK1–SK10 suggested (8 stacks per mob type per chunk, each ≤ 1,024; entity limits armor stands 20, item frames 60, minecarts 20, boats 10, villagers 20, display entities 30, +50% with an upgrade; spawner tiers 1–5 as listed; never villager/warden/wither/dragon/elder guardian/allay; vanilla loot; evoker totems + shulker shells very rare tier 5).
- **Masteries:** MA1–MA12 yes (Timber, Swift Hands, Warrior, Deep Pockets, Market Contacts, Crystal Rush, Lucky Strike, Fuel Efficiency, Beast Tamer, Angler, Homestead, Wings); MA13 AFK Comfort no.
- **Island storage:** ST1–ST6 declined (no auto-collect filter, no search/sort/sell/deposit/compress/pages) — owner open to other ideas.
- **Crates (7):** Ember (common), Cinder (uncommon), Blaze (rare), Inferno (epic), Phoenix (legendary), Eclipse (monthly, new rewards each month), Conqueror (KOTH).
- **Rewards:** RW1 "gg" after a store purchase = 1 gem, max 5/day. **RW2 thanking a global booster with `/thanks` or by saying thanks in chat.** **RW3 AFK pool needs no movement (it's AFK); find other anti-abuse (e.g. one reward per IP/alt check).** **RW4 `/freerank`: the lowest rank, free, with a cosmetic name color, a few small perks and a choice of 1 of 3 tags, kept forever.**
- **Sets (SE1):** no easter eggs for sets (easter eggs go into other parts of the game). `/sets` shows every set; sets are bought with gems, won from crates, or bought with currencies, priced by strength. Sets have a **set level** (e.g. level 0 = 0× multiplier, level 50 = 2×). **Merging** two sets makes one set with both multipliers, but resets the set level to 0. SE2 secret sets suggested.
- **Next planning rounds:** SE3 full custom enchant plan, then SE4 resource pack plan.
- **World:** WR1 warzone death keeps inventory and loses 2% money (max $1M), killer gets nothing; WR2 no piston/observer limits, detect lag machines only; WR3 natural iron golems off; WR4 phantoms off; **WR5 Nether/End become custom worlds for later content.**

# Round 3 follow-ups (2026-10-30)
- **`/wardrobe` replaces `/sets`** (same system: sets bought with gems/crates/currencies, set levels, merging).
- **`/loadout`:** saved presets (e.g. "Mining", "Farming", "Combat") that swap armor and pet in one action. Details in open questions below.
- **Island storage:** (a) yes — mastery delivery tasks can take items straight from island storage; (b) yes — daily take-out limit per role; (c) recall of items a kicked member took = **staff only** (staff tool, not for leaders).

## Loadout (decided)
- L1 a loadout holds **everything**: armor, pet and tools/hotbar.
- L2 **2 presets free**, more with a rank or bought with store credits.
- L3 swapped-out pieces go into the **wardrobe** (no inventory space needed).
- L4 swap anywhere **except in combat or the warzone**, **2 s cooldown**.
