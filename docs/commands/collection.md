# Collection (`modules/collection`)

The collection log (PHASE-10-REWARDS.md **M10.10**, owner 130, P10-32, P10-33). **Per profile**: each profile has its own log (owner principle 2); nothing moves between your own profiles. Settings: `collection.yml`; texts: `messages/collection.yml`. Reload: `/skyblock reload collection`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/collection` | Opens your collection log. The pages are **tabs** in the top row (Mine, Forest, Combat, Crates; each tab name shows found/total). Found entries show their item with a glint, "✔ Name", when and from what you found it. Locked entries are the gray locked look named **"??? · how to get it"** (e.g. *Mine tier 3 in the Depths*), so Bedrock players see the hint in the name; a tap repeats it in the action bar. Slot 46: the whole log (found/total, what the rewards are), slot 47: this page's milestones. Also in `/menu` → your head (**You** menu) → *Collection*, and `/help collection`. No arguments, so there is nothing to tab-complete | `skyblock.collection.use` (everyone) | ✅ |

No staff commands (none are in the approved list; staff see everything in `/logs`, category **collection**: `unlock`, `milestone`, `grant_delivered`, `grant_failed`, `grant_review`, `profile_reset`).

**What counts (P10-32)**
- An entry unlocks the **first time your profile gets the item itself**: Depths block/log drops (main drop and rare finds) and Depths mob drops (read after every other bonus, only amounts above 0), and the **star rewards of crates** you open (Ember, Cinder, Blaze, Inferno, Phoenix; a reward that paid crystals instead does not count). Trades, auction buys, `/pay` and staff gives never count, so a log can't be bought.
- Later sources (fishing, bosses, events, quests, Black Market) report through `CollectionLog#found` (common) and get their own page when they exist. Pages without a live source are not shown (principle 0).

**Pages (default `collection.yml → pages.*`)**
- **Mine**: the 12 Depths mine drops (tier 1 coal … tier 12 voidstone). **Forest**: the 11 Depths logs. **Combat**: the 10 Depths mob drops. Lines are `<tier>: <drop item id>`; unknown item ids are skipped with a console warning.
- **Crates**: the live star rewards of the crates in `pages.crates` (read from the crates module, so a reward removed from `crates.yml` leaves the page; *coming soon* rewards are never on it). Eclipse (monthly rewards) and Conqueror (KOTH) are left out so the page can be finished.

**Milestones and rewards (P10-33, paid once)**
- Every **25 / 50 / 75 / 100 %** of a page (`milestones.percents`) is a collection milestone: **+1 island level point** for the island of every member who has it (the *Collections* source of `/is level`, capped at 5% of the total in `island-level.yml`).
- A **full page**: its title (*Deep Miner*, *Woodwarden*, *Monster Hunter*, *Lucky Hand*; account-wide) + **1 bound Blaze key**.
- The **whole log** (every page 100%): title **Keeper of Ashes** (mythic) + **1 bound Phoenix key** (+ the secret set token once a secret set exists, `rewards.log-set`).
- The milestone row and its reward rows are written in **one transaction**; keys and set tokens are handed out after the commit (`pending → sending → delivered`); a crash in between marks them `review` for staff (never paid twice). Titles are idempotent and simply granted again.

**Notices**: a new find shows a short screen subtitle and one chat line (*New in your collection: Coal (Mine 1/12)*) with a soft sound; a milestone shows a title, a chat line with the rewards and the unlock sound. Nothing is announced to other players.

**Saving**: finds are cached per online profile and saved in batches every 10 s (`save.seconds`); milestones are saved at once together with the waiting finds. A profile's milestones are checked again every time it loads, so nothing is missed after a crash or a catalog change. Deleting a profile deletes its log.

**Placeholders**: `collection_found`, `collection_total`, `collection_percent` (the active profile).
