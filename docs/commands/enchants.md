# Enchants module (`modules/enchants`): E1

Custom enchants (docs/plans/ENCHANTS.md): 48 enchants in `enchants.yml`, unique enchant books and Disenchant Scrolls, the Enchanter. Settings: `enchanter.yml` (costs, fees, rolls, tiers, family caps); enchant definitions: `enchants.yml` (written from the jar on first start, new enchants are added on updates; turn one off with `enabled: false`); texts: `messages/enchants.yml`; data: tables `enchant_books`, `enchant_seen`, `enchant_daily`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/enchanter` | The Enchanter (NPC in the Forge Quarter later): **Apply a book** (pick tool → slot → book; books that don't fit say why: conflict, needs tool Lv, wrong tool, 2 slots, prestige slot only), **Upgrade** (crystals, or a book of the same level for 25%), **Combine books** (two equal books → one level higher for 25%), **Remove** (Disenchant Scroll returns the book; Shatter refunds 20% of the crystals put in), **Book shop** (Apprentice / Adept / Master rolls per pool, Disenchant Scrolls), **Enchant guide** (every enchant by tool, Mythics secret until found), **Armor** (E5: every set in your wardrobe on this profile → its pieces and slots → apply / upgrade / combine / remove / shatter armor enchants on the set entry; 1 slot per piece, 2nd at set Lv 25, chestplate mythic slot at Lv 50; vanilla armor never). Click-only, no fail chance | `skyblock.enchants.use` (everyone) | ✅ |

Rules (defaults, all in `enchanter.yml` / `enchants.yml`):
- Enchants go in the perk slots of custom tools (slots at tool Lv 5 / 15 / 30 / 45, slot 5 after prestige). Mythics only in the prestige slot; Dragonfire Blast and Wildfire take 2 slots; one enchant per conflict group (mine area, forest area, burst, hoe burst, mythic). Level gate: `ceil(max × tool level / 50)`, a prestiged tool counts as 50.
- Crystal cost of a level: step `100 × 1.8^(s−1)` (levels spread over 10 steps) × rarity factor (0.5 / 0.75 / 1 / 1.5 / 2.5 / 4). Apply fee = level I cost or 25% of the book level's cost, whichever is higher. Each tool slot remembers the crystals put in (for the shatter refund).
- Rolls: ◆ 500 / 4,000 / 25,000, need tier 1 / 4 / 7 in that pool's wing, 5 / 5 / 3 per day (Europe/Oslo day), level I books, never Mythic. Disenchant Scroll ◆ 2,500.
- Books and scrolls: vanilla enchanted book / paper with glint, one database row each (id = the item's unique id). Every action is one database transaction (books consumed + crystals + tool perks); if the items moved meanwhile, the action is undone and the crystals come back. A copied book (row already used) is taken away when used, staff with `skyblock.staff.notify` are told. Books can't go into anvils, grindstones, smithing tables, crafting grids or enchanting tables. Tradeable and listable on the auction house (category "Enchant books").
- Profiles (P4): crystals and daily rolls belong to the active profile; each book row records the profile it was made in (`profile_id`) and the player (`created_for`); books are inventory items, so they stay in their profile, and the auction house refuses buys between own profiles. A profile switch is refused while an enchant is saving. The guide's collection is per player (account) and season.
- Passive effects now: Swiftness (break speed attribute), Tempering (tool XP), Scholar's Insight (skill XP of the tool's skill), Prosperity (fortune, expected value), Crystal Seeker / Treasure Hunter / Phoenix Edge (reported to their bonus family for the systems that use them). Drop, area and combat effects come with E2/E3, armor with E5, hoe/rod with E6.

For other modules:
- `core.services().optional(EnchantService.class)` (`common/enchant`): `level(item, id)`, `enchants(item)`, `value(id, key, level)` (numbers from `enchants.yml`), `book(item)`, `createBook(id, level, origin, owner)` / `createScroll(origin, owner)` for drops and crates.
- `core.services().get(BonusService.class)` (`common/bonus`, always present): bonus families and caps (sell cap = `economy.yml`, +300%). Sources register a `BonusSource`; users call `capped` / `multiplier` / `apply` once per action.
- `EconomyService#executeGuarded(tx, guard)`: a payment plus extra SQL in one transaction.

Audit: `item` category: `enchant_apply`, `enchant_upgrade`, `enchant_combine_tool`, `enchant_combine`, `enchant_remove`, `enchant_shatter`, `enchant_roll`, `enchant_scroll_buy`, `enchant_undo`, `book_created`, `book_confiscated` (plus the tools module's `tool_perks`); economy ledger reasons `enchant_apply`, `enchant_upgrade`, `enchant_combine`, `enchant_roll`, `enchant_scroll`, `enchant_shatter`, `enchant_undo`.

Not built (needs owner approval, EN20): staff `/enchanter admin give|apply|clear`.

Permission definitions: `modules/enchants/src/main/resources/permissions/enchants.yml`. Rank grants: `server/permissions/enchants.txt`.
