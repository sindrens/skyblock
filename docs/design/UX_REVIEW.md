# UX review: what makes the server easy (or hard) for thousands of players

**Status:** feedback for the owner and the developer, 31 Oct 2026. Read from `main` at update105.
- All 189 menus are recorded in `menus.json`, with 467 notes in total.
- The live configs used are the ones that were running on 30 Oct.
- Nothing was run in-game, so the in-game items here are marked "check".

---

## 1. The 15 changes that matter most (ranked by impact)
| # | Change | Why | Effort |
|---|---|---|---|
| 1 | **Back to the hub on every screen, plus Main menu at 50** | Today `HubMenu.open()` closes the menu and runs a command, so players land on screens with no way back. Many 3-row menus are dead ends. | M |
| 2 | **Clean /menu: 15 buttons, hide unbuilt features, no duplicates** | 28 buttons, 6 of them dead ("Coming soon" with colourful icons that do nothing on click). Help appears 3 times. | S |
| 3 | **"CLICK" everywhere, and plain clicks work on NPCs** | The owner found that right-click doesn't work (UX3). Lore and help say Right-click in more than 10 places, and NPC help needs a sneak-click. | M |
| 4 | **Fix the Help and Menu tutorial steps** | The Help step says "Right-click any NPC". The Menu step completes on the 15-button island menu instead of /menu. | S |
| 5 | **One welcome with one next step** | Two titles and three chat blocks on first join, each with a different next step. | S |
| 6 | **Scoreboard "Next:" line; rename the "Profile" header** | The `objective` value exists, but no layout shows it. "Profile" means two different things. | S |
| 7 | **Confirm details on the middle item** | Bedrock can't read button lore before tapping. | S |
| 8 | **Rewards on the hub: gold glow, one click claims all** | Daily exists but has no button ("Type /daily"). | S |
| 9 | **A real Top screen** (islands, week, money, skills) | Today only `/is top`, and two of its tiles do nothing. | M |
| 10 | **Travel menu** | The owner's UX3 "warps menu": every place one click from the hub. | M |
| 11 | **Glossary: one meaning per word** | "Tier" has 5 meanings, "Profile" 2. "Pity", "Bound", "Coop", "Pillars" and "Masteries" need plain words in player text. | S |
| 12 | **Bedrock popups instead of chat links** | TPA Accept/Deny, island invites and store/vote/Discord links are clickable chat, which is dead on Bedrock. | M |
| 13 | **Title rule `Feature » Screen`** | Sub-screens repeat or drop their parent's name today. | S |
| 14 | **One look for locked, empty and loading** | Today these are a grey name, grey dye or grey glass, with different loading texts. | S |
| 15 | **Refresh NPC and help texts each release** | NPCs still say "opens soon" for crates and daily, which exist now. | S |

---

## 2. Click depth for the 10 most common tasks
The star tap counts as 1 click.

| Task | Today | Target | How |
|---|---|---|---|
| Go home | 3 (or `/is home`) | 2 | Home on the hub |
| Sell | 4 | 3 | "Add my inventory" + Sell; also Sell all in `/dungeon` |
| Buy blocks | 4 (about 6 on Bedrock) | 3 | Tabs, then item, then Buy 64 |
| Check money | 0 (scoreboard) | 0 | Also on the hub head |
| Claim daily | no button | 2 | Hub Rewards claims all |
| Open the Depths | 3 | 2 | A "Continue: Iron" tile |
| Upgrade island | 6 | 4 | Skip the detail screen when there are no tasks |
| Enchant | 7 | 5 | Skip the slot screen when only one slot is free |
| Change set | 4 | 3 | Loadout swap |
| See top | no button | 2 | Hub Top |

---

## 3. First hour
**What confuses a new player today:**
- **Competing welcomes.** `essentials.yml join.first-title` and `tutorial.yml start.title` both show. The privacy line is on the first screen, which is scary.
- **Help that doesn't work.** "Right-click any NPC": clicking the Depths Guide runs `/dungeon` and shows no help (sneak-click needed).
- **The Menu step ends in the wrong place.** It completes on the island menu. It should complete on /menu and then highlight The Depths.
- **The scoreboard shows no next step,** but three currencies at once. Show only Money until the first crystal or gem.
- **Stale NPC text:** `npcs.yml roles.crates/quests/daily/vote` still say "opens soon".

**Fix order:** wording + NPC clicks (UX3) → one welcome → the Help and Menu steps → scoreboard → the hub.

---

## 4. Bedrock and touch
- **Wording:** Right-click appears in:
  - `navigation.yml menu-item` (with a dead copy in `essentials.yml`)
  - `npcs.yml npc.hint-*`
  - `tutorial.yml steps.menu/help`
  - `cosmetics.yml:79,107`
  - `tools.yml:183` (Shift+Right-click)
  - `stacking.yml:58` (sneak)
  - `help.yml:301,374,553-565`
- **NPC help needs a sneak-click.** Handle `PlayerInteractAtEntityEvent` in `NpcListener` and add an invisible Interaction hitbox, so one plain click talks.
- **Chat links are dead on Bedrock:** TPA, island invites, `[Tutorial]`, store/vote/Discord, help command suggestions. Open a small menu for Bedrock players instead.
- **Hover-only text is unreadable on touch,** because a tap sends the click: help topics written in page lore, the sale breakdown. Put the key fact in the item name.
- **Drag and shift-click:** the sell chest, trade and AH sell rely on them. Keep "Add my inventory" and a button path for every flow.
- **Check on a Bedrock client:** Mannequin NPCs and text-display holograms (`npcs.yml` hedges on this).

---

## 5. Bugs and risks found in the menus (fix soon)
- **`trade.trade`:** Info (48), Help (50) and Close cancel the whole trade for both players, and 45 is "Cancel trade" instead of Back. Same in `trade.money_picker`.
- **`rewards.daily`:** 34 items on one screen, and the day tiles overwrite the border columns (9–35, 39–41).
- **`economy.sell`:** 53 repeats the Sell button, which is fine for a non-paged screen. "Sell inventory" (46) only moves items, so rename it "Add my inventory".
- **`perks.trash`:** destroys everything on any close, with no Close or Back button.
- **`perks.freerank`:** a permanent pick in one tap. **`boosters.boosters`:** uses a booster in one tap.
- **`moderation.punish`:** skips `frame()` (no Back/?/Close), has about 34 buttons, and one click applies a ban, even a permanent one, with no confirm.
- **`stacking.spawner`:** "Take all" removes the block with no confirm, and needs right-click or sneak.
- **`depths.satchel`:** Upgrade (up to $6.4M) and Sell all in one tap.
- **`wardrobe.staff-entry`:** with max level 40 it shows "Set to Lv 40" twice (slots 14 and 16), out of order.
- **`staff.outcome`:** "Invalid" counts as a false report against the reporter, and the lore doesn't say so.
- **`islands.permissions`:** 115 cells on 6 pages, one flip each, with no presets. Add 3 presets (Strict / Normal / Open) and the matrix behind "Advanced".
- **Placeholder tiles that do nothing:** Masteries in `progression.upgrade`; Weekly gain and Pillars in `progression.top`. Hide them until they work.

---

## 6. Text
Chat is mostly short: only 2 of 1,110 prefixed lines pass 120 characters. The problems are jargon and promises:
- **Too long:** `box.yml cap.full` (123 characters), `afk.yml ip-blocked` (123), `economy.yml bank.island.withdrawn-fee`.
- **Technical:**
  - help.yml bank "Taking money out" (masteries, period) and sell "Live prices";
  - crates.yml "Pity" and "bound/tradeable".
- **Promises of "later":** `depths.yml:250` ("Its use arrives in a later update"), `islands.yml:361`, `tools.yml:166`, `rewards.yml reward.for-now`. Say what it does now, or hide it.
- **Coming-soon wording varies** ("Gray buttons come later", "Not available right now", "opens soon"). That's moot once unbuilt features are hidden.

---

## 7. Other feedback relevant to the design
- **Fewer currencies on screen.** Money, crystals, gems, shards and credits is five currencies. Show a currency only once the player has it. On the scoreboard, Money is enough until the first crystal.
- **Tab list at 300 players** shows 80 names at most. Sort by staff, then won ranks, then bought ranks, then Ashborn. That's already planned in TAB_LIST.md, so answer that file next.
- **Leave the store out of the hub.** A calm store in the You menu builds trust and fits Mojang's rules.
- **Broadcasts:** only for rare moments (legendary drops, level 50s, events). With hundreds online, more than that is noise.
- **Sounds:** one click sound, one success and one error sound everywhere (`sounds.yml`). The pack can replace them later.
- **Batch testing:** each design step ships with one Java + Bedrock checklist (the owner's S2 rule).
