# Owner answers: pack round 2 (31 Oct 2026)

The owner's message after seeing the GUI art v1, in plain points. The art on branch `pack/gui-art-v1` already follows it (see `docs/plans/RESOURCE_PACK_GUI.md` §1).

The round-2 questions were on the page "Phoenix Pack Round 2" (B1–B4 rank badges, C1–C2 credits, K1–K2 crates and keys, I1 icons). The answers are below.

## Round 3 answers (31 Oct)
| # | Answer |
|---|---|
| Ranks | "The phoenix #3 looked THE best" (read as concept *3. Phoenix Flame* in `branding/concepts`). **Rank names must be changeable in LuckPerms without breaking it.** **The colours must not be so similar; we need the right rank colours.** → Badges are now a font, and the name comes from LuckPerms (RESOURCE_PACK_GUI §6.2). |
| B1, B2 | Replaced by the rank feedback above. |
| B3 | **Yes**: moving shine on Java, still on Bedrock. |
| B4 | **Yes**: Pyrelord, Immortal, Flameheart, Firecrown. |
| C1 | **Other**: "I liked the other credits symbol". Credits are the ember ticket again. |
| C2 | **Yes**: credits are spent in /store only; /shop stays money. |
| K1 | **Other**: the crate chest itself is custom; his pictures were inspiration only. |
| K2 | **Other**: the keys need to match the crates. |
| I1 | **Yes**: the simpler icons. |

## Decided
1. **Staff badge colours:**
   - Owner: yellow, slightly orange.
   - Admin: light red.
   - Mod: light blue.
   - Helper: light green.
   - Builder: orange.
2. **All rank badges need a better design.** The better the rank, the more unique it looks.
3. **The best ranks get an "enchanted" effect.** Owner: "and maybe more if you have ideas".
4. **Currency icons:**
   - Money looks good.
   - Crystals look fine, but must look more like a crystal.
   - Shards must look like a shard.
   - The gem must look like a gem.
5. **Gems are renamed credits.** Nothing is called gems any more.

   | Currency | What it is for |
   |---|---|
   | **Money** | Everyday buying and selling |
   | **Crystals** | Island upgrades; shown in the island menu |
   | **Shards** | Upgrading tools, armour and such |
   | **Credits** (was gems) | Bought with real money on Buycraft (Tebex), spent in **/store only** (C2). /shop stays money. |

6. **Icons: simpler and easier to read.**
   - "It looks good, but I prefer it simpler and easier to see; more explainable."
   - Thousands of players will join, so it must not look "too much done". It has to be readable and understandable.
7. **In-game items:** a custom crate chest at /warp crates for each crate, depending on its tier rarity, **"and that's it"**.
   - The item art list in the art brief (tools, books, sets, notes, spawners, Depths drops) is dropped.
   - Crate keys: they must match their crates (K2).
8. **Don't clash with the other session.** Pack work stays on its own branch and touches only `resourcepack/` and docs.

## Task for the main session: rename gems → credits
This is not done on the pack branch. It is code and config work for the main session, when it fits the queue.
- **Where "gems" appears on `main` (d458dbe):**
  - economy 16 files, wardrobe 13, rewards 13, `server/plugins/PhoenixSkyblock` 10, common 10, boosters 8, profiles 7, trade 4, plugin 3, islands 2, hud 2, npcs 1, moderation 1, menus 1.
  - This counts configs, messages, code and tests.
- **Stored data:**
  - Balances use the currency id `gems` (for example the `balance.cur` column the wardrobe tests use).
  - Rename the id to `credits` with a one-time migration (`UPDATE … SET cur = 'credits' WHERE cur = 'gems'`), or keep `gems` as the internal id and change only the player-facing name. **Recommended: migrate.** It keeps one word everywhere, and "gems" won't leak into new code.
- **Player-facing text:**
  - Every message and lore line says "credits".
  - `/gems` (if it exists) becomes `/credits`, with `/gems` kept as a hidden alias for a few weeks.
  - The help pages and the glossary need the same change.
- **Colour and icon:**
  - `style.yml credits: #FF8C1A` stays the text colour.
  - The menu icon is `phoenix:currency/credits` (ember ticket), and the glyph is U+E204.
  - The gems colour (`#2EFFC0`) and glyph U+E202 are retired.
- **Tebex/Buycraft packages** that give gems must give credits. Check the package commands before the rename goes live.
- **Docs to update:**
  - FEATURES.md (done on the pack branch);
  - ECONOMY_BALANCE;
  - RANKS / store plans;
  - REVIEW3 answers that mention gems: R3-5a "gems tradeable via /trade and /pay" now means credits.
