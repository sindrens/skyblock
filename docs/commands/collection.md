# Collection (`modules/collection`)

The collection log (PHASE-10-REWARDS.md **M10.10**, owner 130, P10-32, P10-33). **Per profile**: each profile has its own log (owner principle 2); nothing moves between your own profiles. Settings: `collection.yml`; texts: `messages/collection.yml`. Reload: `/skyblock reload collection`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/collection` | Opens your collection log. The pages are **tabs** in the top row (Farm, Ores, Fish, Mobs, Crates; each tab name shows found/total). Found entries show their item with a glint, "✔ Name", how many you found, when the first one was and from where. Locked entries are the gray locked look named **"??? · how to get it"** (e.g. *Ore 3 at /mine*), so Bedrock players see the hint in the name; a tap repeats it in the action bar. Slot 46: the whole log (found/total, what the rewards are), slot 47: this page's milestones. Also in `/menu` → your head (**You** menu) → *Collection*, and `/help collection`. No arguments, so there is nothing to tab-complete | `skyblock.collection.use` (everyone) | ✅ |

No staff commands (none are in the approved list; staff see everything in `/logs`, category **collection**: `unlock`, `milestone`, `grant_delivered`, `grant_failed`, `grant_review`, `profile_reset`).

**What counts (P10-32)**
- An entry unlocks the **first time your profile gets the item itself**: crops harvested at `/farm`, ores mined at `/mine`, catches at `/fish`, mobs killed at `/pve` and ring bosses beaten (`/pve boss`), and the **star rewards of crates** you open (Ember, Cinder, Blaze, Inferno, Phoenix; a reward that paid crystals instead does not count). Trades, auction buys, `/pay` and staff gives never count, so a log can't be bought.
- Batch 9 (7 Nov 2026): every later find adds to the entry's **amount** ("Found: 3,412", saved in the same batch as finds). Other sources report through `CollectionLog#found` (common). Pages without a live source are not shown (principle 0).

**Pages (default `collection.yml → pages.*`)**
- **Farm**: the 5 crops (wheat … nether wart). **Ores** (page id `mine`, so old milestones stay): the 7 ore drops. **Fish**: the 9 catches. **Mobs**: the 10 ring mobs, each followed by its ring boss (20 entries). Lines are vanilla item ids (mobs: mob ids); unknown ids are skipped with a console warning. The old Depths pages (forest, combat) are gone (v2).
- **Crates**: the live star rewards of the crates in `pages.crates` (read from the crates module, so a reward removed from `crates.yml` leaves the page; *coming soon* rewards are never on it). Eclipse (monthly rewards) and Conqueror (KOTH) are left out so the page can be finished.

**Milestones and rewards (P10-33, paid once)**
- Every **25 / 50 / 75 / 100 %** of a page (`milestones.percents`) is a collection milestone: **5 crystals** (`rewards.milestone-crystals`, paid once with the grant id as the idempotency key) and **+1 island level point** for the island of every member who has it (the *Collections* source of `/is level`, capped at 5% of the total in `island-level.yml`).
- A **full page**: its title (*Master Farmer*, *Deep Miner*, *Old Salt*, *Monster Hunter*, *Lucky Hand*; account-wide) + **1 bound Blaze key**.
- The **whole log** (every page 100%): title **Keeper of Ashes** (mythic) + **1 bound Phoenix key** (+ the secret set token once a secret set exists, `rewards.log-set`).
- The milestone row and its reward rows are written in **one transaction**; keys and set tokens are handed out after the commit (`pending → sending → delivered`); a crash in between marks them `review` for staff (never paid twice). Titles are idempotent and simply granted again.

**Notices**: a new find shows a short screen subtitle and one chat line (*New in your collection: Coal (Mine 1/12)*) with a soft sound; a milestone shows a title, a chat line with the rewards and the unlock sound. Nothing is announced to other players.

**Saving**: finds are cached per online profile and saved in batches every 10 s (`save.seconds`); milestones are saved at once together with the waiting finds. A profile's milestones are checked again every time it loads, so nothing is missed after a crash or a catalog change. Deleting a profile deletes its log.

**Placeholders**: `collection_found`, `collection_total`, `collection_percent` (the active profile).
