# Phoenix Pack: test checklist (run once per release)

Run once on **Java** and once on **Bedrock**. Note the pack version from the pack menu ("Phoenix Pack v…").

**Client versions to test each release:** 26.3 (current), 1.21.11, 1.21.5 (oldest supported, via ViaVersion).

## 1. Joining
- [ ] Java, new player: the pack prompt shows **before** the world loads, with our text.
- [ ] Decline: a friendly kick that explains how to turn packs on (Multiplayer → Edit server → Resource Packs: Enabled). *If the client's own message shows instead, tell the session: switch to `required: false` + our kick.*
- [ ] Rejoin and accept: the pack loads and you join.
- [ ] Rejoin again: no new download (the cache works because the hash didn't change).
- [ ] Bedrock: no prompt, you join normally.
- [ ] `/pack status <you>` says LOADED (Java) / BEDROCK (Bedrock).
- [ ] `/pack reload` as staff: online Java players get the pack again, no restart.
- [ ] Firewall: TCP 25580 open (the pack is served from the server itself).

## 2. Screens (Java with pack: backgrounds + icons; Bedrock / no pack: looks like today)
- [ ] Hub (/menu) · You · Travel · /is · /shop + buy page · /sell · amount picker · confirm · /top · one staff screen (/admin) · crate yard previews.
- [ ] Title text is parchment and sits in the top band. **Tune once:** if the background is too high/low, change `ascent` / `title-offset` in `tools/build_pack.py` (start 13 / +8) and rebuild.
- [ ] Frame panes show no tooltip when hovered.
- [ ] Page turns in a list (title reopens; check for flicker).
- [ ] GUI scale 2, 3 and 4.
- [ ] English and French ("Inventaire" sits on the parchment plate).

## 3. Rank badges
- [ ] Chat, nametag, scoreboard, hover all show the badge in the same colour.
- [ ] Rename a rank in LuckPerms (`lp group firewing setdisplayname Wingborn`) → the badge shows the new name.
- [ ] `/rank preview immortal TestName` shows pack, still, simple and Bedrock lines.
- [ ] Shine pack: set `variant: shine` in `pack.yml`, `/pack reload` → Pyrelord, Immortal, Flameheart, Firecrown shimmer. If anything looks wrong, go back to `variant: safe`.
- [ ] /settings: Reduced motion (shine stops), Simple rank badges ([Name] text), Bigger rank text (taller letters).
- [ ] Immortal: You → Rank colour shows the gradient list.
- [ ] Type a badge character in chat / on a sign / anvil: it is removed.
- [ ] Bedrock: `[Name]` in colour, no square icons anywhere (chat, tab, nametag).
- [ ] Before release: `lp group default setdisplayname Ashborn`.

## 4. Crates and keys
- [ ] /warp crates: each crate shows its custom chest, facing the path; the vanilla chest does not poke through (else turn on `displays.hide-vanilla-block`).
- [ ] Hologram: name in rarity colour + "» CLICK to open".
- [ ] Open a crate: the lid swings open on its back hinge, smooth; it closes after the reward.
- [ ] Inferno, Phoenix, Eclipse, Conqueror: soft dust when close, none from far away.
- [ ] Walk up to a crate: one quiet chime; not again for 30 s.
- [ ] Keys show their matching key icon.
- [ ] Bedrock: normal chests, clicks work.

## 5. Sounds
- [ ] Menu click, success and error sound soft and short with the pack; vanilla sounds without it.
