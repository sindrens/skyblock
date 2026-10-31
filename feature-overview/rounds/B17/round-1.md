# B17 Profiles: round 1 (31 Oct 2026)

Read first: `disagreements/B17.md` (section C explains both of your bugs).
A critic pass on my first draft found one blocker, six unflagged reversals and several wrong code facts; this is the corrected round.
**REVERSES** = changes an earlier answer; it is written down only if you say yes. **[MUST]** = a hole or a broken promise. **[QUICK]** = small and safe to do early in the build phase.
Tests run with `./gradlew build` on H2 (no server). Credits rule from C1 (locked): credits belong to the player, are never lost on profile delete, and every credit spend goes through `CreditShop.buy()` and the confirm menu.

## Suggestions

1. **[Security/Dupe] [MUST] Death drops can only be picked up by the owner's own profile family, not by the account's other profiles.**
   - Change: in `ItemSpawnEvent` at the death location (same tick as `PlayerDeathEvent`), tag the dropped items with the dead profile's key. Reuse the existing same-account check (`foreignOwnProfile`): it blocks only your OTHER profiles from picking them up, so PvP loot, staff and other players are unaffected. No lock and no entity scan.
   - Why: today profile A dies, switches, and profile B picks up A's death drops (P4 hole). The drops are not tagged at all (`ProfileGuards:126-138` tags only thrown items and arrows).
   - Risk: if the dead profile switches away, its drops despawn as in vanilla. I also want to check the live keepInventory rule (a read-only check on the VPS, I ask first).
   - Size: S. Test: death drop and a second own profile.

2. **[Security/Dupe] [MUST] Block switching in combat, boss arenas and the warzone.**
   - Change: `switch.blocked-worlds` defaults to `[warzone]`; a handler in the profiles module (next to `SwitchRules`) refuses while `CombatTagService` reports a tag (Depths mob tags count) and inside the configured boss-arena regions (a config list until the boss card gives a service). The message says why and how many seconds are left. Allowed in the Depths out of combat (122, 132).
   - Why: decisions 122/132 say blocked; today a player can switch away from a fight or out of the warzone.
   - Risk: depends on I1 for the tag source; a stuck tag blocks a switch, so the seconds are shown.
   - Size: S/M. Test: a tag from a mob expires and unblocks.

3. **[Logic/UX] REVERSES 254 and 133: a player with a fresh island can accept an invite without "disband".**
   - Change: one boolean `touched_at` per island. It is set by the first block placed or broken by a member **outside tutorial steps**, a bank deposit, a spawner, a completed island task, an added member, or an item put into a chest. No counters, no chest scan, no 10-minute rule (the tutorial can take longer). If the island is not touched and the profile accepts an invite: validate the invite and the join rules first; then, in one flow, remove the island with a **new reason `fresh_left`** that does not wipe the profile (the normal disband reason wipes inventory and money of online members, which is exactly your lost-tools bug), clear the island's pending invites and coops, then join. One confirm: "Your island is empty. Join <friend> instead? Your tools and progress stay with you." A touched island still needs a normal disband, and the text says so and offers "switch to an empty profile".
   - Why: your friend-join problem; item 4 makes sure tools are not tied to the island.
   - Risk: depends on items 4 and 6; a failed join must not delete the island (hence validate first). No refund of creation or join counters.
   - Size: M. Tests: fresh, touched by each trigger, failed join keeps the island, inventory and money survive.

4. **[Logic/UX] [MUST] REVERSES TQ15 and answer 158 in part: every new island gets a fully filled starter chest, and starter tools are bound to the profile.**
   - Change: every new island's chest is filled completely again (plain items, Field Guide, the five tier-1 tools), no 24 h claim timer for chest fills. This fixes your "starter chest does not reset after disband and re-create" bug (the claim was never cleared and the tools were skipped inside 24 h). The tutorial chest step stays as built (open the chest and take the tools; that is the "learn by doing"). The starter tools are bound to the profile (not sellable, tradeable or listable); the reader verifies what the existing `starter` flag restricts first. Mint count of starter tools is logged.
   - Why: tier-1 wooden tools have almost no value, bound tools cannot be sold, and the chest always works.
   - Risk: an alt can mint kits by re-creating islands; bounded by island creation limits and logged.
   - Size: M. Tests: disband then create fills the chest; bound tools cannot be listed.

5. **[Logic/UX] [MUST] REVERSES the §6.1 rule "Starter pack only while the tutorial is incomplete": a joining profile gets the starter tools once.**
   - Change: a new per-profile claim row `kit_given` (atomic, `FOR UPDATE`, like the existing `claim()`). The first time a profile has no `kit_given` and joins a team island as member (or opens the Forge starter-kit button), it receives the starter tools it is missing, delivered to the inventory with overflow to `/box`. `pack_given` (Field Guide, once per account) is not touched. Simpler alternative: only point the joiner at the Forge button (it exists).
   - Why: today a finished account or a second profile that joins a team gets no tools (disagreements C.2b); one atomic claim row closes the double-mint risk of two triggers.
   - Risk: tools already owned must be counted across inventory, box, ender chest and Forge before delivery; the claim row is the guard.
   - Size: S/M.

6. **[Logic/UX] "Join a friend" at spawn, as a list of pending invites (no request feature).**
   - Change: next to the island portal, a second choice "Join a friend" lists pending invites with the friend's head, name and island level, and a Bedrock form. Empty state: "Ask your friend to type /is invite <your name>." Accepting follows item 3. There is no way to request to join (no new spam surface). Whether the tutorial is shortened for friend-join is decided in A3, not here.
   - Why: makes joining a friend a first-class way in.
   - Risk: the friend must invite first; the empty state explains how.
   - Size: M.

7. **[Security/Dupe] [MUST] Soft delete: deleted profiles are kept for staff to restore.**
   - Change: delete hides the profile (the `deleted_at` marker already exists) and keeps `profile_states` and `profiles.settings` (both are erased today). Most module delete handlers stop deleting stored rows (profile ids are never reused, the rows become unreachable) and keep only side effects: cancel AH listings, economy balance backup (C1-R1-11), island handling. Config `profiles.deleted-retention-days: 30` with an automatic purge (0 = keep); at most the last 10 deleted profiles per account are kept. `/profile restore <player> <slot|name>` (admin, tab-completed with the player's deleted profiles, audited) restores into a free slot only and says exactly what comes back (inventory, skills, balances, settings) and what does not (cancelled AH listings, the removed solo island unless `/isadmin restore` covers it). Only the owner can purge. Player text: "This removes the profile. Staff may be able to restore it for a limited time." Permission file, `docs/commands/profiles.md` and `server/permissions/profiles.txt` are updated. Owner-run "erase all data of this account" covers the soft-deleted rows too.
   - Why: principle 3; today the inventory is hard-deleted and wardrobe rows linger.
   - Risk: about 13 modules change their handlers, so this is the biggest item of the round; storage grows (retention and cap).
   - Size: L. Tests: delete then restore returns items and balances; purge removes them; restore refuses an occupied slot.

8. **[Logic/UX] Delete screen: fix the card, show what is lost, make the second screen separate.**
   - Change: the code already has two-button confirm and no typing; the card was wrong (this is a card fix, not a reversal). The screen lists real numbers: island level, playtime, money, crystals, shards, items, and the island's fate ("island removed" or "you leave the island"). The second confirm is a separate view with a short input lock so a double-click cannot confirm. Also fixes `docs/commands/profiles.md:8`.
   - Why: answers open question 4 (no typing on Bedrock); easy to use.
   - Risk: none (item 7 removes the permanence).
   - Size: S.

9. **[Logic/UX] [QUICK] Confirm before creating a profile (135).**
   - Change: creating a profile in an empty unlocked slot opens `ConfirmMenu` ("New profile: starts with $1,000 and an empty inventory, no island. You can switch back any time."). The slot-buying part waits for `CreditShop.buy()` (I6) and keeps today's charge until then.
   - Why: decision 135 is not built.
   - Risk: none.
   - Size: S.

10. **[Bug] [QUICK] Profile names: chat filter, unique per account, staff rename.**
    - Change: `ProfileNames.check` also calls the chat module's filter through a service (138/139), refuses a duplicate name on the same account, and the regex and limits move to config. New `/profile rename <player> <slot> <name>` (mod+, tab-completed, audited, permission file entry) for offensive names.
    - Why: only length and charset are checked today.
    - Risk: filter false positives; the player sees the reason.
    - Size: S.

11. **[Bug] [QUICK] Settings: chat, social and sound toggles follow the account (123).**
    - Change: first classify every `setting.*` key as account or profile (some may genuinely be per profile), then change the default `settings.profile-keys` (today it contains the prefix `setting.`, making all toggles per profile) to `["scoreboard", "menu-item", ...]`. No migration: the active profile's values already live in the account settings and the stale values are overwritten on the next switch. A round-trip test with stale data. The live `profiles.yml` on the VPS probably holds the old default and is edited only with your OK.
    - Why: decision 123; the default silently contradicts it.
    - Risk: a player who wanted different values per profile loses that.
    - Size: S.

12. **[Logic/UX] [MUST] Staff can pick and view any profile (134), read-only.**
    - Change: `/inspect`, `/invsee`, `/ecsee`, `/logs` get a picker (three slots, name, last played). Inactive profiles open read-only from `profile_states`; the active profile comes from the live player (its stored row can be stale). Editing only on the active profile. The reader first checks whether `/logs` is keyed by profile at all. Every view is audited; helper limits stay (H1, H4).
    - Why: staff cannot see or support inactive profiles today.
    - Risk: audit-heavy path; read-only first.
    - Size: L.

13. **[Security/Dupe] [MUST] A failed state consume must never roll a profile back or mix profiles.**
    - Change: at switch, write a `switch_id` into the player's persistent data in the same `saveData()` that applies the state, and store the same id in the `profile_states` row. At login: if the ids match, the state is already applied; ignore and clean the row. If they differ, apply the row. A consume failure then does nothing harmful. No kick; log and alert only on an unexpected mismatch. (Applying "the newer file" is unsafe: the player file has no version and could put profile A's items on profile B.)
    - Why: today a failed consume is logged and the stale row is re-applied at the next login, silently rolling the profile back.
    - Risk: a new column `switch_id` and a migration.
    - Size: M. Test: forced failure.

14. **[Logic/UX] Switch wait of 3 seconds, loaded from the database.**
    - Change: `switch.cooldown-seconds` 10 becomes 3; `lastSwitch` is initialised from the existing `profile_accounts.switched_at` at login so a relog does not skip it. No new column. Bypass node `skyblock.profiles.bypass.cooldown` declared in `permissions/profiles.yml` (the literal `skyblock.cooldown.bypass` moves there). Answers open question 1.
    - Why: principle 1 (no waiting); P5 asked for no real cooldown. Items 1, 2 and 13 are the real protection. If you prefer, set it to 0.
    - Risk: faster switching helps abuse only if a guard has a hole.
    - Size: S.

15. **[Economy] Slots cost credits; old rank names go; no double payment.**
    - Change: slot 2 = 500 credits, slot 3 = 1,500 credits (as built, now credits), through `CreditShop.buy()` once it exists. The "Knight+/Warlord+" text and comments are removed. Rule: a slot bought with credits is never refunded and never "covered again" by a rank slot; the menu shows the source of each slot (bought, rank, track). Which ranks or progression-track steps include a slot is decided in F2 and F1 (no proposal here).
    - Why: the card still names retired ranks; players must not pay twice by surprise.
    - Risk: a store pricing decision (Mojang: convenience, low).
    - Size: S.

16. **[Security/Dupe] REVERSES part of 121 and the P9/121 rejected idea: start money once per slot per season.**
    - Change: the start grant for slots 2 and 3 uses the key `start:<season>:<currency>:<account>:<slot>`; slot 1 keeps its old key (`start:<season>:<currency>:<accountUUID>`, the profile key of slot 1 is the player UUID). Existing slot 2 and 3 grants may be granted once more at the switch-over (accepted, tiny).
    - Why: deleting and re-creating a profile in the same slot gives $1,000 again; an alt on the main's island can deposit it into the island bank without the $10k gate (the island-member exemption), limited only by the join limit. The loop is real but small; the watch flags (C1-R1-06) already look for the pattern.
    - Risk: a player who deletes and re-creates by mistake starts with $0 in that slot (a message says why); a restore (item 7) brings its money back.
    - Size: S. Test: delete and re-create grants once.

17. **[Bug] A guard test: every table with per-profile data has a delete decision.**
    - Change: a unit test lists all tables with a profile column (`profile_key`, but also `player`, `owner_id` in tools, skills, `starter_claims`, `eco_accounts`) and fails when a table has neither a delete handler nor an explicit allowlist entry with a reason ("retain: audit", "retain: soft delete"). Fixes the wardrobe module now (loadouts, set claims), together with item 7.
    - Why: wardrobe rows stay today; the next module (pets) would repeat it.
    - Risk: none.
    - Size: S/M.

18. **[Security/Dupe] [MUST] Items that arrive during a switch go to `/box`, and pending interactions are cancelled.**
    - Change: while a profile is switching, deliveries (credit-shop items, crate rewards, kit tools, mail) go to `/box` of the **target** profile instead of the inventory being captured or applied. At switch start, pending `/trade` requests, teleport requests and open AH or trade screens are cancelled with a message. Pending island invites stay on the account (they are not profile data).
    - Why: items that arrive between capture and apply are lost or land on the wrong profile (P4 hole, and a "nothing is lost" violation).
    - Risk: each delivering module must call one `deliver(profile, item)` helper; a unit test scans for direct inventory gives in delivery paths.
    - Size: M.

19. **[Logic/UX] Delete refusal and invites: leader transfer button, pending invites cleaned.**
    - Change: when a team leader tries to delete (117), the refusal screen has a "Transfer leadership" button (also when the other members are offline: pick a member, they are told on their next login). On delete, the profile's pending invites and coop invitations are cleared (today only coops and membership are). The delete screen states the island fate (item 8).
    - Why: today the player is told to type `/is transfer` and nothing else; stale invites linger.
    - Risk: leadership by offline transfer needs the member's accept on login (B3).
    - Size: S/M.

20. **[Security/Dupe] Per-profile credit sources must not multiply earned credits.**
    - Change: credits belong to the player, but daily rewards, streak, quests and pass are per profile (128, F7, F9). Any payout in `credits_earned` from those sources is paid once per account per day (or per season for one-time rewards), taking the best profile; other rewards stay per profile. Detailed rules are set in F7 and F9; this item fixes the principle so a second or recreated profile cannot farm credits (C1-R1-37 is deferred to F7).
    - Why: with credits per player, three profiles claiming 10 credits each is a 3x farm; with delete and re-create it is unlimited.
    - Risk: players with three profiles feel a smaller daily gain; reward text must say "once per account".
    - Size: S (principle now), M in F7/F9.

## Quick fixes to do early in the build phase (no design needed)
Move `LOGIN_TIMEOUT_SECONDS`, `AUTOSAVE_TICKS`, name regex, date format, menu slots and sound ids to config or messages (keep `MAX_SLOTS = 3`); add a switch-time metric (a log line, no staff alert); fix the `/menu` slot text (it is at You, slot 10) and `docs/commands/profiles.md`; wire `seasonResetSlots` into the season service (I5) and add `profile_states` to the I5 wipe list.

## Needs your answer
- **3, 4, 5 and 6** together are the friend-join solution. Say if you want them as one package.
- **16:** do you accept $1,000 once per slot per season?
- **14:** 3 s, or 0?

---
# Owner answers (31 Oct 2026)
Reading notes (my interpretation, tell me if wrong): the message had two "4" and "9"s; read as 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ... 20 in order. "4 yes only the tools, the rest is given upon island join ..." is read as: item 4 = only the tools are bound to the profile; item 5 (joiner) also gives other relevant items, but not what the island chest already has. "18 yes stats and progress" is read as: item 18 accepted AND `/profiles` shows stats and progress per profile (the idea I dropped from my first draft).

- **1:** yes, but bigger: keep inventory EVERYWHERE and no fall damage. The only place keep inventory is off is the PvP loadout: the player types `/pvp` and opens their PvP loadout, which can be upgraded with XP and resets 100% on death. Envoy and KOTH happen there; players bring nothing. There is a PvP area where you can buy armour, upgrade it and get enchants (chance not to lose, speed, regen, protection, unbreaking III and so on). Needs planning and a good GUI.
- **2:** yes. "Does it make sense?" Yes (see below).
- **3:** yes. **4:** yes, only the tools; all tools need better lores that are easier to understand.
- **5:** yes, and the tools are upgradeable from bad to very good by adding custom enchants, enchants, upgrading and more; needs a plan, cool features and balance; works on the island and in the Depths.
- **6:** not sure, "I don't think so, we need a better way". The tutorials should be relevant either way; after the tutorial is done, players can join someone.
- **7 to 9:** yes; the GUI for profiles needs an overhaul with every other GUI: better titles, lores, easy to understand.
- **10:** yes, and islands can have an island name, chosen at a certain level, with more unlocks after progressing.
- **11 to 20:** yes ("18: stats and progress").

## Does item 2 make sense? (developer answer)
Yes. With keep inventory everywhere, the only reason to switch profiles mid-fight is to escape a PvP death. The combat tag blocks that, and the PvP loadout is a separate kit so a switch cannot carry it away. The rule stays simple: no switching while tagged or inside the PvP area or a boss arena.
