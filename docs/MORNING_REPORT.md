# Morning report: night of 29–30 October 2026

Everything below is live on the test server (latest release: **update57**, 22 modules). GitHub `main` is up to date.
Nothing has been tested in game by me: only unit tests ran (`./gradlew build`, all green). The checklists below are what you need to try.

## 1. Short version

- **19 queue items merged tonight**: island profiles, enchants, wardrobe, `/sell`, island menu + island chat, island notes, visiting/warps, islands round 2, stacking, tools, punishments P2 + P3, crystals, round-2 decisions, `/freerank`, UX cleanup, balance re-run, enchant + wardrobe plans.
- **One outage**: punishments were off for ~9 minutes on the test server (04:36–04:45). Fixed; see section 6.
- **Nothing sketchy happened.** No suspicious instructions anywhere (see `docs/SECURITY_LOG.md`).
- Still running when this was written: per-place scoreboards (SB), stacked-mob kill enchants (E3), `/trade` (M3.7). See section 7.

## 2. Needs your decision (most important first)

1. **Spawners are too strong.** The balance model says `stacking.yml → spawners.mobs-per-spawner: 4` makes spawners ~69% of income and doubles the money supply. Suggested: **1**. I did not change it.
2. **Approve the `/sell` prices** in `server/plugins/PhoenixSkyblock/sell.yml` (draft from the balance sheet). You can edit them on GitHub.
3. **Open questions**: each agent added its questions to `docs/OPEN_QUESTIONS.md` → "Waiting for owner", each with the safe default already built. Biggest ones:
   - Profiles: can you delete your only/active profile? (default: no, switch first) · Is "two profiles never on the same island, ever" OK? (default: yes, strict)
   - `/is` now opens the island menu instead of teleporting home (default: menu; `/is home` still teleports).
   - Island chat is members only (default: coop players can't read it).
   - Notes: muted players can still write notes (default: yes).
   - Wardrobe: every purchase asks to confirm (default: yes).
   - Enchants: staff `/enchanter admin give|apply|clear` was **not** built (not approved). Say yes if you want it.
   - `/sell`: no staff command to see/reset prices. Add `/sell admin`? (default: not built)

## 3. What's new (in plain words)

**Islands**
- **Island profiles** (`/profiles`): up to 3 profiles. Each has its own money, crystals, shards, inventory, ender chest, XP, skills, Depths unlocks, island, auctions and settings. Gems, rank, titles and cosmetics are shared. Slot 2 = 500 gems or Knight+, slot 3 = 1,500 gems or Warlord+. Your old data is profile 1. Nothing can move between your own profiles (pay, notes, auction, islands, dropped items, item stacks, Forge refunds, trades).
- **Island menu** (`/is`): every island feature in one menu. **Island chat** (`/is chat`), role colors picked by the leader, staff spy toggle. `/is info`.
- **Island notes** (`/is notes`): shared to-do list, book view, pinned holograms (max 5), new Read/Write/Manage notes rights.
- **Visiting**: `/is visit` browser, lock/unlock, expel, island bans, 3 warps per island (+ `[warp]` signs), ratings and likes.
- **Islands round 2**: Admin role (old leader becomes Admin on transfer), "Hurt monsters" right, `/is settings` (spawning, fire, explosions, leaf decay, trampling, visitor pickup/alerts/fly, weather/time per island), island fly, personal homes, `/is invites`. A solo island only becomes a team island when the invite is **accepted**.
- **Stacking**: mobs stack (max 1,024, 8 stacks per type per chunk), items merge with a counter, stackable spawners (8 → 512, 5 tiers), island entity limits, crops grow in the dark.

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

### Look and feel
35. Messages: green ✔ / red ✘ with gray text, yellow [Click] buttons. Scoreboard and tab look clean on Java and Bedrock (no boxes instead of symbols). Rank change shows within ~1 s.

## 6. Problems tonight and what I did

- **Punishments down ~9 min (update53).** A new database column was named `lines`, which MariaDB doesn't allow, so the moderation module didn't start. Then `/pnote` was built in a way the command framework refuses. Fixed in update54/55. To stop it happening again: a test now fails the build on reserved column names, a broken command no longer disables its whole module, and the agent instructions list these traps.
- **Profiles: two holes found and fixed before release.** The Forge charged/refunded profile 1's money, and stacked items could lose the "dropped by" tag (so your other profile could pick them up). Both fixed in update50.
- **Config sync during merges** could overwrite new message texts with the older server copy. Caught by a test, fixed in the sync script; I checked every merge tonight and nothing was lost.
- Small: two `tools.yml` settings warned at startup, one islands message used a symbol Bedrock can't show. Fixed.

**Security:** nothing suspicious. No Gmail, Drive, browser or computer control used; no purchases or messages; no files deleted on your Mac. The only Mac actions were copying release files to `~/Development/.skyborn-claude/` and running the release script. Log: `docs/SECURITY_LOG.md`.

## 7. Still running / next

- Running: SB (per-place scoreboards), E3 (stacked-mob kill enchants), M3.7 (`/trade`).
- Next in the queue: UX2 (message cleanup for moderation/Depths/islands), W2 (set levels, merging), W3 (`/loadout`), E2 (book drops in the Depths, needs Depths drops first), M3.3 banks, M3.5 shop, P4 (punishment approvals, probation, watchlist).
