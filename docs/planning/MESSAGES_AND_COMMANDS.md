# Messages and commands

**Status:** needs owner input

You asked for this: every message in the game is configurable, "like EssentialsX but our own format: easy to understand, direct, makes sense". This counts for **all** commands everywhere.

## What you already decided
- **UX1 (30 Oct):** "clean, short, direct messages with normal words", one color scheme, one format. UX1 and UX2 rewrote every message file in that format.
- **31:** English only, always.
- **32:** big numbers short in menus (`1.2M`), full in chat and `/bal`.
- **34:** everything compact for all players. Bedrock changes later only if needed.
- **44:** Bedrock never needs long commands. Everything is reachable from menus.
- **STYLE2 / answers 1-3:** V2 bright palette, red identity, bold yellow `» ACTION` lines, red `|` bars.
- **UI_STYLE 1.6b (your request):** `&` color codes work where people type colors (ranks, nicknames, broadcasts).
- **#48:** no amount hints like `10k 10m 10b` in tab completion.
- **500 / 519:** ban and mute announcements are public (rule number only, no staff name), unless `-s`.
- **579:** join message for money received while offline, max 5 lines then "and N more".

## What exists in the game now
- **Format guide:** `docs/UI_STYLE.md` §3. One prefix `PHOENIX »`. Green ✔ for success, red ✘ for errors, white values, yellow `[Click]` parts with hover text, gray asides.
- **Message files:** 33 files, about 11,000 lines, all MiniMessage, all reloadable with `/skyblock reload`. New keys are added on updates without touching your edits.
- **Shared command texts** (`plugin/.../messages/commands.yml`): usage, unknown word, players only, cooldown, confirm, player offline, never joined, bad amount, bad time, bad choice.
- **Commands:** about 227 built commands in 28 modules (`docs/commands/*.md`), plus 33 planned. Every command has tab completion. Aliases and cooldowns live in `commands.yml`.
- **Server texts:** `spigot.yml → unknown-command` says "Unknown command. Type /help or /menu."
- **Not ours yet:** vanilla death messages, "server full", whitelist and outdated-client screens, and texts from other plugins (LuckPerms, Geyser, ViaVersion, Grim, FastAsyncWorldEdit). Most of these only staff see.

### Inventory: message files to review

| File | Lines | Who sees it |
|---|---|---|
| plugin: moderation.yml | 2,058 | players (bans, mutes, appeals) + staff |
| islands.yml | 1,807 | players |
| economy.yml (bal, pay, notes, sell, bank) | 985 | players |
| wardrobe.yml | 588 | players |
| enchants.yml | 567 | players |
| tools.yml | 379 | players |
| navigation.yml (menu, help, settings) | 353 | players |
| logs.yml | 351 | staff |
| tutorial.yml | 346 | players |
| depths.yml | 336 | players |
| auction.yml | 317 | players |
| plugin: staff.yml | 301 | staff |
| npcs.yml | 292 | players + staff |
| plugin: essentials.yml | 247 | players + staff |
| cosmetics.yml | 225 | players |
| trade.yml | 224 | players |
| shop.yml | 193 | players |
| profiles.yml | 182 | players |
| plugin: hud.yml (scoreboard) | 170 | players |
| box.yml | 145 | players |
| worlds.yml | 143 | staff |
| skills.yml | 136 | players |
| plugin: menus.yml, social.yml | 112 each | players |
| stacking.yml | 105 | players |
| plugin: perks.yml | 99 | players |
| achievements.yml | 85 | players |
| plugin: items.yml | 84 | players |
| plugin: core.yml | 79 | staff |
| plugin: chat.yml (chat, tab) | 74 | players |
| afk.yml | 57 | players |
| plugin: commands.yml (shared) | 24 | everyone |
| plugin: players.yml (login kick) | 4 | players |

## The problem / what to decide
1. Messages are already in files, but not every text is checked. Some texts may still be written in code.
2. There is no single rule yet for **command lists** (what `/is help` prints), **usage lines**, and **request messages** (tpa, trade, invites).
3. Vanilla and other-plugin texts look different from ours.
4. Bedrock: no hover text, and chat clicks may not work. Phones show short lines only.
5. You want to read and approve the texts. 11,000 lines is too much to read as files.

## Our proposal
**A. One format with 9 message types** (added to UI_STYLE §3). Every message in the game is one of these:

| Type | Look (example) |
|---|---|
| Reply, success | `PHOENIX » ✔ Paid $5,000 to Steve. Balance: $12k` |
| Reply, error | `PHOENIX » ✘ You need $2,000 more. Sell items with /sell.` |
| Warning | `PHOENIX » ✘ Your satchel is full. New drops go to your inventory.` |
| Info | `PHOENIX » Teleporting to your island in 3s. Don't move.` |
| Usage | `PHOENIX » ✘ Usage: /pay <player> <amount>` (click fills it in) |
| Request | `PHOENIX » Steve wants to trade. [Accept] [Deny]` (+ Bedrock popup menu, QOL #15) |
| Confirm | `PHOENIX » This can't be undone. [Confirm] (or type /x confirm within 10s)` |
| List | header line + up to 8 lines + `[Next]` (see B) |
| Broadcast / staff alert | no prefix; own header (see CHAT_BROADCASTS.md) |

**B. Command list style.** A command with no arguments opens its menu (`/is`, `/bank`, `/ah`). `/<command> help` prints a short list:
```
Island commands (1/3)
 /is home  » Go to your island
 /is invite <player>  » Invite someone to your team
 ...
[Next]   Tip: /is opens the island menu.
```
Only commands you can use are shown. Each line is clickable on Java (fills the command). Bedrock players get the same list as plain text and the menu button.

**C. Error style.** Say what went wrong, then the fix, in one line. Never "Please", never "successfully". Name the command that fixes it.

**D. Every text configurable.** 
- A build test fails if a player-facing text is written in code instead of a messages file.
- Vanilla texts players see get our style: death messages, unknown command ("did you mean", QOL #9), server full, whitelist, outdated client, kick screens.
- Other plugins: we restyle only what players see (Geyser and Via kick texts). Staff-only plugin texts stay as they are.
- Command usage and descriptions also live in files, so you can rename or explain any command.

**E. Bedrock rules.** Lines under ~60 characters. Never put needed info only in hover text. Every click has a typed or menu way too. Only the safe symbols in UI_STYLE §2.1. Gradients show the first color only.

**F. Review.** We build one clickable review page per module: each message shown as it looks in game, with one-tap **OK / Change / Other**. You go module by module; we apply the changes.

**G. EssentialsX habits.** Keep the aliases players know from other servers (`/money`, `/balance`, `/tpyes`, `/tpno`, `/msg`, `/r`) so nobody gets lost.

## Questions for you
1. Use the 9 message types above for every message in the game? **Suggested: Yes.**
2. Keep one prefix `PHOENIX »` on replies only (never on broadcasts, lists or lore)? **Suggested: Yes.** When the resource pack exists, swap it for a small phoenix logo glyph.
3. A command with no arguments opens its menu, and `/<command> help` prints the list? **Suggested: Yes.**
4. Command lists show 8 lines per page with a `[Next]` button? **Suggested: Yes.**
5. Show only the commands a player can use, in tab completion and in lists? **Suggested: Yes** (already true for tab).
6. Restyle the vanilla texts players see (death messages, unknown command, server full, whitelist, outdated client, kicks)? **Suggested: Yes.**
7. Death messages: PvP deaths only to players in the warzone, other deaths only to the player who died? **Suggested: Yes** (keeps public chat clean).
8. Add a build test that fails when a player text is written in code? **Suggested: Yes.**
9. Review every message on a clickable page, module by module? **Suggested: Yes.**
10. Add an in-game editor for messages? **Suggested: No.** Edit the files and use `/skyblock reload`. Less to break.
11. Keep lines under about 60 characters for phones? **Suggested: Yes.**
12. Keep the EssentialsX-style aliases (`/money`, `/balance`, `/tpyes` ...)? **Suggested: Yes.**
13. Order of the review: player files first (economy, islands, Depths, tutorial), staff files last? **Suggested: Yes.**
14. Anything in EssentialsX you liked that we should copy (for example `/seen`, `/near`, `/list` look)? Write it below.

## Your notes (write here)

