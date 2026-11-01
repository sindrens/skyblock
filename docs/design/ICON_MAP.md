# Icon map: custom icons and plain vanilla items

Owner rule (31 Oct 2026): **a custom icon only for things that are NOT a real Minecraft item.** Everything that has a good
vanilla item stays that vanilla item: no letter tile, no repaint. This file is the single list. It is generated from
`resourcepack/icons.json` (`icons` = custom, `vanilla` = plain items); the pictures are drawn by `tools/icons_lib/sprites.py`.
Preview of every custom icon and variant: `feature-overview/inspiration/icons-v2-preview.html`.

## How menus use it
- A menu button keeps its **stable key** (`feature/home`, `shop/wood`...). `core.menus().icon(viewer, key, ...)` returns the plain vanilla item for a
  vanilla key, and the base item (plus a `custom_model_data` string for viewers with the pack loaded) for a custom key.
- `menu.yml` buttons accept either a key from this file or a plain item id (`icon: emerald`), so the owner can change any tile in-game config.
- Adding a custom icon later: add a row to `icons` (and a sprite or a hand-drawn PNG), remove the key from `vanilla`, run `art`, `pack`, `check`.
- Two buttons on one screen never share a base item (I9 section 7). Repeats below are on different screens.

## Custom icons (27 keys, 26 live)
| Key | Base item (Bedrock / no pack sees this) | Picture | Variants (live style) | Used in |
|---|---|---|---|---|
| `menu` | `nether_star` | plain vanilla Nether Star (no pack file); variant "ember" = own pale star with a tiny ember | **vanilla**, ember | hotbar menu item (slot 9) |
| `currency/money` | `gold_ingot` | round green coin with a $ | one look | economy menus, prices |
| `currency/shards` | `prismarine_shard` | three pointed pink prism crystals | one look | economy menus |
| `currency/crystals` | `amethyst_shard` | cut aqua gem | one look | economy menus, Depths |
| `currency/credits` | `diamond` | chubby orange five-point star | one look | You menu, credit shop, Store tile |
| `nav/back` | `arrow` | A chunky orange arrow / B little wooden door / C double chevron button | A, **B**, C | all menus (Back) |
| `nav/prev` | `paper` | A blue triangle / B pointing glove (left) / C chevron button | A, **B**, C | all paged menus |
| `nav/next` | `paper` | A blue triangle / B pointing glove (right) / C chevron button | A, **B**, C | all paged menus |
| `nav/close` | `barrier` | A fat red X / B red cross in a round button / C red cross in a rounded square | A, **B**, C | all menus (Close) |
| `nav/danger` | `tnt` | red warning triangle with a white ! | one look | danger tile (disband, leave, delete) |
| `nav/help` | `book` | yellow round button with a ? | one look | hub Help (slot 50) |
| `nav/search` | `ender_eye` | orange magnifier with a pale blue lens | one look | auction house search (C6) |
| `ui/info` | `oak_sign` | blue round button with a white i | one look | generic info tile |
| `ui/type` | `name_tag` | pencil writing a line | one look | number picker: Type an amount |
| `state/confirm` | `lime_concrete` | A green tick / B thumbs up / C green round button with a tick | A, B, **C** | confirm screens, number picker, buy button |
| `state/cancel` | `red_concrete` | A red X / B thumbs down / C red round button with a cross | A, B, **C** | confirm screens |
| `state/on` | `lime_dye` | green switch, white knob right | one look | settings toggles |
| `state/off` | `gray_dye` | dark switch, white knob left | one look | settings toggles |
| `state/locked` | `iron_bars` | gold padlock | one look | locked entries |
| `state/undo` | `clock` | chunky curved turn-back arrow | one look | shop undo (P3) |
| `shop/buy` | `emerald` | A bag with a coin dropping in / B green price tag with + / C shopping cart | A, B, **C** | shop buy button (P3) |
| `shop/sell` | `gold_nugget` | A bag with coins popping out / B orange price tag with - / C coin stack | A, B, **C** | shop sell button (P3), hub Sell |
| `shop/sell_all` | `hopper` | A bag with three coins / B two stacked price tags / C two coin stacks | A, B, **C** | shop Sell all (P3) |
| `shop/fill` | `chest_minecart` | backpack stuffed with goods | one look | shop Fill inventory (P3) |
| `feature/bank` | `gold_block` | white bank building with a gold coin | one look | hub Bank |
| `feature/top` | `golden_helmet` | gold trophy cup | one look | hub Top (leaderboards) |
| `feature/auction` | `bell` | gold-banded gavel on its sound block | one look | hub Auction House |

Switch a variant: `python3 tools/build_icons.py use <key> <style>`, then `pack`, `check`. Style `vanilla` (only `menu`) means no custom model at all.

## Plain vanilla keys (49 keys, no custom icon, no letter tile)
| Menu key | Vanilla material | Was | Why |
|---|---|---|---|
| `feature/home` | `red_bed` | `red_bed` | bed = home |
| `feature/island` | `grass_block` | `grass_block` | grass block = island |
| `feature/depths` | `iron_pickaxe` | `iron_pickaxe` | pickaxe = mining the depths |
| `feature/travel` | `compass` (**changed**) | `ender_pearl` | compass = travel (was ender pearl; pearl now only Warps) |
| `feature/sell` | `gold_nugget` | `gold_nugget` | gold nugget (or point the hub tile at `shop/sell`, the coin icon) |
| `feature/shop` | `emerald` | `emerald` | emerald = trading |
| `feature/forge` | `smithing_table` | `smithing_table` | smithing table |
| `feature/skills` | `experience_bottle` | `experience_bottle` | XP bottle |
| `feature/enchanter` | `enchanting_table` | `enchanting_table` | enchanting table |
| `feature/wardrobe` | `leather_chestplate` | `leather_chestplate` | armour |
| `feature/rewards` | `chest` | `chest` | chest = reward box |
| `feature/staff` | `shield` | `shield` | shield |
| `feature/profiles` | `armor_stand` | `armor_stand` | armor stand = a character |
| `feature/titles` | `name_tag` | `name_tag` | name tag |
| `feature/achievements` | `totem_of_undying` | `totem_of_undying` | totem |
| `feature/settings` | `comparator` | `comparator` | comparator (gear-like) |
| `feature/store` | `diamond` | `diamond` | diamond (or point the tile at `currency/credits`, the star) |
| `feature/rules` | `lectern` | `lectern` | lectern with the rule book |
| `feature/quests` | `writable_book` | `writable_book` | book and quill |
| `feature/collection` | `bookshelf` | `bookshelf` | bookshelf |
| `travel/spawn` | `campfire` | `campfire` | campfire |
| `travel/crate_yard` | `tripwire_hook` | `tripwire_hook` | tripwire hook = key |
| `travel/market` | `emerald` | `emerald` | emerald |
| `travel/visit` | `filled_map` (**changed**) | `compass` | map = other islands (was compass) |
| `island/permissions` | `iron_door` (**changed**) | `comparator` | iron door = who may enter (was comparator, same as Settings) |
| `island/warps` | `ender_pearl` | `ender_pearl` | ender pearl = teleport |
| `island/settings` | `repeater` | `repeater` | repeater |
| `island/invites` | `paper` | `paper` | paper = letter |
| `island/notes` | `writable_book` | `writable_book` | book and quill |
| `island/chat_on` | `oak_sign` (**changed**) | `glow_item_frame` | sign = talking |
| `island/chat_off` | `gray_dye` (**changed**) | `oak_sign` | grey dye = off (pairs with the grey switch) |
| `island/chat_colors` | `orange_dye` | `orange_dye` | orange dye |
| `island/visit` | `filled_map` | `filled_map` | map |
| `island/stats` | `knowledge_book` (**changed**) | `gold_ingot` | green book = knowledge (was gold ingot) |
| `island/bank` | `ender_chest` | `ender_chest` | ender chest (the bank hub tile has its own icon) |
| `island/upgrades` | `anvil` | `anvil` | anvil |
| `island/visitors_open` | `oak_fence_gate` | `oak_fence_gate` | open gate |
| `island/visitors_locked` | `iron_bars` | `iron_bars` | iron bars = closed |
| `state/loading` | `clock` | `clock` | clock |
| `state/empty` | `cobweb` | `cobweb` | cobweb = nothing here |
| `shop/essentials` | `crafting_table` | `crafting_table` | crafting table |
| `shop/farming` | `wheat_seeds` | `wheat_seeds` | seeds |
| `shop/wood` | `oak_log` | `oak_log` | oak log |
| `shop/stone_earth` | `stone_bricks` | `stone_bricks` | stone bricks |
| `shop/special` | `quartz_block` | `quartz_block` | quartz block |
| `shop/colors` | `light_blue_stained_glass` | `light_blue_stained_glass` | light blue glass |
| `shop/decoration` | `lantern` | `lantern` | lantern |
| `shop/redstone` | `redstone` | `redstone` | redstone |
| `shop/spawners` | `spawner` | `spawner` | spawner |

## Notes
- **Home no longer shows a purple cube.** The old pack replaced `items/red_bed.json` with a copy of the 1.21.5 definition, which points at
  `minecraft:item/red_bed`. That model was removed in 26.3 (the bed became a composite of block models), so the client drew the missing-model cube.
  Fixed twice: `feature/home` is now a plain vanilla bed (no override file at all), and `python3 tools/build_icons.py verify-jars --jar <client.jar>...`
  now fails the build step if any base item's definition differs between client versions or points at a model that does not exist (run it with the
  1.21.5, 1.21.11 and 26.3 jars before every pack release). Custom icons only sit on bases that are identical in all three.
- Bases for custom icons were chosen so the item a Bedrock player sees still explains the button (arrow = Back, paper = pages, barrier = Close, TNT = danger,
  concrete = confirm/cancel, emerald = Buy, gold nugget = Sell, hopper = Sell all, clock = Undo, bell = Auction, gold block = Bank).
- Hub Sell and Store tiles can optionally use the custom `shop/sell` and `currency/credits` icons by changing `icon:` in `menu.yml`; the defaults stay vanilla.
