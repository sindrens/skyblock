# Boosters (`modules/boosters`)

Boosters (PHASE-10-REWARDS.md **M10.2**, owner R3, R4, RW2, O11, RX39). Settings: `boosters.yml`; texts: `messages/boosters.yml`. Reload: `/skyblock reload boosters` (item looks: restart).

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/boosters` (alias `/booster`) | Opens the boosters menu: your personal booster of each type (running, time left, banked time), the global booster of each type (who started it, time left, thanks so far, the queue) and the booster items in your inventory (click to start). Updates every second. Also in `/help boosters` | `skyblock.boosters.use` (everyone) | ✅ |
| `/thanks` (alias `/thank`) | Thanks every running (or just ended, `thanks.grace-seconds`) global booster you have not thanked yet: its starter gets `thanks.credits` (1) earned credit, once per booster per player (RW2). Saying a thanks phrase in chat (`thanks`, `ty`, `thx` ...) does the same, quietly | `skyblock.boosters.thanks` (everyone) | ✅ |

No arguments, so there is nothing to tab-complete. No staff commands yet (a `/boosters admin give` would need the owner's OK; see OPEN_QUESTIONS).

**Personal boosters** (bound items, RX39)
- Types: **Sell**, **Skill XP**, **Tool XP**, **Crystals**; strength and length are on the item (crates: +25/50/100%, 10-60 min). Items are unique (dupe scanner) and always **bound** to the profile that earned them (`common/item/BoundItems`): no trade, no AH, no pickup by another profile.
- **Right-click** (main hand) or click it in `/boosters` to start. The same type never stacks: the same strength **adds time** (R3), up to `personal.max-minutes` (3 h, RX39); a longer total is refused and the item kept. Another strength of a running type is refused until that one ends (the item is kept).
- Time counts down **only while you are online on that profile** (never wasted offline, paused during a profile switch).
- Every booster is a `BonusService` source (`boosters`): it adds to its family (sell, skill_xp, tool_xp, crystals) together with enchants, sets, ranks ... and the **family cap applies once** (sell: the +300% cap, O11).

**Banked time (AFK pool, moved here)**
- The AFK pool's sell booster (2% per reward after 1 h, +50% for 10 min, max 60 min banked, `afk.yml → booster.*`) is banked in the boosters module. It **starts by itself** when you are outside the AFK pool and no other sell strength runs (as before), and adds to a running booster of the same strength.
- On the first start, sell boosters left in the old `afk_boosters` table (banked time plus what was left of a running one) are moved into the bank once (`bank_add`, source `afk_pool_migrated`).

**Global boosters** (Phoenix/Eclipse crates, later the store)
- Everyone online gets the bonus. One per type runs; more wait in a **queue** (max `global.max-queue`, 5, per type; more are refused and the item kept). Time left counts down while the server runs and survives restarts.
- A **boss bar** shows type, strength, time left, the starter and `/thanks` to everyone (`/settings → Global booster bar` hides it). Start, queue and end are announced (`global.announce`).
- Global booster items are bound by default (`global.always-bound`).
- **Thanks (RW2):** 1 earned credit per player per booster, never for your own booster, no credit from the starter's IP address (`thanks.same-ip-pays: false`), at most `thanks.max-credits-per-booster` (100) credits per booster. Paid through the economy ledger (reason `booster_thanks`, idempotency key `booster_thanks:<id>:<player>`); a crash before the payment is retried at the next start, never paid twice.

**Safety**
- Starting an item: the item leaves the inventory, then **one transaction** subtracts the unsaved used time, marks the item's unique id used (`booster_used_items`) and adds the time. A refusal or error gives the item back (inventory, else `/box`). Because the id is stored, a copy or a crash-rolled-back inventory can never start the same item twice (it is removed with a message and logged as `reuse_refused`). After the start the player file is saved (`items.save-player-data`).
- Used time is saved every `personal.save-seconds` (30 s) as a **relative** subtraction, so it can never undo time added meanwhile; every database step of a profile runs in order. A crash loses at most that much counting, in the player's favour.

**Audit** (category `boosters`, `/logs`): `personal_start` (type, minutes, item, outcome started/extended, time left), `personal_end`, `bank_add` (source, added, banked), `bank_start`, `global_start`, `global_queue`, `global_end` (thanks count), `thanks` (credit yes/no, same IP), `reuse_refused`, `profile_reset`. The economy writes `booster_thanks` ledger rows.

**For other modules:** `BoosterService` (common): `createPersonal` / `createGlobal` (crates use them for booster rewards), `bank(connection, …)` + `banked(…)` (the AFK pool), `hold(owner, predicate)` (banked time waits while it is true: the AFK pool while inside), `valid(…)`.

**Tables:** `booster_active (profile_key, booster_type, bonus_percent, remaining_ms)`, `booster_bank (profile_key, booster_type, bonus_percent, banked_ms, bank_source)`, `booster_used_items (item_uuid)`, `booster_global (global_id, booster_type, bonus_percent, duration_ms, remaining_ms, starter_*, global_state)`, `booster_thanks (global_id, thanker_uuid, gem_state)` (`gem_state` = whether the thanks credit is paid; the column kept its old name).
