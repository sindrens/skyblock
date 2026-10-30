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
- R1–R3 suggested. **R4 `/boosters` shows personal + global boosters; `/thanks` gives the starter 1 gem; saying "gg" after a store purchase gives the buyer… (1 gem)** (see follow-up). **R5 30-day calendar; a broken streak can be saved for 100 credits within 2 days.** R6, R7 suggested.
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
