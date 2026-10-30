# Morning report: night of 29–30 October 2026

Everything below is live on the test server (latest release: **update70**, 24 modules). GitHub `main` is up to date.
Nothing has been tested in game by me: only unit tests ran (`./gradlew build`, all green). The checklists below are what you need to try.

## 1. Short version

- **33 queue items merged tonight**: island profiles, enchants (+ stacked-mob enchants), wardrobe (+ set levels, merging, `/loadout`), `/sell`, `/shop`, `/trade`, `/bank`, Depths satchel + drops, island stats, punishments P5 + P6, island menu + island chat, island notes, visiting/warps, islands round 2, per-place scoreboards, stacking, tools, punishments P2–P4, crystals, round-2 decisions, `/freerank`, message cleanup (UX1 + UX2), balance re-run, enchant + wardrobe plans.
- **One outage**: punishments were off for ~9 minutes on the test server (04:36–04:45). Fixed; see section 6.
- **Nothing sketchy happened.** No suspicious instructions anywhere (see `docs/SECURITY_LOG.md`).
- Nothing running right now; next items in section 7.

## 2. Needs your decision (most important first)

1. **Spawners are too strong.** The balance model says `stacking.yml → spawners.mobs-per-spawner: 4` makes spawners ~69% of income and doubles the money supply. Suggested: **1**. I did not change it.
2. **Approve the prices**: `/sell` in `server/plugins/PhoenixSkyblock/sell.yml` (from the balance sheet) and `/shop` in `shop.yml` (made up, the balance sheet had none; some are high on purpose so nothing costs less to buy than it sells for, e.g. carrots $120, redstone $260). You can edit both on GitHub.
3. **Approvals (P4):** only admins can approve big punishments, so a mod's first 30-day hacked-client ban becomes a request. Give mods the approve permission, or drop the `ban >= 30d` rule?
4. **`/menu` is full**: `/trade` is only in `/help` for now. Which button should it replace?
5. **Open questions**: each agent added its questions to `docs/OPEN_QUESTIONS.md` → "Waiting for owner", each with the safe default already built. Biggest ones:
   - Profiles: can you delete your only/active profile? (default: no, switch first) · Is "two profiles never on the same island, ever" OK? (default: yes, strict)
   - `/is` now opens the island menu instead of teleporting home (default: menu; `/is home` still teleports).
   - Island chat is members only (default: coop players can't read it).
   - Notes: muted players can still write notes (default: yes).
   - Wardrobe: every purchase asks to confirm (default: yes).
   - Enchants: staff `/enchanter admin give|apply|clear` was **not** built (not approved). Say yes if you want it.
   - `/sell`: no staff command to see/reset prices. Add `/sell admin`? (default: not built)
   - Wardrobe: set levels are now on, so sets you already own start at Lv 0 (no bonus until they level).
   - Loadouts: if part of a preset can't swap, the rest still swaps and the skipped parts are listed (default).
   - Banks: island bank goes to the leader on disband; bank money doesn't count in `/baltop` (defaults).

## 3. What's new (in plain words)

**Islands**
- **Island profiles** (`/profiles`): up to 3 profiles. Each has its own money, crystals, shards, inventory, ender chest, XP, skills, Depths unlocks, island, auctions and settings. Gems, rank, titles and cosmetics are shared. Slot 2 = 500 gems or Knight+, slot 3 = 1,500 gems or Warlord+. Your old data is profile 1. Nothing can move between your own profiles (pay, notes, auction, islands, dropped items, item stacks, Forge refunds, trades).
- **Island menu** (`/is`): every island feature in one menu. **Island chat** (`/is chat`), role colors picked by the leader, staff spy toggle. `/is info`.
- **Island notes** (`/is notes`): shared to-do list, book view, pinned holograms (max 5), new Read/Write/Manage notes rights.
- **Visiting**: `/is visit` browser, lock/unlock, expel, island bans, 3 warps per island (+ `[warp]` signs), ratings and likes.
- **Islands round 2**: Admin role (old leader becomes Admin on transfer), "Hurt monsters" right, `/is settings` (spawning, fire, explosions, leaf decay, trampling, visitor pickup/alerts/fly, weather/time per island), island fly, personal homes, `/is invites`. A solo island only becomes a team island when the invite is **accepted**.
- **Stacking**: mobs stack (max 1,024, 8 stacks per type per chunk), items merge with a counter, stackable spawners (8 → 512, 5 tiers), island entity limits, crops grow in the dark.

**More features (merged after the first draft)**
- **Per-place scoreboards**: own island (with a "Next" note line), visiting, Depths (Gate / Mine / Forest / Combat), spawn, warzone.
- **Stacked-mob enchants (E3)**: Slaughter (up to 48 mobs per hit), Bloodlust, Plunder, Soul Reaper, Reaper's Sweep, Phoenix Wrath; island stack books.
- **Wardrobe levels + merging (W2)**: sets level 0–50 from the activity they boost (also `/sell`), Tomes, merge two Lv 50 sets into one.
- **`/loadout` (W3)**: up to 8 presets that swap set pieces and hotbar tools in one click; 10 s combat tag.
- **`/trade` (M3.7)**: safe two-sided trade window for items + money (never crystals), both confirm, 3 s countdown.
- **`/shop` (M3.5)**: builder shop, 8 categories, 332 items.
- **`/bank` (M3.3)**: personal bank per profile (10 tiers) + island bank, interest every 3 h, island bank rights.
- **Punishments P4**: approval for big punishments, "mute ended" message, probation (+1 step for 14 days), `/watch` + `/watchlist`.
- **Message cleanup (UX2)**: moderation, Depths and island texts in the same short format.
- **Punishments P5**: `/pbulk` + Bulk button in `/p` (each player their own step, [Undo all]), punish from a `/logs` chat line, appeals (`/appeal`, staff `/appeals` with Accept/Reduce/Deny).
- **Punishments P6**: raid lockdown (`/lockdown chat|join|off|status` + menu, only new accounts), raid alert, `/psuggest` for anticheat suggestions (never applied automatically), network-ready storage.
- **Depths satchel + drops (M4.4 + M4.9)**: drops go to a per-profile satchel (Sell all, take out, upgrades), `Satchel N / M` on the scoreboard, drop tables per tier, crystals every 3 s, the Forge uses satchel materials.
- **Island stats (M2.6)**: `/is stats` (island menu → Stats): mined, chopped, crops, placed, generator, kills, sold + money earned, visitors, time on island; today / week / last week / all time with each member's share.

**Progression**
- **Enchants** (`/enchanter`): 48 enchants, unique tradeable books and Disenchant Scrolls, apply/upgrade/combine/remove/shatter, Book shop rolls, guide. Swiftness, Prosperity, Tempering and Scholar's Insight already work.
- **Wardrobe** (`/wardrobe`): 16 sets, locked real armor, buy with money/crystals/gems, Set Tokens, Wardrobe Chest, set bonuses (capped). Set levels, merging and `/loadout` come next (W2–W3).
- **Tools**: starter kit every 24 h, the Forge, living tools (Lv 1–50, perk slots, prestige), blueprints, skins.
- Skills cap 150 + staff `/skills set|add`.

**Economy**
- **Crystals replace tokens** everywhere (aqua ◆, not tradeable). Old balances migrated.
- **`/sell`**, `/sell hand`, `/sell all`: live prices (40–150% of base, slow recovery, daily Hot Item +25%), sell bonus capped at +300%, dupe-safe.
- New accounts need 30 min playtime before `/pay`, `/withdraw` and the auction house. Admins can use `/eco`.
- `/freerank`: free Ember rank + pick 1 of 3 tags after 30 min.
- Balance re-run for 1 h / 3 h / 8 h players (`docs/ECONOMY_BALANCE.md`).

**Staff and punishments**
- **P2**: [Undo] for 2 minutes, double-punish protection, offences expire after 90 days (cheating never), `/pstats`.
- **P3**: chat evidence with mutes/warnings, private staff notes (`/pnote`), warning acknowledgement (`[I understand]`, `/ack`), weighted warnings, punishing closes your claimed reports, Discord log (off until a webhook is set), `/mypunishments`.
- A ban clears a freeze. Inspect tool = blaze rod, Punish tool = breeze rod. `/s` = staff mode. All times in Oslo time.

**Look and feel (UX1)**
- All messages short and plain in one format. Cleaner scoreboard and tab (no command footer, gradient address, matching lines next to SKYBORN), rank changes show at once, `/baltop` refreshes every 30 s.

**Removed / changed**: Axiom removed. No cooldown after `/is disband`. Amount suggestions (10k/10m) removed from tab completion.

## 4. New commands

| Command | Who |
|---|---|
| `/profiles` (`/profile`) | everyone |
| `/is` (opens the island menu) | everyone |
| `/is chat [message]`, `/is info [player]` | everyone |
| `/is notes` | everyone (staff: `/is notes <island>`) |
| `/is visit [player]`, `/is lock`, `/is unlock`, `/is expel <player>`, `/is ban <player>`, `/is unban <player>` | everyone (lock/expel/ban: leader, admin, mod) |
| `/is setwarp <name> [category]`, `/is delwarp <name>`, `/is warp [player] [name]` | everyone (set/del: leader, admin, mod) |
| `/is rate <player> <1-5>`, `/is like <player>` | everyone |
| `/is settings [setting] [value]`, `/is invites`, `/is setmyhome [name]`, `/is home [name]` | everyone |
| `/enchanter` | everyone |
| `/wardrobe` (`/sets`) | everyone |
| `/sell`, `/sell hand`, `/sell all` | everyone |
| `/shop [category]` | everyone |
| `/trade [player]`, `/trade accept [player]`, `/trade deny [player]` | everyone |
| `/bank` | everyone |
| `/appeal [id] <text>` | everyone |
| `/is stats` | everyone |
| `/pbulk <reason> <players...>`, `/appeals` | staff (mod+) |
| `/lockdown [chat\|join\|off\|status]`, `/psuggest <player> <reason> [details]` | staff (helper+) |
| `/isadmin stats view\|reset <island>` (not in the plan, added by the agent: approve or remove) | staff |
| `/loadout [name]` | everyone |
| `/watch <player> [reason]`, `/watch remove <player>`, `/watchlist` | staff |
| `/forge [type]`, `/tool` | everyone |
| `/freerank` | everyone |
| `/ack`, `/mypunishments` | everyone |
| `/pnote <player> [text]`, `/pnote remove <id>` | staff (notes permission) |
| `/pstats [period]` (`/mystats`), `/pstats <staff>`, `/pstats top` | staff |
| `/p <player> <reason> [-s] [-ip] [-force]` (flags new) | staff |
| `/s` (= `/staffmode`) | staff |
| `/skills set|add <player> <skill> <levels>` | admin + owner |
| `/spawner give <player> <type> [amount]` | admin + owner |
| `/tool give`, `/blueprint give`, `/skin give` | admin + owner |
| `/isadmin setowner <island> <member>` (old leader → Admin) | admin |

Full list with permissions: `docs/COMMANDS.md`.

## 5. Test checklist (Java and Bedrock unless it says otherwise)

Tip: an alt account helps for anything with two players.

### Profiles
1. `/profiles`: Profile 1 has your old inventory, money, island and skills.
2. Buy slot 2 with gems (gems go down once), create it. The new profile is empty, $0, no island, you land at spawn. Gems stay the same. Switch back: everything returns, you land at your island home.
3. Rename (chat) and change icon. Relog right after a switch: same profile comes back.
4. Blocked between your own profiles: picking up an item you dropped on the other profile, cashing a note made on the other profile, buying your own `/ah` listing, visiting or joining your other profile's island.
5. `/baltop` shows you once. Switching while frozen or in staff mode is refused. Delete needs confirm + typing the name.
6. `/forge` starter kit works on a new profile (own 24 h timer).

### Island menu, chat, notes
7. `/is` opens the menu; every button opens and Back returns. Leader sees Disband (confirm + type the word), members see Leave.
8. `/is chat` on: only members see your messages, colored by role; coop/visitors don't. `/is chat hello` sends one message. Filter and mute still apply. `/mutechat` doesn't stop island chat.
9. Leader changes role colors in Chat colors; members see it read-only. Staff: `/settings` → "Island chat spy".
10. `/is notes`: add, tick done, edit; 201 characters/link/bad word refused. Teammates get "added a note". Pin a note: hologram appears, survives relog/restart without ghosts; done/delete removes it. Book view saves edits and can't leave the slot.

### Visiting and islands round 2
11. `/is visit` browser (4 lists), visit with warmup. `/is lock` sends visitors away and blocks entry; `/is ban` blocks all routes (also relog on the island).
12. 3 warps max; warp browser with categories; `[warp]` sign creates a warp.
13. `/is rate` only after a visit; `/is like` once a day; never your own island.
14. Promote to Mod/Admin; transfer → old leader is Admin. Hurt monsters off for visitors works.
15. `/is settings`: spawning, fire, TNT, leaf decay, trampling, visitor pickup, weather/time only for players on the island.
16. Island fly (with permission), `/is setmyhome` + `/is home <name>`, `/is invites` accept/deny/cancel.
17. Invite on a solo island: it only becomes a team island when the other player accepts.

### Stacking
18. 10 cows → `Cow x10`; a kill takes one; lava kills the whole stack with big drops. Breeding by wheat.
19. Dropped cobblestone merges into one `Cobblestone x320`; pickup takes what fits.
20. `/spawner give <you> zombie 16`, place on your island, add/take in the menu (Bedrock: crouch + tap), mobs don't move or burn, pause beyond 16 blocks, break gives spawners back.
21. Island limits: 21st armor stand is refused. Crops grow in the dark.

### Tools, enchants, wardrobe
22. `/forge` starter kit (24 h), tool levels and perk slots, Forge upgrade (moving the materials during confirm refunds), blueprints, skins, `/tool` menu (Bedrock).
23. `/enchanter`: roll a book, apply to a Lv 5+ tool, the wrong cases are refused with a reason (conflict, level, wrong tool, slot). Upgrade, combine, remove with a scroll, shatter (20% back). Books sell on `/ah` under "Enchant books". A copied book gets confiscated.
24. `/wardrobe`: buy Delver's Garb ($20k, charged once on double-click), wear pieces, "Worn now" shows the bonus. Worn pieces can't be moved out any way. Pieces stay on death; each profile has its own sets. Wardrobe Chest stores tools/books safely.

### Economy
25. `/bal`, scoreboard: crystals ◆ (aqua) and gems ✦ (emerald). `/pay` refuses crystals.
26. New account (< 30 min): `/pay`, `/withdraw`, `/ah` refused with time left.
27. `/sell`: tap items in, total updates, sell; closing never loses items. `/sell hand` refuses tools, books, notes. `/sell all` leaves armor alone. Selling a lot of coal makes the price fall. `/logs` shows one row per sale.
28. `/freerank` after 30 min: Ember rank + tag menu; second time "already claimed".

### Staff and punishments
29. `/staffmode` (or `/s`): blaze rod = Inspect, breeze rod = Punish (tap works on Bedrock). `/ban` without `-s` is announced. A ban clears a freeze.
30. `/p` → punish → [Undo] within 2 min works; after 2 min "too late". Two staff punishing within 15 s → second is blocked unless clicked again.
31. `/pstats`, `/mystats 30d`, `/pstats top`.
32. Warn a player: `[I understand]` + reminders; `/ack`; Bedrock gets a small menu. `/history` → Evidence shows chat lines with IPs hidden.
33. `/pnote Steve text`, `/pnote Steve`, `/pnote remove <id>`. Punishing closes reports you claimed. `/mypunishments` shows no staff names.
34. Dates in `/whois`, `/history`, `/logs` are Oslo time. `/skills set|add` works for admin/owner only.

### Added after the first draft
36. Scoreboard changes by place: spawn, your island (Next note line), visiting someone (`Visiting <name>`), Depths Gate/Mine/Forest/Combat. Fast teleports don't flicker.
37. Slaughter V on a 200-zombie stack kills 8 per hit, X kills 48; drops come as a few merged stacks + one XP orb.
38. `/wardrobe`: level bar on owned sets; mining with 4 pieces gives about twice the set XP of 2 pieces. Staff `/inspect` → Wardrobe → set Lv 49, mine a bit: title, glint, trail.
39. `/loadout`: save two presets, swap with `/loadout <name>` (Bedrock: tap in the menu); old tools go to the Wardrobe Chest; refused right after combat; 2 s cooldown.
40. `/trade <alt>`: offer items + money, both confirm, change something during the countdown (confirms reset), cancel by walking away (items come back). Crystals can't be offered.
41. `/shop`: buy 1/16/64/custom; full inventory refused before paying; money comes from the profile you're on.
42. `/bank`: deposit, withdraw (fee shown), buy tier 2; island menu → Bank; Member withdraw locked by default; other profile can't reach your bank.
43. P4: as mod, `/p Steve hacking` → "Sent for approval"; admin clicks [Approve]. `/tempmute Steve 1m` → "mute ended" once. `/watch Steve reason` → staff see it when Steve joins.

44. P5: `/p Steve` → Bulk → pick 2 more players → Spam → confirm → [Undo all]. `/logs chat Steve` → click a line → `/p` opens with evidence. Muted alt: `/appeal <text>`; staff `/appeals` → Accept / Reduce / Deny.
45. P6: `/lockdown` menu → Chat lockdown: a brand-new account can't chat or `/msg`, ranked/older players can. `/lockdown join 5m`: a never-seen account gets "server is busy". Console: `psuggest <name> hacking Grim: Reach` → suggestion line, nothing applied.
46. Depths: mine/chop → drops go to the satchel (`/dungeon` → Satchel), scoreboard shows Satchel N / M, Sell all pays once (double click), "+N crystals" every few seconds, full satchel = block isn't broken. Forge a copper pickaxe with 256 Coal from the satchel.
47. `/is stats`: mine generator stone, kill mobs, sell items, then reopen: numbers go up; place + break the same block gives nothing; click a category for each member's share.

### Look and feel
35. Messages: green ✔ / red ✘ with gray text, yellow [Click] buttons. Scoreboard and tab look clean on Java and Bedrock (no boxes instead of symbols). Rank change shows within ~1 s.

## 6. Problems tonight and what I did

- **Punishments down ~9 min (update53).** A new database column was named `lines`, which MariaDB doesn't allow, so the moderation module didn't start. Then `/pnote` was built in a way the command framework refuses. Fixed in update54/55. To stop it happening again: a test now fails the build on reserved column names, a broken command no longer disables its whole module, and the agent instructions list these traps.
- **Profiles: two holes found and fixed before release.** The Forge charged/refunded profile 1's money, and stacked items could lose the "dropped by" tag (so your other profile could pick them up). Both fixed in update50.
- **Config sync during merges** could overwrite new message texts with the older server copy. Caught by a test, fixed in the sync script; I checked every merge tonight and nothing was lost.
- **Tools catalog bug (found tonight, fixed in update69):** on servers where `tools.yml` already existed, blueprints and abilities were never loaded. Fixed, and tool tiers/blueprints were updated once to the Depths drop recipes (backup: `tools-catalog-v1-backup.yml` on the server).
- **Fixes I made while merging:** the island scoreboard had dropped Gems (put back, your UI decision); stacked-spawner mobs gave sword XP although `tools.yml` says spawner mobs give none (fixed); `/sell` didn't give wardrobe set XP or island stats (both hooked up).
- Small: two `tools.yml` settings warned at startup, one islands message used a symbol Bedrock can't show. Fixed.

**Security:** nothing suspicious. No Gmail, Drive, browser or computer control used; no purchases or messages; no files deleted on your Mac. The only Mac actions were copying release files to `~/Development/.skyborn-claude/` and running the release script. Log: `docs/SECURITY_LOG.md`.

## 7. Still running / next

- Running: nothing right now.
- Next in the queue: M4.7 Forest + M4.8 combat zones (then E2 book drops in the Depths), P7–P10 (rest of the punishment spec: sanctions, island punishments, rule numbers, training mode, guard/rollback, monthly report), M3.8 economy dashboard, F4 shared teleport warmup, F3 achievements.
