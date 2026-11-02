# Owner answers, 1 Nov 2026 (verbatim from the Phoenix-Questions page + chat) and how they are resolved
Rule from the owner: **one big update; plan AND implement everything; deploy each lane when it is finished; do not forget anything.** Unanswered questions (A1.. HT-Q4, EP-Q4, EP-Q13, EP-Q20, EP-Q21, EP-Q24, EP-Q26, EP-Q27, EP-Q15 partly, V3-Q12, HT-Q9) use the SUGGESTED answer in the question JSON (feature-overview/questions/*.json).

## Chat answers to the tutorial questions (earlier turn)
1. "Remove the scoreboard Next: row?": **yes** during the tutorial (the boss bar is the one place); after the tutorial the one `Next:` goal stays (HT-Q6 a).
2. Full inventory at the chest: "all tools are per player ... the tool in inventory will NEVER leave the players inventory; right-clicking it opens a GUI with levels/tiers; the first GUI shows 'Island hoe' (automatically equipped when on the island) and 'Depth hoe' (automatically when in the Depths); these are different tools (island use vs Depths use)". => tools are bound to the profile, never drop/leave the inventory, restorable; each tool type has an Island version and a Depths version that swap automatically by world; a right-click GUI shows both with levels/tiers. (Replaces the pickaxe/`/box` overflow question: nothing to overflow.)
3. Keep the Forge camera: **yes**.
4. Guide NPC counts for Help: **no**; Help step must be **automatic, no NPC, use the `/tutorial` command instead**; clean it up (with HT-Q12: the empty Help step is replaced by a shop buy).
5. Requester may cancel their own waiting /eco self-change: **yes**.
6. Pip (earlier): more appealing name, cuter, perches on the chest, leads the player. (built in q/tutorial-fix)

## Spawner structure (chat, replaces EP-Q2 and EP-Q5 wording)
"4 tiers with 5 spawners in each tier and one 5th tier with the best spawners, 3 spawners." => tiers 1-4 earned (5 mob spawners per tier = 20), tier 5 premium (3 best spawners, sold by Black Market and /store). Per-island requirement to unlock the next tier: kill X (reasonable) of the previous tier's mobs; players can grind tiers at the same time.
## Shards (chat): "Shards only from blocks, no other rewards": the ONLY shard source is breaking a **shard block** on an island (sold in the credit shop and found in crates; right-click upgrades respawn speed and double chance). Every other shard payout is removed (pass, daily, milestones, crates...). Shard blocks themselves may be rewards.
## Release (chat): deploy each lane when it is finished.
## Credits: everything called "gems" is credits from now on. Finish the credit shop (buycraft/Tebex to buy credits; spend credits on ranks, shard blocks, etc.).

## Page answers (verbatim)
A1-Q1 a) Only after 6+ hours away [suggested]
A1-Q2 OTHER: use common sense and make it look natural and fitting, dont write "chat is logged for 30 days" etc, be careful and simple
A1-Q3 a) Toggle in /settings and the You menu [suggested] | note: command and in settings you menu
A4-Q1 b) Tools and food only
A4-Q2 a) No timer [suggested] | note: this is a plan b, there will no longer be a starter kit daily, the tools will NEVER get lost so make that instead. tools are per profile
A4-Q3 a) Same kit everywhere [suggested] | note: yes same kit everywhere but new items, make it easier and make more sense
A4-Q4 a) Stays bound forever [suggested]
C4-Q1 OTHER: be common sense and make sure we found a good useful solution to everywhere
C4-Q2 OTHER: we want everything to be higher prices, we talk higher numbers, we will plan the exact prices later
C4-Q3 a) Chat line, join line, HOT tag [suggested]
C4-Q4 a) Yes, add it [suggested] | note: yes add it and also in the tutorial make the "depth" light up with resource pack so its easier to see during tutorial
C4-Q5 a) No, selling always pays money [suggested]
C5-Q2 b) Keep exactly today's 253 | note: we need the uncraftable blocks and minus whats not useful and make people craft what they need
C5-Q3 a) No, fixed prices for now [suggested]
C6-Q1 a) Fixed price only [suggested]
C6-Q2 a) Yes, use /box [suggested]
C6-Q3 c) No pacing, only the 5 slots | note: no pacing and the better rank the more slots in /ah
C6-Q4 a) Add 7 days, same 1% fee [suggested]
EP-Q1 a) The tier [suggested] | note: suggested and also make sure the prices are round not 117 but instead 100k for example
EP-Q2 a) 12, one per Depths tier [suggested] | note: more tiers, faster progress, remember   (SUPERSEDED by the 4+1 structure above)
EP-Q3 OTHER: kind of, it will allow more upgrades the more you upgrade /IS UPGRADE and /is masteries and unlock better, and the different spawners have the same default settings, make it spawn every 10 second, 1 mob drops 1x loot
EP-Q4 NO ANSWER
EP-Q5 OTHER: if we have 4 tiers of spawners, + 5th (blackmarket and /store) make it kill x amount of tier 1 mobs to get to tier 2, (per island requirements) they can grind at same time, and make it reasonable x kills
EP-Q6 a) Black Market for shards [suggested] | note: shards and also gems, please work on finishing the credit shop reminder.: everything thats called gems is gonna be credits from now
EP-Q7 c) Bigger by upgrades only | note: the idea is that every progress on island makes the island perks and more better and also in the depth, and by doing depths you can upgrade even more in island and also all others. NEW THING: Shards are only possible to be achieved on islands with a so called "shard block" its gonna be sold in credit shop and in crates and this is a stone block and when broken it gives 1x shard, and you can upgrade it with right clicking it and make it respawn faster, and chance to give double etc plan and implement
EP-Q8 OTHER: we will have custom drops and it will say in shop, and btw also make sure the mine in depth, log and slayer actually drops to satchel and give /satchel command
EP-Q9 a) Gently: 60 h to 122 h [suggested]
EP-Q10 a) Phoenix life [suggested]
EP-Q11 a) Ashborn; Torchbearer, Flameheart, Firecrown [suggested]
EP-Q12 a) Even ladder [suggested] | note: also make sure to add buycraft to buy credits and set up the credit shop
EP-Q13 NO ANSWER
EP-Q14 OTHER: in tab `&e&lOWNER &fPouched`, and in chat the same with double arrow gray and white text   (staff rank words: OWNER &e&l, ADMIN &c&l, MOD &b&l, HELPER &a&l, BUILDER &6&l; name white; chat: `&e&lOWNER &fPouched &7»» &fmessage`)
EP-Q15 a) Automatically after the tutorial [suggested] | note: requirements are finishing tutorial and 30min gameplay and fix color
EP-Q16 a) Firewing and Pyrelord [suggested]
EP-Q17 a) Monthly cosmetic and weekly kit [suggested]
EP-Q18 a) Balanced [suggested]
EP-Q19 b) Longer and bigger | note: no crystals in tutorial these are gained by doing things on island
EP-Q20 NO ANSWER
EP-Q21 NO ANSWER
EP-Q22 OTHER: 500 island level maximum, each upgrading a % of the progress for island players such as for example a little faster general breaking blocks, chance to double drop, haste when holding a tool etc. plan and implement
EP-Q23 b) 2 / 4 / 8 / 15 / 30 minutes | note: often, but try to make it not noisy, it has to be simple and good and actually worth, they need to feel it, then have more kind of rewards think
EP-Q24 NO ANSWER
EP-Q25 a) Keep Ascension, smaller steps [suggested] | note: this has to be planned very good and you can do it
EP-Q26 NO ANSWER
EP-Q27 NO ANSWER
V3-Q1 b) Yes, full MC Hub | note: full mchub but a little different so we dont steal it, but mostly yes
V3-Q2 a) Yes, plaque and hidden empty slots [suggested]
V3-Q3 a) A soft tile behind each item [suggested]
V3-Q4 a) Cartoon letters, all caps [suggested]
V3-Q5 a) Yes, same layout, plain titles [suggested]
V3-Q6 a) Pick from the icon preview [suggested]
V3-Q7 a) Plain vanilla Nether Star [suggested]
V3-Q8 a) One wallet tile on spending screens [suggested]
V3-Q9 a) One panel colour for every menu [suggested]
V3-Q10 a) 4 rows, 12 tiles in four groups [suggested] | note: simple and good and easy to understand, SYNERGY BRO
V3-Q11 a) Centred tiles, detail shows next level [suggested] | note: like a mastery tree, it's written somewhere, make it better not as it is right now. now it's weird, we want a mastery tree and see different tiers to everything and click with crystals and requirements met for example break 1000 pumpkin (balance this so easy at first and more later) plan and implement best way
V3-Q12 NO ANSWER
V3-Q13 a) Podium, places 4 to 10, and you [suggested] | note: good podium and improve it more
V3-Q14 a) One group per row, centred, two pages [suggested] | note: be cautious and simple
V3-Q15 a) Both prices always, one Java hint [suggested]
V3-Q16 a) 6 lines, 36 characters, one action line [suggested]
V3-Q17 a) Vanilla sounds only [suggested]
V3-Q18 a) Scoreboard title and tab header only [suggested]
HT-Q1 a) Yes, logo-only font [suggested]
HT-Q2 a) Header above the list [suggested]
HT-Q3 a) All of them, by place [suggested]
HT-Q4 NO ANSWER
HT-Q5 a) Timer first [suggested]
HT-Q6 a) One Next goal always [suggested]
HT-Q7 a) Your 3 lines [suggested] | note: and make sure the tab shows staff players and sorts right with all ranks
HT-Q8 a) Rank word and name [suggested]
HT-Q9 NO ANSWER
HT-Q10 a) Split over the steps [suggested] | note: IMPORTANT: the rewarding system should be small rewards often and when hitting a milestone or has done something such as gotten level 50 or come to tier 2 etc, so they need unexpected rewards sometimes that really is gonna help them. We can't have only small rewards, we need to give them pleasure and the hate-love relationship to make them get yes
HT-Q11 a) Mining Lv 2, Island Lv 3 [suggested]
HT-Q12 a) Swap Help for a shop buy [suggested]
HT-Q13 a) Only with 2+ profiles [suggested]

## Resolutions / conflicts (developer)
- V3-Q1 reverses GUI-Q1/I9 v2 "no custom font": ALLOWED now (panels with plaque, hidden empty slots, cartoon title letters, logo on scoreboard/tab). Different from MC Hub (own art/shape/colours). Java with pack loaded only; Bedrock/no-pack = plain. Kill-switch config.
- EP-Q2/Q5 -> 4 tiers x 5 mobs + tier 5 (3 mobs) premium (above). EP-Q4 (cap) suggested default applies unless the planner finds a conflict with 5 spawners per tier.
- Shards: only from shard blocks (supersedes everything about shard payouts; C13 Black Market prices still in shards).
- EP-Q19 'no crystals in the tutorial' overrides any crystal reward in tutorial steps.
- EP-Q22 island level max 500 (was the old curve) with % perks per level.
- HT-Q10 reward philosophy: frequent small rewards + milestone rewards + unexpected surprise rewards (variable reward), still not noisy.
