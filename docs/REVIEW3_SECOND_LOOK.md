# Review 3: a second look at your answers (v2, checked against the code and the live server)

Date: 2026-10-30, checked against update95 (`main` on GitHub) and the live test server configs pulled from the VPS at 21:06.

**v2 vs v1:** every one of the 44 items was checked by reading the code, the live configs, your original answers and notes, and the newer answers on the Decisions page.
- **4 are already settled** (built, or already working the way the item asks): R3-13, R3-16, R3-31, and the kick half of R3-12. You only confirm them.
- **13 had wrong facts or numbers**, now fixed: R3-3, R3-9, R3-11, R3-12, R3-21, R3-22, R3-26, R3-29, R3-37, R3-38, R3-40, R3-43, R3-44.
- **Most Suggested options are improved** (simpler, safer, or fixed to match the facts). The biggest changes are marked *(changed)* in the title. 2 items are split in two (R3-5, R3-9).
- **R3-17 is replaced** by the open question P5-21, which keeps your "no personal homes".
- **9 things found while checking** need no decision, only your OK (bottom of this file).

## How to answer
Tick one box per item, or reply in chat like `1 S, 2 S, 3 N, 4 O: <text>` (S = Suggested, N = No/keep yours, O = Other).
- **Suggested** = take the new option. **No** = keep what you picked. **Other** = write what you want.
- "Today" = what the test server does right now.

---

## High (decide before launch)

### R3-1 · Start money loop and alt funnel
**Question:** Should a profile only send money away (/pay, notes, /trade, AH buying, island bank deposit) after it has earned $10k itself? Start money, rewards and money from other players don't count.
- **Suggested:** Yes, unlock after $10k earned by the profile itself.
- **You picked:** every profile gets start money (121), no limit on delete and recreate (121b, P9), /pay opens at tutorial step 11 (TUT-4).
- **Today:** built as you picked. $1,000 start money per profile. The tutorial is per account, so a second profile can /pay right away. A profile can only be deleted when it isn't active, so the loop needs a second slot (500 gems or a rank).
- **Why:** a fresh account gets $1,000 + the $2,500 tutorial reward, about $3,500 every 3 minutes to send to a main. The repeat-transfer alert only starts at $10k, so small loops are invisible.
- **Changed in v2:** the island bank deposit is added (it was a way around the rule), and only money the profile made itself counts.
- **Downside:** a new player can't gift money or buy on the AH for about his first 10 minutes. **Cost:** medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-2 · Bound keys, tradeable loot
**Question:** Should loot from bound keys (votes, AFK pool, calendar, playtime, rank claims) also be bound to the profile? Keys earned by playing (quests, pass, events) stay tradeable.
- **Suggested:** Yes. Also: a key found inside a bound crate (for example the 3% Cinder key in Ember) is bound too, and a spawner from a bound key goes straight onto your island's spawner stack.
- **You picked:** bound keys (P10-6, P10-13, P10-17, 1028), while spawners, books, tomes and set tokens are tradeable (833, 869, 812, 988).
- **Today:** crates aren't built, and there is no "bound item" tag yet.
- **Why:** binding the key does nothing if what's inside can be traded. Alts vote and AFK, open the keys and send the loot to a main.
- **Downside:** spare vote/AFK books and tokens can't be sold. **Cost:** large (one bound-item system shared by AH, trade, /box, /sell and stacking; R3-4 and R3-14 need it too).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-3 · AFK earners per connection *(facts fixed)*
**Question:** Keep it as built: one account per internet connection earns at a time (money, fuel and keys), 8 hours per connection per day, and staff can whitelist a family?
- **Suggested:** Yes, keep as built and add the family whitelist.
- **You picked:** 1031 Suggested. Its note said "families on one connection share the 8 hours".
- **Today:** this is already how it works (`afk.yml one-per-ip: true`, `ip-daily-cap-minutes: 480`). v1 said 3 accounts per IP, which was wrong. The pool itself is closed until the build has an `afk_pool` area.
- **Check before launch:** Bedrock players must reach the server with their own IP through Geyser. Otherwise every Bedrock player counts as one connection.
- **Downside:** siblings take turns. **Cost:** small (whitelist only).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-4 · Spawner trading skips the price rise
**Question:** Should spawners bought in /shop be bound to the island that bought them, with the +10% price counting every spawner the island has bought (not only the placed ones)? Spawners found in play or from crates stay tradeable.
- **Suggested:** Yes.
- **You picked:** all spawners tradeable (988), +10% shop price per spawner the island owns (642).
- **Today:** not built. There are no spawners in /shop yet. The AH blocks spawners, but /trade allows them.
- **Why:** the +10% rise is the main brake on spawner money. A fresh alt island buys at base price and passes them on. Counting only placed spawners lets anyone buy a pile at base price before placing.
- **Note:** `planning/SHOP.md` Q5 asks the same thing; this answer settles both.
- **Downside:** no gifting or reselling shop spawners. **Cost:** medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-5a · Gems can never be traded *(split from R3-5)*
**Question:** Gems can never be noted, traded, dropped or wagered (hard rule in the code).
- **Suggested:** Yes.
- **You picked:** "all types [of notes], and can be traded by dropping to players or /trade or wagered in /duels" (666). Also: "...we need it so people wont be boosting, we need a solution" (660).
- **Today:** only money notes (`economy.yml notes.currencies: [money]`). Gems and crystals aren't tradeable, shards are.
- **Why:** gems come from the store (and 3 per vote). Trading them turns real money into in-game money, which is pay-to-win and a risk under Mojang's rules. Wagering them is gambling.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-5b · Crystals and shard notes *(split from R3-5)*
**Question:** Crystals stay untradeable (your CR3), and shards get notes?
- **Suggested:** Yes. Crystals untradeable, shard notes on (one config line).
- **Other option (B in `planning/CURRENCIES_TRADING_BOOSTING.md`):** crystals tradeable with 10% burned on every trade and a weekly limit on how much one player can receive.
- **Note:** this decides most of planning topic 5.
- **Your answer:** [ ] Suggested [ ] Option B [ ] Other:

### R3-6 · /eco needs your OK above a limit
**Question:** Should /eco above $1M per command, or above a daily total per admin, wait for your OK (one click in the alert)? Below that it works at once.
- **Suggested:** Yes, $1M per command or a daily total per admin.
- **You picked:** /eco for owner + admin (630); "owner alert is enough" (631).
- **Today:** /eco runs at once with no limit. The alert goes to online staff and the console. The big-money alert only fires above $50M or 0.5% of all money, so a $40M give isn't even flagged. Discord alerts are off (no webhook yet).
- **Changed in v2:** "a second admin" is replaced by "you", because the staff team is small (631).
- **Downside:** admin prizes over the limit wait for you. **Cost:** medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-7 · Paid sell and XP bonus
**Question:** Start bought ranks at 0% sell and 0% skill XP bonus (a config switch), and turn it on only if Mojang's rules allow it? Ranks won in play keep their bonuses. The same rule applies to store boosters and weekly-kit fuel.
- **Suggested:** Yes.
- **You picked:** "both to a degree its donations" (389). Up to +15% sell / +10% XP (RTA-14, RTA-16). Check Mojang before the store opens and set 0 if forbidden (RTA-1).
- **Today:** no paid ranks exist yet (only the free Ember rank).
- **Why:** Mojang's server rules don't allow selling gameplay advantages. Starting at 0 means nobody loses a perk they paid for. The balance sheet then also matches what really happens.
- **Cost:** small (docs + rerun the economy model).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-8 · Monthly keys from paid ranks
**Question:** Should bought ranks give a monthly cosmetic instead of crate keys? Season ranks won in play keep their monthly keys.
- **Suggested:** Yes.
- **You picked:** Immortal gets 4 Ember + 2 Cinder + 1 Blaze keys a month after 5 h played (RTA-17).
- **Why:** keys from a paid rank count as paid loot boxes. That breaks your own rule "keys are earned only" (DESIGN §9) and is a legal risk in some countries. Cinder and Blaze crates hold tier 3-4 spawners.
- **Downside:** donors lose a small monthly gift. **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-9a · Private messages in reports *(facts fixed, split)*
**Question:** When someone uses /report on a player, should the last 10 private messages between those two be attached as evidence?
- **Suggested:** Yes, only through /report.
- **You picked:** "public chat only" as evidence (513), plus a 'severe' reason for threats and child safety (520).
- **Today:** private messages are already logged for 30 days, and /rules already tells players staff can read them. Staff can read and act on them already. The only blocked part is attaching them as evidence. v1 said "they can't be punished", which was wrong.
- **Why:** threats, grooming and scams happen in private messages, so your severe rule (520) needs the proof.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-9b · Helpers reading private messages *(found while checking)*
**Question:** Hide private messages from helpers in /logs (mods and up keep them)?
- **Suggested:** Yes. This is what your 318 says ("Helpers see private messages only through a report"). Today helpers can read every private message in /logs.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-10 · Mini-boss group rewards *(facts fixed)*
**Question:** Mini-boss health +25% per extra player, up to 10 players, plus the world-boss anti-alt rules (must have hit in the last 90 s, not AFK, max 3 accounts per IP)?
- **Suggested:** Yes.
- **You picked:** full rewards for everyone with 10% damage (770); health +25% each, max +100% (797).
- **Today:** full rewards at 10% damage is built. Health scaling isn't built, so boss health is fixed.
- **Why:** with 10 players a boss drops 10 full rewards. v1's "no cap" can't really happen (10% each means at most about 10 players qualify), and more health alone doesn't stop alts. The anti-alt rules do.
- **Downside:** big groups take longer per boss. **Cost:** small-medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-11 · Island bank withdrawals *(facts fixed, less urgent)*
**Question:** Lock island bank withdrawals to Leader and Admin, so a leader can't give the right to Mods or Members? Also give Admin a daily limit (for example 50%)?
- **Suggested:** Yes to both.
- **You picked:** both "Mod 25%, Member 10% a day" (589) and "withdraw Admin only" (590).
- **Today:** only Leader and Admin can withdraw by default. Mods and Members can only if the leader turns it on, and then they get 25% / 10% a day. Joining needs an invite. So the two answers don't clash in the code, and v1 overstated the risk.
- **Why:** the real gap is that Admin has no limit at all.
- **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-12 · Leaving an island *(kick part settled)*
**Settled:** a kick keeps the member's money, items and skills. Only the membership is removed. This was built in SECFIX1 (kick backups restore the membership only, which closed a dupe).
**Question:** Your 141 says leaving by choice wipes your profile progress. Today leaving keeps everything. Keep leaving safe too?
- **Suggested:** Yes, keep leaving safe. A wipe never stops a fresh alt, and the 24 h join cooldown already slows island hopping.
- **You picked:** "Leaving an island = you lose everything (profile progress)" (141).
- **Downside of building the wipe:** large (skills and Depths have no backup), plus support tickets.
- **Your answer:** [ ] Suggested [ ] No (build the leave wipe) [ ] Other:

### R3-13 · Spawner lost after a crash *(settled)*
**Already built (SECFIX1):** a stacked spawner whose block is gone is frozen and logged for staff, never deleted.
**Two small follow-ups:**
- (a) spawners on an island you disband or reset on purpose are cleared, not frozen for staff;
- (b) a staff restore button instead of `/spawner give`.
- **Your answer:** [ ] OK, do both [ ] Other:

### R3-14 · Paid skins, tags and sets stay bound *(wider)*
**Question:** Everything bought with gems or store credits (skins, tags, sets) stays bound to the account. Only things earned in play can become tradeable tokens.
- **Suggested:** Yes.
- **You picked:** every skin can become a tradeable skin token (836, 835).
- **Today:** skins remember where they came from (`skins_owned.source`). Store tags already arrive as tradeable items that can be sold on the AH, and that would change.
- **Why:** a paid item sold on the AH turns real money into in-game money.
- **Downside:** a paid skin or tag can't be gifted. **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

---

## Medium

### R3-15 · AFK and Blaze keys
**Question:** Should the AFK pool never give Blaze keys (8 h gives a Cinder key)?
- **Suggested:** Yes.
- **You picked:** Ember at 1 h and 3 h, Cinder at 6 h, Blaze at 8 h, every day (1028).
- **Fixed in v2:** after this AFK still gives 2 Cinder a day, more than quests. Most of the abuse is closed by R3-2 + R3-3. This only removes the rare Blaze.
- **Cost:** small (one line in `afk.yml`).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-16 · Old 30-minute AH rule *(settled)*
**Already built:** the tutorial is the only gate (the AH opens at step 8). The old 30-minute rule isn't used anywhere.
**Follow-up:** delete the dead settings (`auction.yml` new-account min-playtime, `economy.yml` pay/notes new-account-minutes) and the old lines in the docs.
- **Your answer:** [ ] OK [ ] Other:

### R3-17 · Member homes → replaced by P5-21
**Question (P5-21):** No personal homes (your 247). Instead the Homestead mastery gives shared island homes (`/is home <name>`), +1 per mastery rank?
- **Suggested:** Yes.
- **You picked:** "No personal homes, remove /is setmyhome" (247). It's already removed from the code.
- **Note:** `/is home <name>` is a new sub-command, so it needs your OK. **Cost:** small-medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-18 · Key fragment count
**Question:** 3 fragments make 1 Phoenix key (the crate plan), not 10 fragments for any key?
- **Suggested:** Yes. Fragments drop in mine and forest tiers 11+ (and later fishing, 1 in 4,000).
- **You picked:** 10 fragments for a crate key (811). The crate plan default says 3 for a Phoenix key.
- **Today:** the drop chance is 0, and crates aren't built.
- **Extra question:** how many hours of tier 11+ play should one Phoenix key take? (Write a number under Other, or we suggest one with the crate build.)
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-19 · Island size for gems
**Question:** Island sizes above 9x9 only for shards (Black Market), not gems?
- **Suggested:** Yes. Then the gem prices 250/500/750 (P5-15) are dropped.
- **You picked:** "bigger than 9x9 only with gems or from the black market" (157).
- **Why:** more space means more farms and more money, which is paid progress.
- **Today:** not built (staff can already set sizes up to 15x15).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-20 · Gem sets with real bonuses
**Question:** The three gem sets (Lorekeeper 400, Thornward 900, Stormkeel 900 gems): make them looks only (worn over an earned set), with the bonus versions moved to crates and crystals?
- **Suggested:** Yes.
- **You picked:** sets bought with gems, priced by strength (SE1). Real bonuses, claimed free every season on every profile (958, 959).
- **Today:** built and live. Bonuses start at 0 and grow with play. Stormkeel is a fishing set.
- **Downside:** gem buyers lose a power path. **Cost:** medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-21a · No custom drops from spawner mobs
**Question:** Should mobs from spawners drop only vanilla loot, never the new custom drop item?
- **Suggested:** Yes.
- **You picked:** every mob drops a custom item, more with looting, and it's sellable (693).
- **Fixed in v2:** emeralds are a vanilla item, not a custom drop (they're unsellable today).
- **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-21b · Your 641 isn't applied *(found while checking)*
**Question:** Apply your answer 641 now: spawners make 1 mob per cycle? Today the live server still has `stacking.yml mobs-per-spawner: 4.0`, so spawners make 4x the money the plan expects.
- **Suggested:** Yes, set it to 1.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-22 · Mining with a full satchel *(Suggested changed)*
**Question:** When the satchel is full, drops go to your inventory, then /box. Breaking stops only when /box is full (hard cap 5,000). The warning comes every 3 s, and combat drops work the same way.
- **Suggested:** Yes.
- **You picked:** "allow the break but warn... minimum delay between messages 3 sec" (748); inventory, then /box (769).
- **Today:** a full satchel stops the break, the warning comes every 1.5 s, and **combat drops are lost** when the satchel is full. That breaks "nothing is lost".
- **Fixed in v2:** /box does have a hard cap (5,000), so it can't grow forever. v1's "stop when the inventory is full" was the thing you said no to.
- **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-23 · Forge and island storage
**Question:** When the Forge takes from island storage, it counts as taking out: same permission, same daily limit?
- **Suggested:** Yes, built together with island storage.
- **You picked:** the Forge takes from inventory, satchel and island storage (891).
- **Note:** the daily limits in `planning/ISLAND_STORAGE.md` must fit Forge amounts. Tier 10 needs 2,824 items, more than a full inventory.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-24 · Combat XP only from the Depths
**Question:** Combat XP only from Depths combat zones, like Mining and Foraging?
- **Suggested:** Yes.
- **You picked:** Combat XP from any non-spawner mob, island farms included (919).
- **Today:** built as you picked. One hit with a stack-kill enchant on stacked island drowned can pay about 3,456 XP.
- **Downside:** the Soul Reaper enchant ("+Combat XP from stacked kills") needs a new effect. **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-25 · Bank interest and AFK time *(simpler)*
**Question:** Should time in the AFK pool not count toward the 2 hours a week needed for interest? Other idle time still counts, because real AFK detection is a big build.
- **Suggested:** Yes.
- **You picked:** 2 hours in 7 days, AFK counts (597).
- **Note:** interest is small (tier 1 max $600 per payout).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-26 · Total mob stacks per chunk *(facts fixed)*
**Question:** Add a total cap of 40 mob stacks per chunk (all types together), on top of 8 per type?
- **Suggested:** Yes, 40.
- **You picked:** 8 stacks per type per chunk, never despawn (S12).
- **Fixed in v2:** there are 30 spawner mob types, not 40, so the max today is 240 stacks per chunk. A normal farm with 3-5 types never reaches 40. At 24 (v1), normal farms would be cut.
- **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-27 · Hopper safety net *(softer)*
**Question:** Keep "no limits", but count hoppers per chunk and alert staff above 64. A hard cap comes only if the load test shows lag.
- **Suggested:** Yes.
- **You picked:** no block, hopper, piston or observer limits; detect lag machines only (O1, O2, WR2).
- **Today:** neither a counter nor lag-machine detection is built.
- **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-28 · Duped items *(changed)*
**Question:** When the system finds a duplicated unique item, block trading (/pay, notes, AH, /trade) for the holders for 24 h automatically, and let staff decide after?
- **Suggested:** Yes. It's a trade block, not a full freeze.
- **You picked:** "No, staff click it themselves" (458).
- **Fixed in v2:** duped money notes can't make money (the second cash-in is refused). The risk is duped unique items sold on.
- **Downside:** an innocent buyer can't trade until staff look. **Cost:** medium.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-29 · Confirm on expensive quick-buys
**Question:** Quick-buy asks to confirm only for spawners and buys over $1M?
- **Suggested:** Yes.
- **You picked:** no confirm on any quick-buy (8).
- **Fixed in v2:** today the most expensive builder item is $2,400 (64 = $153,600), and spawners aren't in /shop yet. This matters once spawners are added.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-30 · Disband with two steps
**Question:** Disband gets a second screen with the real amounts ("you lose $X, N items, bank $Y"), with the buttons in different slots so a double-tap can't confirm?
- **Suggested:** Yes.
- **You picked:** "a confirm button is enough" (261).
- **Fixed in v2:** disband resets money, inventory and ender chest (not skills or Depths yet), and staff can restore it.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-31 · /report for new players *(settled)*
**Already live:** /report works from the first minute, with a 60 s cooldown and max 3 open reports.
**Question:** Add the report reasons Scamming, Threats and Griefing (your open 542)?
- **Your answer:** [ ] Yes [ ] No [ ] Other:

### R3-32 · Private messages for new players *(changed)*
**Question:** Run /msg through the link/IP/phone filter (not the swear filter). Until a new account finishes the tutorial, only staff and people it messaged first can message it?
- **Suggested:** Yes.
- **You picked:** public chat and /msg open from the first second (TUT-5).
- **Fixed in v2:** links are already blocked in public chat for everyone. Private messages skip the filter today, so links and IPs go through there.
- **Downside:** a new player's friends can't message him for about 3 minutes. **Cost:** small.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-33 · Top rated islands *(changed)*
**Question:** "Top rated" needs ratings from at least 5 different IPs, ranked by a smoothed average so 3 five-star ratings don't beat 40 ratings at 4.9?
- **Suggested:** Yes. At 300 players, 10 IPs (v1) would leave the list nearly empty.
- **You picked:** 3 ratings (282); max 3 accounts per IP and 1 hour played to rate (292).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-34 · Full combat zones *(changed)*
**Question:** First make mob spawning grow with the number of players in a zone. Then open a second copy of the busy low tiers (1-3) when a zone reaches about 25 players.
- **Suggested:** Yes.
- **You picked:** max 40 players with a "zone full" message (824).
- **Today:** no cap, no "zone full", no copies. A zone spawns about 2 mobs a second, enough for about 16 players.
- **Cost:** large for copies, small for the spawn scaling.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-35 · Idle players in the AFK pool
**Question:** In the AFK pool, kick players who no longer earn (daily cap reached, or paused by the one-per-connection rule) after 20 minutes idle, with a 60 s warning?
- **Suggested:** Yes.
- **You picked:** idle kick after 20 minutes, but never inside the AFK pool (1021, 1033).
- **Today:** there is no idle kick anywhere yet (only the [AFK] tag after 5 minutes).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-36 · AFK pool in its own area
**Question:** The AFK pool gets its own small area (not inside the Tavern), with players inside hidden from each other?
- **Suggested:** Yes.
- **You picked:** no player cap, pool inside the Tavern (1020, 412).
- **Fixed in v2:** expect about 90 players in the pool on average and 120-150 at peak, not 300. That's still enough to lag phones and block the Tavern NPCs.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-37 · Day-3 money target *(Suggested changed)*
**Question:** Keep the smooth money curve and re-check your $5M day-3 target on the test server (your 650)?
- **Suggested:** Yes, keep it and re-check.
- **You picked:** the smooth curve (651).
- **Fixed in v2:** the model today gives the 8 h/day grinder $12.9M on day 3 (2.6x your $5M) and the regular player $1.5M. Tuning down to $5M creates a 100x jump to week 2's $500M.
- **Your answer:** [ ] Suggested [ ] No (tune to $5M) [ ] Other:

### R3-38 · Trade value hints
**Question:** The trade value hint uses server sell and shop prices. Items with no server price (spawners, books, sets, tools) show "value unknown". An AH price is used only with 5+ different sellers and buyers, ignoring same-IP pairs.
- **Suggested:** Yes.
- **You picked:** an estimated value on hover plus a red warning (736, 737). v1 said "7-day AH median", but that was the review's guess, not your answer.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-39 · Staff play accounts
**Question:** Admins, builders and the owner play on a separate account. Mods and helpers keep one. /eco can't target your own or same-IP accounts.
- **Suggested:** Yes.
- **You picked:** same account, powers off, big transfers flagged (334).
- **Today:** staff /give items are tagged (can't be sold or traded), and staff transfers of $5M+ are flagged. Many small transfers aren't.
- **Downside:** a second Minecraft account costs money.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-40 · Note creation limit *(facts fixed)*
**Question:** Replace today's 10-second wait between notes with up to 20 notes per minute, and delete cashed notes after 90 days?
- **Suggested:** Yes.
- **You picked:** no wait, no fee (665). The code still has a 10 s wait.
- **Why:** the purge is the real fix for database growth.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-41 · Bank withdrawal fee
**Question:** The only rule is the 2% fee (lower with Treasury): no free 10 minutes after a payout, and a withdrawal no longer loses that period's interest?
- **Suggested:** Yes (same as `planning/BANK_UX.md` Q1-Q2).
- **You picked:** 2% fee, 10 free minutes after each payout (588).
- **Today:** 2% fee, 10 free minutes, and a fee withdrawal also loses up to 8 h of interest.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-42 · Warzone kill reward
**Question:** The warzone killer gets half of the victim's 2% money loss (max $500k), once per pair per hour, and nothing if both are on the same IP?
- **Suggested:** Yes.
- **You picked:** the victim loses 2% (max $1M), the killer gets nothing (WR1).
- **Today:** not built (the warzone has no build yet).
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-43 · Booster thanks *(changed)*
**Question:** Keep /thanks and typed "thanks", fold them into one chat line, cap at 20 gems per booster, and give Bedrock a menu button?
- **Suggested:** Yes.
- **You picked:** /thanks or "thanks" in chat gives the booster 1 gem (R4, RW2).
- **Fixed in v2:** clickable chat isn't reliable on Bedrock, so v1's "one tap" was wrong. Uncapped, 250 players online = 250 gems per booster.
- **Your answer:** [ ] Suggested [ ] No [ ] Other:

### R3-44 · Premium pass reroll
**Question:** Drop the extra daily quest reroll from the premium pass, so it is cosmetic only?
- **Suggested:** Yes. It costs nothing (not built) and removes any doubt with Mojang's rules.
- **Fixed in v2:** a reroll only swaps one quest, and rewards per day don't change. It's a small convenience, not "more money and keys".
- **Your answer:** [ ] Suggested [ ] No (keep it) [ ] Other:

---

## Found while checking (no decision needed, just OK)
1. Your 641 isn't applied (spawners make 4 mobs per cycle, not 1). See R3-21b.
2. Combat drops are lost when the satchel is full. See R3-22.
3. Helpers can read every private message in /logs (against your 318). See R3-9b.
4. N2-1 (unlimited island joins) isn't applied. Live `islands.yml` still has `max-joins-per-season: 3` and `join-cooldown-hours: 24`.
5. Spawners on disbanded or reset islands get frozen "for staff" instead of cleared. See R3-13.
6. Dead settings to delete: the old 30-minute keys (`auction.yml`, `economy.yml`), `islands.yml disband-prompt-length` and `homes.*`, and `shop-settings.yml buy.confirm-above`.
7. Docs still describe old rules: `ECONOMY_BALANCE.md` and DECISIONS #40 (30-minute rule), and `docs/commands/islands.md` ("typed word" disband).
8. The economy model (`tools/economy_model.py`) has the 15% paid sell bonus built in. Rerun it after R3-7.
9. Bedrock IP check: make sure Geyser/Floodgate passes the real IP, or one-per-connection rules (AFK, ratings, warzone) treat all Bedrock players as one.

- **Your answer:** [ ] OK, fix all [ ] Other (which not):
