# Review 3: a second look at the owner's answers

Date: 2026-10-30. Scope: every answer marked Suggested in `tools/owner_sheet/answers.json` (rounds 1-3, 347 answers), every Other answer that clashes with another answer or a principle, and the round-2/3 decisions in `DECISIONS_2026-10-30.md`. Checked against `OWNER_PRINCIPLES.md`, `ECONOMY_BALANCE.md`, `phases/PHASE-5/10`, `plans/*` and the module code/configs.

**44 items: 14 high, 30 medium.** On the page, *Suggested* = take the new, better option; *No* = keep what you picked. Data: `tools/owner_sheet/review3.json`.

## Top 10 risks

1. **R3-1** (Start money loop and alt funnel): Start money + unlimited profile delete/recreate + /pay after a 3-minute tutorial = an endless $1k-per-cycle money printer for alts.
2. **R3-2** (Bound keys, tradeable loot): Bound keys give tradeable loot (spawners, books, tokens), so alt votes, AFK and calendar keys still feed a main.
3. **R3-3** (AFK earners per IP): 3 AFK earners per IP (code and balance sheet assume 1) triples free daily keys per household and per alt farm.
4. **R3-4** (Spawner trading skips the price rise): Tradeable spawners let alt islands buy at base price and skip the +10% price rise, the main brake on spawner compounding.
5. **R3-5** (Notes for every currency): Notes for every currency make gems (real money) tradeable for in-game money and wagerable, and break 'crystals not tradeable'.
6. **R3-6** (/eco needs approval): /eco relies on an alert after the fact; a hacked admin plus unlimited /pay spreads $1T before anyone reacts.
7. **R3-7** (Paid sell and XP bonus): Paid ranks with +15% sell / +10% XP break Mojang's commercial usage guidelines (blocklist risk); decide now, not after people pay.
8. **R3-8** (Monthly keys from paid ranks): Monthly crate keys from paid ranks are paid loot boxes, against your own 'keys are earned only' rule and loot-box laws.
9. **R3-9** (Private messages as evidence): Private messages are never evidence, so grooming, threats and scams in DMs can't be punished (clashes with your 'severe' rule 520).
10. **R3-10** (Mini-boss group rewards): Mini-boss: 10 players with 10% damage each get full rewards while health is capped at +100%, 5x better than solo and an alt farm.

## Contradictions found (two answers that can't both be true)

- **589 vs 590** island bank withdraw rights (R3-11)
- **550 vs 131/656** 30-minute AH block vs 'no 30-minute limit' (R3-16)
- **247 vs I13/MA11** no personal homes vs 1 home per member + Homestead mastery (R3-17)
- **811 vs PHASE-10 M10.4** 10 fragments for any key vs 3 fragments for a Phoenix key (R3-18)
- **513 vs 520/319** PMs never evidence vs severe bans for threats/child safety, and PMs logged for staff (R3-9)
- **1031 vs balance sheet §7.5 / PHASE-10 anti-abuse / AfkConfig** 3 earners per IP vs one per IP (R3-3)
- **RTA-17 vs 'keys are earned only'** (DESIGN §9, PHASE-10) (R3-8)
- **666 vs CR3 / ECONOMY §9.2** notes of every currency vs crystals and gems not tradeable (R3-5)

## Full list (ranked by impact)

### R3-1 · Start money loop and alt funnel (high, before launch)
- **Area:** Economy · **Original:** 121, 121b, P9, 656, TUT-4, TUT-7
- **Question:** Should a profile only send money (/pay, notes, trade, AH) after earning $10k itself, not counting start money or rewards?
- **Suggested:** Yes, unlock after $10k earned
- You picked: every profile gets $1k, delete and recreate without limit, /pay unlocks after the 3-minute tutorial
- **Why:** An alt with two slots can create a profile, /pay the $1k away, delete it and repeat forever: a money printer that also pastes a new island each time. Earning $10k takes a real player about 10 minutes and kills the loop.

### R3-2 · Bound keys, tradeable loot (high, before launch)
- **Area:** Economy · **Original:** P10-6, P10-13, P10-17, 1028
- **Question:** Should rewards from bound keys (votes, AFK pool, calendar, playtime, rank claims) also be bound to the profile?
- **Suggested:** Yes, bound key gives bound loot
- You picked: bound keys, but the spawners, books, tomes and set tokens inside them are tradeable
- **Why:** Binding the key does nothing if the spawner it gives can be traded: alts vote and AFK, open their keys and trade the loot to a main. Keys earned by playing (quests, pass, events) stay tradeable.

### R3-3 · AFK earners per IP (high, before launch)
- **Area:** AFK pool · **Original:** 1031, RW3
- **Question:** Should only one account per IP earn AFK pool keys, while money and fuel still go to up to 3?
- **Suggested:** Yes, keys for 1 account per IP
- You picked: 3 accounts per IP, each with its own 8 hours (the code and balance sheet assume 1)
- **Why:** Three AFK accounts per connection triple the free keys (2 Ember, Cinder and Blaze a day each), the cheapest alt farm on the server. Families still get money and fuel.

### R3-4 · Spawner trading skips the price rise (high, before launch)
- **Area:** Stacking & spawners · **Original:** 988, 642
- **Question:** Should spawners bought in /shop be bound to the island that bought them, with only found spawners tradeable?
- **Suggested:** Yes, shop spawners bound to island
- You picked: all spawners tradeable, plus +10% shop price per spawner the island owns
- **Why:** The +10% rise is the main brake on spawner compounding (balance sheet R10). A fresh alt island buys at base price and trades them to the main, so the brake never works.

### R3-5 · Notes for every currency (high, before launch)
- **Area:** Economy · **Original:** 666, 660, CR3
- **Question:** Should notes exist only for money and shards, keeping crystals and gems untradeable and never wagered in duels?
- **Suggested:** Yes, money and shard notes only
- You picked: notes of all currency types, tradeable by dropping, /trade and wagered in /duels
- **Why:** Gem notes turn real-money gems into in-game money (pay-to-win, Mojang rule risk), and wagering paid gems is gambling. Crystal notes break your rule CR3 and the enchant balance.

### R3-6 · /eco needs approval (high, before launch)
- **Area:** Economy · **Original:** 631, 630
- **Question:** Should /eco changes above a set amount need approval from the owner or a second admin before they apply?
- **Suggested:** Yes, two-person approval above a limit
- You picked: an owner alert after the fact, no second confirm
- **Why:** A hacked or rogue admin can give $1T, and /pay has no limit, so the money spreads in minutes before you read the alert. Approval before, not after, stops it.

### R3-7 · Paid sell and XP bonus (high, before launch)
- **Area:** Ranks, perks & cosmetics · **Original:** RTA-14, RTA-16, RTA-1, 389
- **Question:** Should bought ranks give convenience and cosmetics only, with sell and skill XP bonus set to 0 now?
- **Suggested:** Yes, 0% for bought ranks
- You picked: up to +15% sell and +10% skill XP on bought ranks, checking Mojang's rules later
- **Why:** Mojang's commercial usage guidelines forbid selling gameplay advantages, and breaking them can get the server blocklisted. Deciding now keeps the balance sheet right instead of removing perks people already paid for; won ranks can keep bonuses.

### R3-8 · Monthly keys from paid ranks (high, before launch)
- **Area:** Ranks, perks & cosmetics · **Original:** RTA-17
- **Question:** Should paid ranks give a monthly cosmetic reward instead of crate keys?
- **Suggested:** Yes, cosmetics, no keys
- You picked: monthly Ember, Cinder and Blaze keys for donor ranks after 5 h played
- **Why:** Keys from a paid rank are paid loot boxes: against your own 'keys are earned only' rule, Mojang's rules and loot-box laws (Belgium, Netherlands). These crates hold spawners and books.

### R3-9 · Private messages as evidence (high, before launch)
- **Area:** Staff & punishments · **Original:** 513, 520, 319
- **Question:** Should reported private messages count as evidence for threats, child safety, doxxing and scams?
- **Suggested:** Yes, when reported or severe
- You picked: private messages are never punishment evidence
- **Why:** Grooming, threats and scam deals happen in private messages, so your own 'severe' rule (520) can't work without them. Only reported messages, with the reason logged.

### R3-10 · Mini-boss group rewards (high, before launch)
- **Area:** The Depths · **Original:** 770, 797
- **Question:** Should mini-boss health grow +25% for every player who earns rewards, with no cap, so rewards match effort?
- **Suggested:** Yes, health scales with no cap
- You picked: full rewards for everyone with 10% damage, but boss health capped at +100%
- **Why:** With 10 players the boss has 2x health but drops 10 full rewards, 5x better than solo and an easy alt farm. Uncapped health keeps groups fun and fair to solo players.

### R3-11 · Island bank withdrawals (high, before launch)
- **Area:** Islands · **Original:** 589, 590, N2-1
- **Question:** Should only the Leader and Admin withdraw from the island bank, with no daily withdrawal for Mods or Members?
- **Suggested:** Yes, Leader and Admin only
- You picked: both 'Mod 25%, Member 10% a day' (589) and 'withdraw Admin only' (590)
- **Why:** The two answers contradict each other. With island joins now unlimited, a scammer can join, take 10% of the bank and leave in the same minute.

### R3-12 · Kicked members lose everything (high, before launch)
- **Area:** Islands · **Original:** 141, 251
- **Question:** Should kicked members (not those who leave) keep their inventory, money and skills on a fresh solo island?
- **Suggested:** Yes, a kick keeps personal progress
- You picked: leaving or being kicked wipes the profile; staff can restore a backup
- **Why:** One click by a leader can wipe months of a member's play: a common scam and a flood of staff tickets at 300 players. Leaving by choice can still wipe.

### R3-13 · Spawner lost after a crash (high, before launch)
- **Area:** Stacking & spawners · **Original:** 1006
- **Question:** After a crash, should a spawner missing from the database be disabled and flagged for staff instead of deleted?
- **Suggested:** Yes, disable and flag for staff
- You picked: remove the spawner block
- **Why:** A 512-stack can be worth billions, and deleting it breaks 'nothing is ever lost by accident'. A disabled block can't be duped, and staff restore it from the audit log.

### R3-14 · Tokens from paid skins (high, before launch)
- **Area:** Tools, skills & enchants · **Original:** 836, 835, P10-26
- **Question:** Should only skins won in play become tradeable tokens, keeping gem-bought and premium-pass skins bound to the account?
- **Suggested:** Yes, paid skins stay bound
- You picked: every skin can become a tradeable skin token
- **Why:** A gem or premium skin sold on the AH turns real money into in-game money, which your plans forbid (gems never become money). Skins earned in play can still be traded.

### R3-15 · AFK pays more keys than playing (medium, before launch)
- **Area:** AFK pool · **Original:** 1028, O10
- **Question:** Should the 8-hour AFK reward be a Cinder key, so standing still never earns more keys than weekly quests?
- **Suggested:** Yes, 8 h gives a Cinder key
- You picked: Ember at 1 h and 3 h, Cinder at 6 h, Blaze at 8 h, every day
- **Why:** AFK gives 7 Blaze keys a week, while all 5 weekly quests (about 4 h of play) give 1. Paying idle time more than play hurts fun and fills the pool with alts.

### R3-16 · Old 30-minute AH rule (medium, before launch)
- **Area:** Economy · **Original:** 550, 131, 656, TUT-4
- **Question:** Should the old 30-minute auction house block (550) be dropped, so the tutorial locks are the only gate?
- **Suggested:** Yes, tutorial locks only
- You picked: 550 'under 30 minutes blocked from the AH' and 131 'remove the 30-minute limit'
- **Why:** These two rules contradict, and the code may end up with both. One clear gate (the tutorial plus the earning rule in R3-1) is easier to explain.

### R3-17 · Member homes (medium, before launch)
- **Area:** Islands · **Original:** 247, I13, MA11
- **Question:** Should each member get 1 personal island home, with more from the Homestead mastery?
- **Suggested:** Yes, 1 home, more from mastery
- You picked: both 'no personal homes, remove /is setmyhome' (247) and '1 home per member' (I13)
- **Why:** The answers contradict, and setmyhome was already removed from the code. The Homestead mastery you approved (MA11) needs homes to exist.

### R3-18 · Key fragment count (medium, before launch)
- **Area:** The Depths · **Original:** 811, P10-1
- **Question:** Should key fragments follow the crate plan, where 3 fragments make 1 Phoenix key, instead of 10 for any key?
- **Suggested:** Yes, 3 fragments make a Phoenix key
- You picked: 10 fragments for a crate key (811), while the approved crate plan says 3 for a Phoenix key
- **Why:** There are two rules for the same item. Fragments only drop in tiers 11+, so 3 for a Phoenix key fits that rarity.

### R3-19 · Island size for gems (medium, before launch)
- **Area:** Islands · **Original:** N2-2, P5-14, P10-38, 157
- **Question:** Should island sizes above 9x9 come only from the Black Market for shards, not for gems?
- **Suggested:** Yes, shards only
- You picked: 11x11 to 15x15 for 250, 500 or 750 gems, or for shards
- **Why:** With no block or hopper limits, more space means more farms and more money: paid progress and a Mojang rule risk. Shards are earned, so big islands stay a goal.

### R3-20 · Gem sets with real bonuses (medium, before launch)
- **Area:** Wardrobe & sets · **Original:** 958, 959
- **Question:** Should gem sets be looks only (skins worn over earned sets), with their bonus versions from crates and crystals?
- **Suggested:** Yes, gem sets are looks only
- You picked: 3 gem sets with real bonuses, kept as Sigils and claimed free every season on every profile
- **Why:** A paid set with skill XP or foraging bonuses that comes back free every season on 3 profiles is paid power. Looks only keeps gem sales safe and still sells.

### R3-21 · Custom mob drops from spawners (medium, before launch)
- **Area:** Economy · **Original:** 693, 641
- **Question:** Should the new custom mob drops (like sellable emeralds) never come from spawner mobs?
- **Suggested:** Yes, no custom drops from spawners
- You picked: every mob drops a custom item (1, more with looting), and emeralds become sellable
- **Why:** Spawners are already the biggest money risk in the balance sheet. An extra sellable drop on every spawner mob raises their income far past the model.

### R3-22 · Mining with a full satchel (medium, before launch)
- **Area:** The Depths · **Original:** 748, 769, N2-3
- **Question:** With a full satchel, should drops go to the inventory, and breaking stop only when the inventory is full too?
- **Suggested:** Yes, inventory, then stop breaking
- You picked: keep breaking with a full satchel and warn every 3 s (where the drops go is unclear)
- **Why:** If drops go to /box (never expires, no hard cap), satchel size and its upgrades mean nothing and the database grows without end. If they vanish, it breaks 'nothing is lost'.

### R3-23 · Forge takes from island storage (medium, before launch)
- **Area:** Tools, skills & enchants · **Original:** 891, storage (b)
- **Question:** Should the Forge use island storage items only within your role's daily take-out limit?
- **Suggested:** Yes, counts toward the take-out limit
- You picked: the Forge takes materials from inventory, satchel and island storage together
- **Why:** Otherwise a new member can spend the team's whole storage on a personal tool, bypassing the daily take-out limit you chose.

### R3-24 · Combat XP from island mob farms (medium, before launch)
- **Area:** Tools, skills & enchants · **Original:** 919, 874
- **Question:** Should Combat XP come only from Depths combat zones, like Mining and Foraging XP?
- **Suggested:** Yes, Depths only
- You picked: Combat XP from any mob not from a spawner, island mob farms included
- **Why:** Dark-room farms on islands with mob stacking and stack-kill enchants give huge XP per swing with no danger, skipping the combat tiers. Depths-only keeps the three skills fair.

### R3-25 · Interest activity rule counts AFK (medium, before launch)
- **Area:** Economy · **Original:** 597, 195
- **Question:** Should bank interest need 2 hours of active play in 7 days, with AFK pool time not counting?
- **Suggested:** Yes, active time only
- You picked: 2 hours in 7 days, AFK time counted
- **Why:** With AFK counted, an alt can stand in the pool 2 hours a week and collect interest all season. The rule exists to stop exactly these idle banks.

### R3-26 · Total mob stacks per chunk (medium, before launch)
- **Area:** Stacking & spawners · **Original:** S12, SK1
- **Question:** Should every chunk also have a total cap on stacked mobs of all types together, for example 24 stacks?
- **Suggested:** Yes, a total cap per chunk
- You picked: 8 stacks per mob type per chunk, and they never despawn
- **Why:** With about 40 mob types that allows hundreds of never-despawning stacks per chunk and thousands per island. A total cap protects the server's speed with 300 players.

### R3-27 · Hopper safety net (medium, before launch)
- **Area:** Worlds & builds · **Original:** O1, O2, WR2
- **Question:** Should there be a high hopper and piston cap per chunk as a safety net, on top of lag-machine detection?
- **Suggested:** Yes, a high cap, e.g. 64 hoppers
- You picked: no block, hopper, piston or observer limits; detect lag machines only
- **Why:** Hoppers are the top lag source on big skyblock servers, and detection only reacts after the lag. A cap that normal builds never reach costs players nothing.

### R3-28 · Auto-freeze on detected dupes (medium, before launch)
- **Area:** Staff & punishments · **Original:** 458
- **Question:** Should the system freeze a balance automatically when it detects a duplicated note or unique item, with staff deciding after?
- **Suggested:** Yes, auto-freeze on system detection
- You picked: staff click the freeze themselves
- **Why:** With /pay unlimited, duped money spreads in minutes, often while no staff is online. Freezes after player reports can stay manual.

### R3-29 · No confirm on expensive quick-buys (medium, before launch)
- **Area:** Economy · **Original:** 8, 642
- **Question:** Should quick-buy ask for confirmation only for spawners and purchases over $1M?
- **Suggested:** Yes, confirm spawners and over $1M
- You picked: no confirmation on any quick-buy
- **Why:** A right-click buys 64, and spawners cost $117k to $2M each and rise per purchase. One misclick can spend a week's money, which feels terrible.

### R3-30 · Disband with one click (medium, before launch)
- **Area:** Islands · **Original:** 261, 591
- **Question:** Should disbanding need a second confirm screen with the button in a different slot, since it wipes the profile?
- **Suggested:** Yes, two-step confirm
- You picked: one confirm button is enough
- **Why:** Disband deletes the island, the bank and the profile's progress. A two-step menu stops misclicks and still needs no typing on Bedrock.

### R3-31 · /report for new players (medium, before launch)
- **Area:** Staff & punishments · **Original:** 539
- **Question:** Should /report work from the first minute, with a per-account rate limit instead of 10 minutes of playtime?
- **Suggested:** Yes, rate limit, no wait
- You picked: 10 minutes of playtime before /report works
- **Why:** New players are the main targets of scams and harassment, and they can't report in their first minutes. A rate limit stops spam without blocking victims.

### R3-32 · Private messages during the tutorial (medium, before launch)
- **Area:** Social & chat · **Original:** TUT-5
- **Question:** Should /msg to strangers and links in chat unlock at the tutorial finish, with public chat open from the start?
- **Suggested:** Yes, /msg and links after the tutorial
- You picked: public chat and /msg open from the first second
- **Why:** Spam bots and groomers message fresh accounts first. The tutorial takes under 3 minutes, so real players barely notice.

### R3-33 · Top rated islands boosted by alts (medium, before launch)
- **Area:** Islands · **Original:** 282, 292
- **Question:** Should 'top rated' need ratings from at least 10 different IP addresses instead of 3 ratings?
- **Suggested:** Yes, 10 different IPs
- You picked: 'top rated' from 3 ratings, and up to 3 accounts per IP may rate
- **Why:** One person with 3 accounts can put their own island on the top rated list. Different IPs make the list mean something.

### R3-34 · Full combat zones (medium, before launch)
- **Area:** The Depths · **Original:** 824
- **Question:** When a combat zone reaches 40 players, should a second copy open instead of showing 'zone full'?
- **Suggested:** Yes, open a second copy
- You picked: a cap of 40 players with a 'zone full' message
- **Why:** 'Zone full' means waiting, against your no-waiting rule, and 30 mobs for 40 players means kill stealing. A second copy keeps both smooth.

### R3-35 · Idle players hogging slots (medium, before launch)
- **Area:** AFK pool · **Original:** 1021, 1033
- **Question:** Should players who reached the daily 8-hour AFK cap be kicked when idle, like everywhere else?
- **Suggested:** Yes, idle kick after the cap
- You picked: idle kick after 20 minutes, but never inside the AFK pool
- **Why:** After the cap they earn nothing but still hold a slot; on a full 300-player night real players can't join.

### R3-36 · Crowded AFK pool in the Tavern (medium, before launch)
- **Area:** AFK pool · **Original:** 1020, 412
- **Question:** Should the AFK pool be its own area outside the Tavern, with players inside hidden from each other?
- **Suggested:** Yes, own area, players hidden
- You picked: no player cap, with the pool inside the Tavern
- **Why:** Up to 300 players standing in one building lags phones and Bedrock and blocks the Quest and Vote NPCs. Hiding bodies is cheap.

### R3-37 · Day-3 money target (medium, before launch)
- **Area:** Economy · **Original:** 651
- **Question:** Should the price curve be tuned to reach about $5M on day 3, your own target, instead of $15M?
- **Suggested:** Yes, tune to $5M
- You picked: the smooth curve that reaches about $15M on day 3
- **Why:** Three times your target in the first days pushes AH prices up early and leaves later joiners behind. The balance sheet already flags day 3 as the one target it misses.

### R3-38 · Trade value hints can be faked (medium, before launch)
- **Area:** Economy · **Original:** 736, 737
- **Question:** Should trade value hints use server sell and shop prices, and the AH median only with many different sellers?
- **Suggested:** Yes, estimates that can't be pumped
- You picked: an estimated value from the 7-day AH median as a hover line
- **Why:** A scammer can wash-trade with an alt to pump the median, so the hint says his junk is worth millions. Safe sources make the red warning trustworthy.

### R3-39 · Staff play on the same account (medium, before launch)
- **Area:** Staff & punishments · **Original:** 334
- **Question:** Should admins with /eco, /give or creative play on a separate account instead of their staff account?
- **Suggested:** Yes, separate play account
- You picked: staff play on the same account with powers off and big transfers flagged
- **Why:** One account with both powers and a play profile makes it easy to slip staff money or items into play. A separate account makes every transfer visible.

### R3-40 · Note creation spam (medium, before launch)
- **Area:** Economy · **Original:** 665
- **Question:** Should making money notes have a soft rate limit, for example 20 notes per minute?
- **Suggested:** Yes, 20 per minute
- You picked: notes from $100 with no fee and no wait between notes
- **Why:** Every note is a unique database row. A macro could create a million $100 notes and bloat the database; real players never hit 20 a minute.

### R3-41 · Free bank withdrawal window (medium)
- **Area:** Economy · **Original:** 588
- **Question:** Should the 2% bank withdrawal fee always apply, lowered by Treasury, without the free 10 minutes after each payout?
- **Suggested:** Yes, no free window
- You picked: 2% fee, except 10 free minutes after each payout
- **Why:** Players who know the trick time every withdrawal (busywork) and only casual players pay. The lowest-balance interest rule already stops payout gaming.

### R3-42 · Warzone kills give nothing (medium)
- **Area:** Worlds & builds · **Original:** WR1
- **Question:** Should a warzone killer get half of the victim's 2% money loss, once per pair per hour?
- **Suggested:** Yes, half to the killer
- You picked: the victim loses 2% and the killer gets nothing
- **Why:** A PvP zone with no reward for kills gives little reason to fight. A victim with an empty purse gives nothing, so alts can't farm it.

### R3-43 · Thanks by typing in chat (medium)
- **Area:** Ranks, perks & cosmetics · **Original:** RW2, R4
- **Question:** Should booster thanks work through /thanks or a clickable [Thanks] button, not by typing 'thanks' in chat?
- **Suggested:** Yes, button and /thanks only
- You picked: /thanks or typing 'thanks' in chat gives the starter 1 gem
- **Why:** With 300 players every booster floods chat with 'thanks'. A button is one tap on Bedrock and keeps chat readable.

### R3-44 · Premium pass extra reroll (medium)
- **Area:** Ranks, perks & cosmetics · **Original:** P10-26
- **Question:** Should the premium battle pass drop the extra daily quest reroll and stay cosmetic only?
- **Suggested:** Yes, cosmetics only
- You picked: premium cosmetics plus 1 extra daily reroll
- **Why:** A paid reroll means more quest money and keys for payers, a small gameplay advantage under Mojang's rules. Everything else on premium is already clean.

## Checked and left alone

Most Suggested answers hold up. Examples that looked risky but are fine: drop-to-own-profile transfers (already blocked by `ProfileGuards` drop tags), gem sets becoming money (WD7 binds claimed sets), island level bought with money (log-scale bank, square-root block worth, 20% cap), team vs solo boards (separate boards, members' skills averaged), spawners at 1 mob per cycle (641) and the +10% price rise (642), premium pass without keys (P10-26 apart from the reroll), crate pity and odds shown, blueprint and book drop rates, satchel take-out only, and the staff permission split.
