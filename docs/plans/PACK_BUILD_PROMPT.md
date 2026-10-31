# Prompt for the main session: finish the Phoenix resource pack

The art is done and documented. This prompt covers building the real pack and wiring it into the plugin.

## Read first
- `docs/plans/RESOURCE_PACK_GUI.md`: the art, the ids, every menu's background, and how to wire it (§8).
- `docs/plans/RESOURCE_PACK.md`: the plumbing (sending, fonts, `ids.yml`, the build script, Geyser), §12–13.
- `docs/OWNER_ANSWERS_PACK1.md`: the owner's decisions (badges, credits, crates, keys).
- `docs/design/GUI_DESIGN.md`: the menu rules.
- **Branch `pack/gui-art-v1`** holds the art. Merge it first: it is art and docs only, and it merges cleanly into main.

## Rules
- **Plan first.** Write `docs/plans/PACK_BUILD.md` before any code:
  - the queue items (RP1–RP6 / GUI-6);
  - the order, the files each one touches, and the tests;
  - open questions.

  If anything is unclear, ask the owner before building.
- **Keep the look VERY close to the approved art.** No new styles, colours or effects beyond this prompt.
  - Change art only in the generators (`resourcepack/src/gui-art`, `badge-font`, `crate-art`), then regenerate.
  - Never edit the PNGs by hand.
- **Never run tests or test servers without the owner's permission.** Code, build and release only. The owner runs the in-game checklist.
- **One branch per queue item.** Don't clash with other work in progress.
- **Original art only.** Vanilla items keep vanilla textures.

## Must-haves
1. **The pack loads before joining, with a clear prompt.**
   - Send the pack in the configuration phase (check the exact Paper 26.3 API, don't guess) with `required: true`. Put the prompt text in `pack.yml`.
   - **Declined:** a friendly kick saying why the pack is needed and how to turn it on (Multiplayer → Edit server → Resource Packs: Enabled).
   - **Download failed:** "Couldn't download the pack, please rejoin" (`kick-on-fail`).
   - Bedrock is not affected.
2. **The pack hash is in the URL.**
   - File name: `phoenix-java-<version>-<sha1 first 8>.zip`, with the SHA-1 sent in the request.
   - The zip must come out byte-for-byte the same every build (sorted entries, fixed timestamps), so an unchanged pack keeps the same hash and clients use their cache.
3. **`/pack reload` for staff** (`skyblock.pack.reload`).
   - It reloads `pack.yml` and resends the new pack to online Java players, without a restart.
   - Add `/pack status <player>` to show loaded / declined / failed / Bedrock.
4. **A safe fallback for everything; never a missing texture.**
   - Icons use `custom_model_data` strings on vanilla items only. Never put a pack model in `item_model`.
   - Glyphs, badge fonts and menu backgrounds go only to viewers whose pack is loaded (render per viewer from `PackService` status). Everyone else gets plain text or Unicode.
   - Bedrock gets Geyser item mappings plus the text fallback.
5. **Strip private-use characters (U+E000–U+F8FF) from all player input.** That covers:
   - chat, signs, books and anvil renames;
   - nicknames, island names, notes, warp names and AH listings;
   - anything a player types that others see.

   This way no one can fake rank badges or icons.
6. **Pin the supported client versions.**
   - Give `pack.mcmeta` the format range for 1.21.5 → current (look the numbers up), matching the ViaVersion range.
   - Put the shine shader in pack overlays per version range.
   - Keep a short list of the client versions to test each release.
7. **One test checklist per release:** `docs/PACK_TEST_CHECKLIST.md`, run once on Java and once on Bedrock.
   - **Screens:** hub, You, Travel, /is, shop + buy page, /sell, amount picker, confirm, Top, one staff screen, crate yard.
   - **Settings:** GUI scale 2, 3 and 4; English and one long language (check the "Inventory" plate).
   - **Badges:** a renamed rank and the shine.
   - **Crates and keys.**
   - **Tune the title position once** (start values: ascent 13, offset +8).

## Improvements
1. **The version in the pack menu:** `pack.mcmeta` description "Phoenix Pack v<version>", and `pack.png` made from the logo in `branding/`.
2. **`/rank preview <group> [name]` for staff.** It shows the badge in chat exactly as players will see it, with an optional test name (to try a rename), plus the Bedrock line.
3. **Same colour everywhere for a rank:** chat name, tab, nametag, scoreboard and hover all come from one place (`style.yml` → `badges:`, from `resourcepack/src/badge-font/ranks.json`).
   - **The highest rank (Immortal) can choose its colour from a short list** in /settings or You → Rank colour.
   - The list: the default phoenix gradient plus a few approved gradients, set in config.
   - Every choice stays in the dark Phoenix Flame badge style, never a staff colour or a solid plate.
   - Default: the phoenix gradient.
4. **Crate yard polish** (items 5–7 below; keep each one light).
5. **Particles:** subtle, only for Inferno and higher.
   - Spawn only while a player is within about 16 blocks, about every 10–20 ticks, with a low count.
   - Use the crate's rarity colour (dust).
6. **Hologram:** the crate name in its rarity colour, plus a second line "» CLICK to open". A plain click opens the crate; how players see the rewards stays as it is today.
7. **Approach sound:** a short, quiet chime when a player comes within about 6 blocks of a crate. It plays once per approach (with a cooldown) and only for that player.
8. **Custom sounds for menu click, success and error** (RP6).
   - Events `phoenix:ui.click`, `phoenix:ui.success` and `phoenix:ui.error`, as `pack-sound` keys in `sounds.yml`.
   - The vanilla sounds stay as the fallback for no-pack players.
   - Add Bedrock sound definitions.
   - Keep them close to the vanilla UI feel: short and soft.
9. **Lid-open animation for crates.**
   - Split each crate model into `crate/block/<id>_body` and `<id>_lid` (`crates.js` already has the lid as its own parts; add the two exports).
   - Show them as two item displays and swing the lid open on its back hinge with display interpolation (smooth, no frames). Close it after the roll.
   - Bedrock keeps the normal chest.

## Quality of life (/settings)
- **Reduced motion: turn the shine off.** This viewer gets `phoenix:badge` instead of `phoenix:badge_shine`. Default: shine on.
- **Simple rank badges.** This viewer sees ranks as plain coloured `[Name]` text instead of the badge font. Default: off.
- **Bigger rank text.**
  - Add a badge font variant `phoenix:badge_large` with taller 5×7 letters, still inside the 9 px line. Write original letters in `badges.py`; don't copy Minecraft's font.
  - Same plates, caps and colours. Default: off.

## Remember
- **Size budget:** Java ≤ 8 MB, Bedrock ≤ 5 MB (about 1 MB today).
- **PNGs:** RGBA, no metadata.
- **Badges:**
  - The name is the LuckPerms group display name; the look comes from the group id.
  - Put a U+F801 space between letters and the cap characters at both ends, and turn the shadow off.
  - A group with no style set gets the plain Ashborn look.
- **Menus:**
  - Title = `[−8 space][background glyph][−177 space][+8 space]` + title text in parchment `#E8DCC0`, for pack viewers only.
  - Frame panes use `menu/blank` with the tooltip hidden.
- **Ready and selected states live on the item** (the `rewards_ready` model, glint, count), never on the background.
- **Crates:** keep the real chest block (Bedrock and clicks). Pack players get an item display at scale about 1.05 facing the path; the model's front is south (+z).
- **Unit tests:**
  - Every icon id the code uses exists in `pack-ids.yml`.
  - Every menu class has a background.
- **The gems → credits rename is a separate task** (`OWNER_ANSWERS_PACK1.md`). Credits are spent in /store only.

## Done when
- Java players get the pack before joining. Declined and failed both show clear messages. `/pack reload` and `/pack status` work.
- Every menu, icon, badge and crate looks like the previews in `resourcepack/src/preview/`. No-pack and Bedrock players get the text fallback.
- The checklist is written and handed to the owner to run.
