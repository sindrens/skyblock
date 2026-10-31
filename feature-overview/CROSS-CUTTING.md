# Cross-cutting tasks found during the review

Not tied to one card. Each is a work package for after the review (or inside the card named). Status: logged.

## X1. Messages: review and fix every message (owner, 31 Oct)
Facts (counted 31 Oct from `modules/*/src/main/resources/messages/*.yml` and `plugin/.../messages/*.yml`):
- 43 message files, about 6,560 message lines. Biggest: moderation 1,020, islands 703, economy 393, wardrobe 322, enchants 308, tutorial 271.
- About 2,060 lines have no colour tag at all (many are short button labels or menu words that code may colour; this must be checked per file, not assumed). 404 lines use bold. 2 lines still use legacy `&` codes.
Plan:
1. Write the message style rules once in `docs/UI_STYLE.md` (colours per meaning, where bold is used, one prefix format, max length, no sentences with more than one idea, English only, no passive voice, every error says the fix).
2. A script (`tools/messages_audit.py`) lists per file: lines without a colour tag, over-long lines, legacy codes, missing placeholders, duplicates, Norwegian words. It runs in `./gradlew build` as a warning, later as a failure.
3. Go module by module in the order players meet them: spawn, tutorial, islands, economy, shop, depths, then the rest. The owner approves a sample page per module before the rest is changed.
4. Messages are changed together with the card review of the same feature, so nothing is changed twice.
Open questions for the owner: which colours for success, error, info and money in chat (UI_STYLE already has a palette; the owner says it is not appealing enough), bold only for headings or also for key numbers.

## X2. Holograms and /warp displays (owner, 31 Oct)
- Today: island note holograms use `<bold><c:value>text</c:value></bold>` and muted author line (`islands.yml:1852`); crate holograms, NPC text and warp signs have their own styles.
- Proposal: one hologram template set in `messages/holograms.yml` (title line bold in flame, value line in text colour, hint line in muted with an arrow), used by crates, NPCs, warps, notes and leaderboards; /warp category colours (one colour per category, same colour on sign, hologram and menu). Max 3 lines, readable from 10 blocks.
- Needs the owner's choice of category colours and one example screenshot approved. Belongs to A5, B6 and I7.

## X3. Menus
- Part of the GUI and symbol redesign (new card I9 / I7): simple layouts, readable symbols. The resource pack art is drawn by the owner; vanilla fallback stays for Bedrock.

## X4. Blocks and items that old clients cannot see (owner asked: "from 1.21.5, find out and keep me updated")
Source: a research pass on 31 Oct (minecraft.wiki version pages and the ViaBackwards/Mappings repositories); not tested with a real 1.21.5 client.
- Server 26.3, clients 1.21.5 to 26.3. Anything added after 1.21.5 is mapped by ViaBackwards to a similar older block or item. The client does not crash, but it sees something else.
- New blocks since 1.21.5 and what a 1.21.5 client sees:
  | Version | New blocks | Old client sees |
  |---|---|---|
  | 1.21.6 | dried ghast | chorus plant |
  | 1.21.9 | copper bars, chain, lantern, torch, chest, golem statue; shelves (all woods); oxidised lightning rods | iron bars, chain, soul lantern, soul torch, normal chest, planks, cut copper |
  | 26.1 | golden dandelion | dandelion |
  | 26.2 | sulfur, cinnabar, potent sulfur and their stairs, slabs, walls, bricks; sulfur spike | sandstone, granite, end stone, pointed dripstone |
  | 26.3 | poplar wood set, red shrub, shelf mushroom, wool and concrete stairs and slabs (16 colours), cushions, straw bed | birch, crimson roots, cocoa, unrelated stairs and slabs (wrong colours), probably carpet |
- New ITEMS (copper tools and armour, harnesses, spears, nautilus armour, new spawn eggs, discs, maps) become a similar old item with the original name stored; ViaBackwards adds a translated name unless the item has a custom name. A resource pack can restore the look of such items through custom model data; it cannot do it for placed blocks.
- Rule (proposed, logged as C1-R2-24): in `/shop`, `/sell`, crate loot, menu icons and island blocks, use only blocks and items that exist in 1.21.5. Newer ones may appear only with a custom name and lore, so old clients never see a wrong name. Wool/concrete stairs and slabs, copper blocks, shelves and the sulfur set are the ones most likely to be asked for.
- Found in our code: crates use `copper_chest` (added in 1.21.9) as the pedestal block and icon (`crates.yml:66`, `CratesSettings.java:126`); a 1.21.5 client sees a normal chest there. The owner already wants a custom crate chest through the pack (PACK1 item 7), so check that it renders the same on 1.21.5 clients when that is built. `InteractRules` already treats `_SHELF` and `COPPER_CHEST` as containers (correct for the server).
- Not yet verified: whether the installed ViaBackwards jar on the VPS includes the 26.2 and 26.3 mappings (check the version on the VPS, needs an ssh read); 1.21.5 real-client behaviour; mapping of cushions.
- Pack format: `pack.mcmeta` range for 1.21.5 to current is looked up when the pack work starts (RESOURCE_PACK.md).

## X5. Pillars, island level and /is top (owner, C1-R2-21)
"Too easy, not how it is supposed to be, has to be even better." Handled in the B13 and B16 reviews; the owner will be asked how hard levels should be, what should count, how often rankings update, how big the gap between solo and team is allowed to be.
