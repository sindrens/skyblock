# Plan: Look v2, feature colours (owner answers 7 Nov 2026, LOOK_THEMES.html)

Answers: Theme B (feature colours) yes; A and C no ("I liked B and a little A, but not the orange top of the
scoreboard"); small caps labels yes ("and we still need a full pack that supports it"); no gradients in text; scoreboard
in blocks; tab list grouped by rank; chat prefix + framed big moments; menu lore template; **do it now** (the release
right after the credit shop). Inspiration: docs/planning/INSPIRATION_7NOV.md.

## The palette (flat colours, each one of Bedrock's 16 so Java and Bedrock look the same)
| Role | Colour |
|---|---|
| Scoreboard / chat brand word | **PHOENIX** red (`#FF5555`) + **SKYBLOCK** white (no orange top) |
| Labels | grey `#AAAAAA`, small caps (ᴍᴏɴᴇʏ) |
| Values | white |
| Money | green `#55FF55` |
| Credits | aqua `#55FFFF` |
| Crystals | light purple `#FF55FF` |
| Shards | gold `#FFAA00` (farming), aqua (mining), blue (fishing), red (combat) |
| You | gold `#FFAA00` |
| Island | green |
| Farm | yellow `#FFFF55` |
| Mine | aqua |
| Fish | blue `#5555FF` |
| PvE / danger | red |
| Events | light purple |
| Store | aqua |
| Click lines | the feature colour, `» Click to …` |
| Errors / success | red / green |
| IP line | aqua |

## What changes
1. **Style tokens** (`style.yml` / UI_STYLE.md): the colour roles above; `grad:*` tags in player-facing text become flat.
2. **Scoreboard** (hud.yml, every layout: island, spawn, farm, mine, fish, pve, warzone): title `PHOENIX SKYBLOCK`,
   2-3 blocks (You, the area, Event) with a coloured bar `▍` on each row in the block colour, small-caps grey labels,
   white values, one empty line between blocks, IP at the bottom; fewer lines.
3. **Tab list** (chat module TabService): header `PHOENIX SKYBLOCK` + `Season 1`; players grouped by rank with a count
   line per group, rank tag in the rank colour; footer `Playing with N players · Ping 32ms · /store`.
4. **Chat:** prefix `PHOENIX »` (red + grey), white text, only the key word coloured; events, rare drops and purchases =
   a title + a short framed chat block (rule line, label, one line, one action).
5. **Menu lore** (every menu): bold title in the feature colour, a dim small-caps category line, one sentence, values,
   price, `» Click to …` in the feature colour.
6. Later with the resource pack: a pixel font with small caps, the logo as scoreboard title and tab header, rank
   plates.

## Built (update169)
- `style.yml`: the feature colours (`<c:you>`, `<c:island>`, `<c:farm>`, `<c:mine>`, `<c:fish>`, `<c:pve>`, `<c:event>`,
  `<c:spawn>`, `<c:store>`, `<c:server>`, `<c:label>` …), money green, credits aqua, crystals light purple, shards gold /
  aqua / blue / red, every gradient flat, the PHOENIX notice and every module prefix flat in its feature colour, neutral
  grey lore bars.
- `<sc>…</sc>`: small caps on Java, capitals on Bedrock (Bedrock fonts may not have small capitals; the build blocks
  unsafe symbols in message files).
- Scoreboard: every layout titled PHOENIX (red) SKYBLOCK (white), blocks You / the area / Event with a coloured `│` on
  each row, small-caps grey labels, white values; at /farm, /mine, /fish and /pve the visit's earnings sit under You and
  the event and your next goal under Event.
- Tab: PHOENIX SKYBLOCK, the season and players online; footer `/help · /store` and the IP.
- Not yet: rank groups with counts in the tab list (needs fake list entries; comes with the pack's rank plates), every
  menu's title in its feature colour (menus keep their meaning colours from I9 for now).
