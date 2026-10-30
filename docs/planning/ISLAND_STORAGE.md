# Island storage (`/is storage`, Island Vault)

**Status:** needs owner input

STORE2 (mastery deliveries, take-out limits, staff recall) waits for this.

## What you already decided
- **Idea 6:** yes, `/is storage`, upgradeable.
- **O4:** `/is storage` = the **Island Vault** upgrade (Phase 5 M5.4: 5 levels at island level 1, 3, 5, 7, 9; $2.0k → $7.2M).
- **I7:** 1 row → 6 rows (54 slots) over 5 levels; access by role toggle; a log of who took what.
- **I8:** collectors do **not** overflow into it; blocked while frozen or in combat. (An auto-collect filter was also in I8.)
- **Round 3 ST1-ST6:** **declined**: no auto-collect filter, no search/sort, no sell, no deposit-all, no compress, no pages. "Open to other ideas."
- **Q5:** `/sort` + a sort button in island storage (round 2, before ST2 was declined).
- **I9:** leader logs include storage (7 days).
- **891:** the Forge takes materials from inventory, satchel and island storage. **R3-23** asks: only within your role's daily take-out limit.
- **PHASE-5 M5.3:** mastery deliver tasks take items from storage.
- **251 / 263:** backups on kick and disband.

## What exists in the game now
Nothing. The Island Vault upgrade is planned in Phase 5. The leader-log, island permission matrix and the crash-safe sell chest give us patterns to reuse.

## The problem / what to decide
1. **Two members open it at once:** both must see the same items, without dupes.
2. **Crash safety:** a shared chest holding a team's wealth must never lose or copy items.
3. **Take-out limit (STORE2):** how much can a Member take per day?
4. **What may go in:** unique items (tools, books), shulkers?
5. **Q5 vs ST2:** sort button yes or no?

## Our proposal
- **One shared live chest** per island. Everyone sees the same contents live. Changes save to the database within 2 s and on shutdown, with an item log per change.
- **Size:** 1 row at Vault level 0, +1 row per level up to 6 rows (I7).
- **Who:** role toggles "Storage: put in" (default Leader, Admin, Mod, Member) and "Storage: take out" (default Leader, Admin, Mod). Coop and visitors never.
- **Daily take-out limit (STORE2):** Member 64 items a day, Mod 512, Admin and Leader no limit. Putting in is never limited. The Forge and mastery deliveries count toward it (R3-23).
- **Allowed:** stackable normal items and Depths materials. **Not** unique items (tools, books, set tokens: they have the Wardrobe Chest), filled shulkers or bundles, money notes.
- **Blocked** while frozen, in combat or in staff mode (I8).
- **Kicked member recall (STORE2):** staff can return items a kicked member took in their last 7 days.
- **Disband/reset:** contents deleted with a staff backup (like the bank).
- **Sort:** one Sort button (Q5). No search, sell, compress or pages (ST declined).

## Questions for you
1. One shared live chest that all members see at once? **Suggested: Yes.**
2. 1 row → 6 rows through the Island Vault upgrade (I7, O4)? **Suggested: Yes.**
3. Role toggles: put in (default down to Member), take out (default down to Mod)? **Suggested: Yes.**
4. Daily take-out limit: Member 64 items, Mod 512, Admin and Leader none? **Suggested: Yes.**
5. Forge and mastery deliveries count toward that limit (R3-23)? **Suggested: Yes.**
6. Keep unique items, filled shulkers, bundles and notes out of storage? **Suggested: Yes.**
7. Staff can return items a kicked member took in their last 7 days? **Suggested: Yes.**
8. Contents deleted on disband or reset, with a staff backup? **Suggested: Yes.**
9. Add one Sort button (Q5), nothing else from ST1-ST6? **Suggested: Yes.**
10. Members see a log of who put in and took out (7 days, I9)? **Suggested: Yes.**
11. Any other idea for storage ("open to other ideas")? Write below.

## Your notes (write here)

