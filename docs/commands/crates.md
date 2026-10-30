# Crates (`modules/crates`)

Crates and keys (PHASE-10-REWARDS.md **M10.1**, owner decisions CA1-CA7). Keys are earned only and live per **profile** and crate as two counters: **bound** (votes, AFK pool, daily calendar, playtime, rank claims, tutorial: never withdrawn or traded) and **tradeable** (quests, battle pass, events, weekly top, fragments: withdraw as items in M10.4). Opening uses bound keys first. Crates and reward tables: `crates.yml`; settings: `crates-settings.yml`; texts: `messages/crates.yml`. Reload: `/skyblock reload crates`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/crates [crate]` | Opens the crates menu: your keys and every crate with keys, pity and top-band odds. With a crate (`ember`, `cinder`, `blaze`, `inferno`, `phoenix`, `eclipse`; tab-completed) it opens that crate's reward preview straight away. Also in `/menu` (tripwire hook) and `/help crates` | `skyblock.crates.use` (everyone) | ✅ |

**Menus** (6 rows, Bedrock-safe). `/crates`: keys summary in the top middle, the 7 crates in a row (Conqueror shows *Coming soon* until KOTH exists). Java: **left-click = preview**, **right-click = open 1**, **shift-click = open 10** (`open.bulk-amount`). Bedrock: a **tap opens the preview**, which has buttons for everything. **Preview**: every reward as its item with the V2 line `★ Name (xN) | chance%` (★ = top band; the chance is the reward's real share of the table), money and crystal pouches show what they pay **at your tier**, coming-soon rewards say what they pay now; keys and `Pity 7/23` in the top middle; buttons **Open 1**, **Open 10**, **Open all** (up to `open.max-per-click`, 64). **Open 10 / Open all** skip any animation and show a **summary screen** (equal rewards merged, top rewards first) plus a short chat summary.

**Crate yard.** Right-click a crate block on a crate yard pedestal (build points `crates`, mapping `physical.points`) to open 1; left-click (or sneak + right-click) to see its rewards. The blocks, holograms, spinner and win broadcasts come in M10.3.

**Rules**
- **Rolls are decided first.** One database transaction per click takes the keys (bound first), moves the pity counter, writes one `crate_opens` row per open, adds won keys and pays money / crystals / shards through the ledger (reason `crate_reward`, idempotency key `crate_open:<batch>:<currency>`). Everything commits or nothing does; if the keys or pity changed meanwhile, nothing is taken and it is tried again (`open.retries`).
- **Items** (enchant books, Disenchant Scrolls, Set Tokens, spawners, elytra) are created by their own services with origin `crate:<id>` (dupe detection) after the commit, go to the inventory and the rest to `/box` (offline / other profile: straight into that profile's box), then the player file is saved (SECFIX1 PlayerSaves). **Cosmetics** (tags, titles) unlock account-wide; **Island Fuel** goes to your island.
- **Bound loot (REVIEW3 R3-2):** items from a bound key carry a hidden bound tag (`common/item/BoundItems`): they can't be traded, listed on the AH or picked up by another profile. Keys won with a bound key are bound too.
- **Pouches scale with your tier:** Money Pouch = N minutes of `REF(t)` (`pouch.ref-per-minute`), Crystal Pouch = base x 1.3^(t-1) (`pouch.crystal-growth`). `t` = your best stage tier (highest of mining/foraging/combat level, the AFK pool's rule).
- **Pity:** per profile and crate, counts opens without a top-band reward; at the crate's `pity` number the open is forced into the top band. Resets when `pity.season-id` is raised (season reset).
- **Duplicates:** a tag/title you own re-rolls into one you don't (same reward, then another cosmetic of the same band); own them all = its crystal value.
- **Coming soon:** rewards whose system is not built yet (boosters M10.2, tomes, skins, sell wands, collectors, blueprints, pet eggs, global boosters) keep their chance and pay their crystal value (`fallback`) until then, so the odds never change.
- Not while frozen, in staff mode or during a profile switch; one open at a time per player. A box at its hard safety cap refuses opens.

**Audit** (category `crates`, `/logs`): `key_give` (every key grant, reason = source, bound yes/no), `crate_open` (one per click: crate, keys used, pity after, money/crystals/shards, rewards), `crate_grant_failed` (an item/cosmetic/fuel could not be handed out: staff give it by hand), `crate_review` (startup: an item reward was rolled while the server stopped mid hand-out; check inventory and `/box`), `crate_profile_reset` (deleted profile). The economy writes `crate_reward` ledger rows.

**For other modules:** `CrateKeys` (common) `giveKeys(profileKey, crate, amount, reason, actor)`: `reason` decides bound vs tradeable (`keys.bound-sources` / `keys.tradeable-sources`; unknown = bound). The AFK pool's pending keys are delivered through it at the player's next join/check after this module runs.

**Staff tools:** none yet. `/crates admin` (give, take, reset, test roll) needs the owner's OK (PHASE-10 RX41). Until then test keys come from the AFK pool (1 h = 1 Ember key) or, on the test server only, from a direct database insert into `crate_keys` (profile key, crate id, `bound_amount`) while the player is offline.

## Safety (for developers)
- `crate_opens.grant_state`: `done` (paid in the transaction), `pending` → `delivered` / `failed`. On startup pending **cosmetics** are granted again (idempotent); pending **items/fuel** become `review` and are never handed out twice (lose at worst, never duplicate).
- Key counters only change through conditional `UPDATE`s (`bound_amount >= ?`); pity through compare-and-set on the old value.
